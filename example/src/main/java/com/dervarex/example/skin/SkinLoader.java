package com.dervarex.example.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;

/**
 * Gets a skin from what the user typed: a Minecraft username (via Mojang's API, which also says whether the
 * skin uses slim arms), an http(s) URL, or a PNG file. Old 64×32 skins are converted to 64×64.
 * Blocking; call it from a background thread ({@code async(...)}).
 */
public final class SkinLoader {

    /** @param slim true/false if known (from the player's profile), null if unknown */
    public record Skin(BufferedImage image, Boolean slim, String description) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private SkinLoader() {
    }

    public static Skin load(String input) throws IOException {
        String text = input.strip();
        if (text.isEmpty()) {
            throw new IOException("Type a username, a skin URL or a PNG file path.");
        }
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return new Skin(normalize(decode(get(text))), null, "Loaded the skin from the URL.");
        }
        if (text.contains("/") || text.contains("\\") || text.toLowerCase(Locale.ROOT).endsWith(".png")) {
            return file(Path.of(text.startsWith("~/") ? System.getProperty("user.home") + text.substring(1) : text));
        }
        if (!text.matches("[A-Za-z0-9_]{1,16}")) {
            throw new IOException("\"" + text + "\" isn't a Minecraft username, URL or file path.");
        }
        return player(text);
    }

    public static Skin file(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("There's no file at " + file + ".");
        }
        return new Skin(normalize(decode(Files.readAllBytes(file))), null, "Loaded " + file.getFileName() + ".");
    }

    /** Username → UUID → profile → textures, the way the game does it. */
    private static Skin player(String name) throws IOException {
        HttpResponse<byte[]> lookup = send("https://api.mojang.com/users/profiles/minecraft/" + name);
        if (lookup.statusCode() == 404 || lookup.statusCode() == 204 || lookup.body().length == 0) {
            throw new IOException("There's no Minecraft player called " + name + ".");
        }
        check(lookup);
        JsonObject account = JsonParser.parseString(new String(lookup.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        String id = account.get("id").getAsString();
        String realName = account.get("name").getAsString();

        HttpResponse<byte[]> profileResponse = send("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
        check(profileResponse);
        JsonObject profile = JsonParser.parseString(new String(profileResponse.body(), StandardCharsets.UTF_8)).getAsJsonObject();
        for (var element : profile.getAsJsonArray("properties")) {
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(property.get("name").getAsString())) {
                continue;
            }
            String json = new String(Base64.getDecoder().decode(property.get("value").getAsString()), StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
            if (textures == null || !textures.has("SKIN")) {
                throw new IOException(realName + " uses one of the default skins.");
            }
            JsonObject skin = textures.getAsJsonObject("SKIN");
            boolean slim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
            BufferedImage image = normalize(decode(get(skin.get("url").getAsString())));
            return new Skin(image, slim, "Loaded " + realName + "'s skin" + (slim ? " (slim arms)." : "."));
        }
        throw new IOException("Mojang didn't send a skin for " + realName + ".");
    }

    private static byte[] get(String url) throws IOException {
        HttpResponse<byte[]> response = send(url);
        check(response);
        return response.body();
    }

    private static HttpResponse<byte[]> send(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", "unweird-example").build();
        try {
            return HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    private static void check(HttpResponse<byte[]> response) throws IOException {
        if (response.statusCode() == 429) {
            throw new IOException("Mojang is rate limiting; try again in a minute.");
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("The server answered HTTP " + response.statusCode() + ".");
        }
    }

    private static BufferedImage decode(byte[] data) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) {
            throw new IOException("That isn't an image.");
        }
        return image;
    }

    /**
     * Skins are 64×64 (or a multiple, for HD skins). Old 64×32 skins have no separate left arm and leg: like the game,
     * copy the right ones over, mirrored.
     */
    static BufferedImage normalize(BufferedImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width % 64 != 0 || (height != width && height * 2 != width)) {
            throw new IOException("Skins are 64×64 or 64×32 pixels; this one is " + width + "×" + height + ".");
        }
        BufferedImage out = new BufferedImage(width, width, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        if (height * 2 == width) {
            int s = width / 64; // scale for HD skins
            // left leg from the right leg, left arm from the right arm: each face mirrored, sides swapped
            copy(out, s, 4, 16, 16, 32, 4, 4);
            copy(out, s, 8, 16, 16, 32, 4, 4);
            copy(out, s, 0, 20, 24, 32, 4, 12);
            copy(out, s, 4, 20, 16, 32, 4, 12);
            copy(out, s, 8, 20, 8, 32, 4, 12);
            copy(out, s, 12, 20, 16, 32, 4, 12);
            copy(out, s, 44, 16, -8, 32, 4, 4);
            copy(out, s, 48, 16, -8, 32, 4, 4);
            copy(out, s, 40, 20, 0, 32, 4, 12);
            copy(out, s, 44, 20, -8, 32, 4, 12);
            copy(out, s, 48, 20, -16, 32, 4, 12);
            copy(out, s, 52, 20, -8, 32, 4, 12);
        }
        return out;
    }

    /** Copies the {@code w×h} block at (x, y) by (dx, dy), mirrored horizontally (all in 64-pixel units, times {@code s}). */
    private static void copy(BufferedImage image, int s, int x, int y, int dx, int dy, int w, int h) {
        for (int yy = 0; yy < h * s; yy++) {
            for (int xx = 0; xx < w * s; xx++) {
                int pixel = image.getRGB(x * s + xx, y * s + yy);
                image.setRGB((x + dx) * s + (w * s - 1 - xx), (y + dy) * s + yy, pixel);
            }
        }
    }
}
