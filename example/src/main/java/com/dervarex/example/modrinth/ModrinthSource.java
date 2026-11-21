package com.dervarex.example.modrinth;

import com.dervarex.unweird.install.Downloads;
import com.dervarex.unweird.install.Item;
import com.dervarex.unweird.install.Kind;
import com.dervarex.unweird.install.Release;
import com.dervarex.unweird.install.Results;
import com.dervarex.unweird.install.Source;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Modrinth as an unweird {@link Source}: mods, resource packs, shaders, data packs and modpacks for one target.
 * This is all it takes; downloading, checksums, dependencies, installed state and the UI come from unweird-install.
 */
public final class ModrinthSource implements Source {

    private static final int PAGE = 20;

    private final Modrinth modrinth;
    private final Target target;
    private final Map<Kind, ContentType> types = new LinkedHashMap<>();

    public ModrinthSource(Modrinth modrinth, Target target) {
        this.modrinth = modrinth;
        this.target = target;
        for (ContentType type : ContentType.values()) {
            types.put(new Kind(type.projectType(), type.label()), type);
        }
    }

    public Target target() {
        return target;
    }

    @Override
    public List<Kind> kinds() {
        return List.copyOf(types.keySet());
    }

    @Override
    public String userAgent() {
        return modrinth.userAgent();
    }

    @Override
    public Results search(String query, Kind kind, int offset) {
        Modrinth.SearchPage page = modrinth.search(query, types.get(kind), target, offset, PAGE);
        List<Item> items = new ArrayList<>();
        for (Project hit : page.hits()) {
            items.add(new Item(hit.projectId(), kind, hit.title(), hit.description(), hit.author(), hit.iconUrl(),
                    hit.downloadsShort() + " downloads"));
        }
        return new Results(items, page.totalHits());
    }

    @Override
    public Release release(Item item) {
        ContentType type = types.get(item.kind());
        List<Version> versions = modrinth.versions(item.id(), type, target);
        if (versions.isEmpty()) {
            throw new ModrinthException("There's no version of " + item.title() + " for " + target.describe());
        }
        Version version = versions.getFirst();
        Version.File file = version.primaryFile();
        if (type == ContentType.MODPACK) {
            return modpack(version, file);
        }

        Release.Builder release = Release.version(version.versionNumber())
                .download(file.url(), type.folder() + "/" + file.filename(), file.sha1(), file.size());
        if (type == ContentType.MOD) {
            Kind mods = kindOf(ContentType.MOD);
            Set<String> required = new LinkedHashSet<>();
            for (Version.Dependency dependency : version.dependencies()) {
                if (dependency.required()) {
                    required.add(dependency.projectId() != null ? dependency.projectId() : modrinth.version(dependency.versionId()).projectId());
                }
            }
            for (Modrinth.ProjectInfo info : modrinth.projects(required)) { // real names for the installed list
                release.requires(new Item(info.id(), mods, info.title(), info.description(), null, info.iconUrl(), null));
            }
        }
        return release.build();
    }

    /** A .mrpack: its index lists files to download; overrides are unpacked on top. */
    private Release modpack(Version version, Version.File file) {
        Path pack;
        try {
            pack = Downloads.temporary(file.url(), file.sha1(), userAgent());
        } catch (IOException e) {
            throw new UncheckedIOException("Couldn't download the modpack: " + e.getMessage(), e);
        }
        try (ZipFile zip = new ZipFile(pack.toFile())) {
            ZipEntry indexEntry = zip.getEntry("modrinth.index.json");
            if (indexEntry == null) {
                throw new ModrinthException("That modpack has no modrinth.index.json");
            }
            Index index;
            try (Reader reader = new InputStreamReader(zip.getInputStream(indexEntry), StandardCharsets.UTF_8)) {
                index = new Gson().fromJson(reader, Index.class);
            }
            if (index == null || index.formatVersion() != 1 || !"minecraft".equals(index.game())) {
                throw new ModrinthException("Unsupported modpack format");
            }
            Release.Builder release = Release.version(version.versionNumber());
            for (Index.File entry : index.files()) {
                if (entry.env() != null && "unsupported".equals(entry.env().get("client"))) {
                    continue; // server-only
                }
                if (entry.downloads() == null || entry.downloads().isEmpty()) {
                    throw new ModrinthException("The modpack lists " + entry.path() + " without a download");
                }
                release.download(entry.downloads().getFirst(), entry.path(), entry.hashes().get("sha1"), entry.fileSize());
            }
            release.unpack(pack, "overrides/", "").unpack(pack, "client-overrides/", "");
            if (index.dependencies() != null) {
                index.dependencies().forEach(release::info); // e.g. minecraft -> 1.21.1, fabric-loader -> 0.16.10
            }
            return release.temporary(pack).build();
        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(pack);
            } catch (IOException ignored) {
                // best effort
            }
            if (e instanceof ModrinthException known) {
                throw known;
            }
            throw new ModrinthException("Couldn't read the modpack: " + e.getMessage(), e);
        }
    }

    private Kind kindOf(ContentType type) {
        return types.entrySet().stream().filter(e -> e.getValue() == type).findFirst().orElseThrow().getKey();
    }

    /** The parts of modrinth.index.json we need. */
    private record Index(int formatVersion, String game, String versionId, String name, List<File> files,
                         Map<String, String> dependencies) {

        record File(String path, Map<String, String> hashes, Map<String, String> env, List<String> downloads, long fileSize) {
        }
    }
}
