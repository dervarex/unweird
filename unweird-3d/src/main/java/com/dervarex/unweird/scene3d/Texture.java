package com.dervarex.unweird.scene3d;

import com.dervarex.unweird.Unweird;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * An image for {@link Material}s. Loaded from the classpath, a file, a URL (in the background) or a
 * {@link BufferedImage}; {@link #set} swaps the picture later (e.g. a new skin), the GPU copy follows on the next frame.
 */
public final class Texture {

    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10)).build();

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
            return of(ImageIO.read(in));
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

    public static Texture of(BufferedImage image) {
        Texture texture = new Texture();
        texture.set(image);
        return texture;
    }

    /** A 1×1 white texture. */
    public static Texture blank() {
        Texture texture = new Texture();
        texture.setPixels(1, 1, new byte[]{-1, -1, -1, -1});
        return texture;
    }

    private static Texture bytes(byte[] data) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                throw new IllegalArgumentException("Not an image format Java can read");
            }
            return of(image);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Pixel-sharp scaling (no blur): right for pixel art like Minecraft skins. */
    public Texture nearest() {
        nearest = true;
        version++;
        return this;
    }

    /** Replaces the picture. Safe from any thread. */
    public void set(BufferedImage image) {
        Objects.requireNonNull(image, "image");
        int w = image.getWidth();
        int h = image.getHeight();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        byte[] rgba = new byte[w * h * 4];
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            rgba[i * 4] = (byte) (p >> 16);
            rgba[i * 4 + 1] = (byte) (p >> 8);
            rgba[i * 4 + 2] = (byte) p;
            rgba[i * 4 + 3] = (byte) (p >>> 24);
        }
        setPixels(w, h, rgba);
    }

    /** Replaces the picture with one downloaded in the background. Errors are kept in {@link #problem()}. */
    public void load(String url) {
        problem = null;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "unweird").timeout(Duration.ofSeconds(20)).build();
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).whenComplete((response, error) -> {
            try {
                if (error != null) {
                    throw new IOException(error.getMessage(), error);
                }
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
                if (image == null) {
                    throw new IOException("not an image");
                }
                set(image);
            } catch (IOException | RuntimeException e) {
                problem = "Couldn't load " + url + ": " + e.getMessage();
            }
            Unweird.refresh();
        });
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
