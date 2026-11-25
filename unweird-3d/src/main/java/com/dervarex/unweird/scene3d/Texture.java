package com.dervarex.unweird.scene3d;

import com.dervarex.unweird.Unweird;

import com.dervarex.unweird.Pixels;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * An image for {@link Material}s. Loaded from the classpath, a file, a URL (in the background) or {@link Pixels}
 * (on desktop, {@code Pixels.of(bufferedImage)}); {@link #set} swaps the picture later (e.g. a new skin), the GPU copy
 * follows on the next frame.
 */
public final class Texture {

    private volatile int width;
    private volatile int height;
    /** RGBA, top row first. */
    private volatile byte[] pixels;
    /** Bumped on every change so renderers know to upload again. */
    private volatile int version;
    private volatile String problem;
    private boolean nearest;

    private Texture() {
    }

    /** From the classpath, e.g. {@code "/skins/steve.png"}. */
    public static Texture resource(String path) {
        String absolute = path.startsWith("/") ? path : "/" + path;
        try (InputStream in = Texture.class.getResourceAsStream(absolute)) {
            if (in == null) {
                throw new IllegalArgumentException("No such resource: " + absolute);
            }
            return bytes(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Texture file(Path file) {
        try {
            return bytes(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Downloads in the background; until it arrives (or if it fails) the texture is plain white. */
    public static Texture url(String url) {
        Texture texture = blank();
        texture.load(url);
        return texture;
    }

    public static Texture of(Pixels pixels) {
        Texture texture = new Texture();
        texture.set(pixels);
        return texture;
    }

    /** A 1×1 white texture. */
    public static Texture blank() {
        Texture texture = new Texture();
        texture.setPixels(1, 1, new byte[]{-1, -1, -1, -1});
        return texture;
    }

    private static Texture bytes(byte[] data) {
        return of(Pixels.decode(data));
    }

    /** Pixel-sharp scaling (no blur): right for pixel art like Minecraft skins. */
    public Texture nearest() {
        nearest = true;
        version++;
        return this;
    }

    /** Replaces the picture. Safe from any thread. */
    public void set(Pixels picture) {
        Objects.requireNonNull(picture, "picture");
        setPixels(picture.width(), picture.height(), picture.rgba().clone());
    }

    /** Replaces the picture with one downloaded in the background. Errors are kept in {@link #problem()}. */
    public void load(String url) {
        problem = null;
        Thread thread = new Thread(() -> {
            try {
                set(Pixels.decode(download(url)));
            } catch (IOException | RuntimeException e) {
                problem = "Couldn't load " + url + ": " + e.getMessage();
            }
            Unweird.refresh();
        }, "unweird-texture");
        thread.setDaemon(true);
        thread.start();
    }

    private static byte[] download(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(20_000);
        connection.setRequestProperty("User-Agent", "unweird");
        try {
            if (connection.getResponseCode() / 100 != 2) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            try (InputStream in = connection.getInputStream()) {
                return in.readAllBytes();
            }
        } finally {
            connection.disconnect();
        }
    }

    private synchronized void setPixels(int w, int h, byte[] rgba) {
        width = w;
        height = h;
        pixels = rgba;
        version++;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Why the last {@link #url}/{@link #load} failed, or null. */
    public String problem() {
        return problem;
    }

    /** RGBA of one pixel (for tests and tools). */
    public int argb(int x, int y) {
        byte[] p = pixels;
        int i = (y * width + x) * 4;
        return (p[i + 3] & 0xff) << 24 | (p[i] & 0xff) << 16 | (p[i + 1] & 0xff) << 8 | (p[i + 2] & 0xff);
    }

    int version() {
        return version;
    }

    boolean isNearest() {
        return nearest;
    }

    /** Pixels and size, read together. */
    synchronized Snapshot snapshot() {
        return new Snapshot(width, height, pixels, version);
    }

    record Snapshot(int width, int height, byte[] pixels, int version) {
    }
}
