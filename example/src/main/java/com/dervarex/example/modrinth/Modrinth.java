package com.dervarex.example.modrinth;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A small client for the Modrinth API (v2): search, versions, lookups by hash, downloads.
 * Thread-safe; every call blocks, so call it from a background thread (the installer and browser do).
 *
 * <p>Modrinth asks clients to send a User-Agent that identifies the app, ideally with a way to contact you:
 * {@code Modrinth.connect("my-launcher/1.2 (me@example.com)")}.
 */
public final class Modrinth {

    String userAgent() {
        return userAgent;
    }

    public static final String API = "https://api.modrinth.com/v2/";
    private static final String DEFAULT_AGENT = "unweird-modrinth/0.1.0";

    static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final URI base;
    private final String userAgent;
    private final HttpClient http;

    Modrinth(URI base, String userAgent) {
        this.base = base.toString().endsWith("/") ? base : URI.create(base + "/");
        this.userAgent = Objects.requireNonNull(userAgent, "userAgent");
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Connects to modrinth.com with a generic User-Agent. Prefer {@link #connect(String)} in real apps. */
    public static Modrinth connect() {
        return new Modrinth(URI.create(API), DEFAULT_AGENT);
    }

    /** Connects to modrinth.com, identifying as {@code userAgent} (e.g. {@code "my-launcher/1.2 (contact)"}). */
    public static Modrinth connect(String userAgent) {
        return new Modrinth(URI.create(API), userAgent);
    }

    /** One page of search results. */
    public record SearchPage(List<Project> hits, int offset, int limit, int totalHits) {
    }

    /**
     * Searches projects of one type that have a version for the target's Minecraft version (and loader, for mods).
     * An empty query lists the most downloaded ones.
     */
    public SearchPage search(String query, ContentType type, Target target, int offset, int limit) {
        List<List<String>> facets = new ArrayList<>();
        facets.add(List.of("project_type:" + type.projectType()));
        if (target != null && target.gameVersion() != null) {
            facets.add(List.of("versions:" + target.gameVersion()));
        }
        if (target != null && type == ContentType.MOD && target.loader() != null) {
            facets.add(List.of("categories:" + target.loader()));
        }
        boolean blank = query == null || query.isBlank();
        String path = "search?query=" + encode(blank ? "" : query.strip())
                + "&index=" + (blank ? "downloads" : "relevance")
                + "&offset=" + Math.max(0, offset)
                + "&limit=" + Math.max(1, Math.min(100, limit))
                + "&facets=" + encode(GSON.toJson(facets));
        return get(path, SearchPage.class);
    }

    /** Versions of a project that fit the target, newest first. */
    public List<Version> versions(String projectId, ContentType type, Target target) {
        StringBuilder path = new StringBuilder("project/").append(encode(projectId)).append("/version");
        String separator = "?";
        List<String> loaders = target == null ? List.of() : type.versionLoaders(target);
        if (!loaders.isEmpty()) {
            path.append(separator).append("loaders=").append(encode(GSON.toJson(loaders)));
            separator = "&";
        }
        if (target != null && target.gameVersion() != null) {
            path.append(separator).append("game_versions=").append(encode(GSON.toJson(List.of(target.gameVersion()))));
        }
        return get(path.toString(), new TypeToken<List<Version>>() { }.getType());
    }

    public Version version(String versionId) {
        return get("version/" + encode(versionId), Version.class);
    }

    /** Looks files up by SHA-1. Unknown hashes are missing from the result. */
    public Map<String, Version> versionsBySha1(Collection<String> sha1s) {
        if (sha1s.isEmpty()) {
            return Map.of();
        }
        String body = GSON.toJson(Map.of("hashes", List.copyOf(sha1s), "algorithm", "sha1"));
        HttpRequest request = request("version_files")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return parse(send(request), new TypeToken<Map<String, Version>>() { }.getType());
    }

    /** Basic info for several projects at once (for naming files found on disk). */
    public List<ProjectInfo> projects(Collection<String> projectIds) {
        if (projectIds.isEmpty()) {
            return List.of();
        }
        return get("projects?ids=" + encode(GSON.toJson(List.copyOf(projectIds))), new TypeToken<List<ProjectInfo>>() { }.getType());
    }

    /** What {@link #projects} returns. */
    public record ProjectInfo(String id, String slug, String title, String description, String iconUrl, String projectType) {
    }

    // ---- plumbing ----

    private <T> T get(String path, java.lang.reflect.Type type) {
        return parse(send(request(path).GET().build()), type);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(base.resolve(path))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30));
    }

    private String send(HttpRequest request) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 404) {
                throw new ModrinthException("Not found on Modrinth: " + request.uri().getPath());
            }
            if (status == 429) {
                throw new ModrinthException("Modrinth is rate limiting requests; try again in a minute");
            }
            if (status / 100 != 2) {
                throw new ModrinthException("Modrinth answered HTTP " + status + " for " + request.uri().getPath());
            }
            return response.body();
        } catch (IOException e) {
            throw new ModrinthException("Can't reach Modrinth (" + e.getMessage() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ModrinthException("Interrupted while talking to Modrinth", e);
        }
    }

    private static <T> T parse(String json, java.lang.reflect.Type type) {
        try {
            T value = GSON.fromJson(json, type);
            if (value == null) {
                throw new ModrinthException("Modrinth sent an empty answer");
            }
            return value;
        } catch (JsonParseException e) {
            throw new ModrinthException("Modrinth sent something unexpected: " + e.getMessage(), e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
