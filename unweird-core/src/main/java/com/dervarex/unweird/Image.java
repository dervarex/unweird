package com.dervarex.unweird;

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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A picture. {@code new Image("/images/logo.png")} loads from the classpath (e.g. src/main/resources);
 * {@code Image.file(path)} loads from disk, {@code Image.url("https://...")} downloads in the background
 * (each URL once per run; a blank placeholder shows meanwhile). PNG, JPEG, BMP, GIF and TGA work.
 *
 * <p>Shown at its natural size (scaled down to fit), or at {@link #size(float, float)}.
 * If it can't be loaded, a placeholder is drawn and the reason is printed once.
 */
public class Image extends Widget {

    private static final float PLACEHOLDER = 48;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    /** Downloads by URL, shared by every image showing the same URL. */
    private static final Map<String, CompletableFuture<byte[]>> DOWNLOADS = new ConcurrentHashMap<>();

    private final String source;
    private final Supplier<byte[]> bytes;
    /** For {@link #url}: the download, or null for local images. */
    private final String url;
    private float requestedWidth = Float.NaN;
    private float requestedHeight = Float.NaN;
    private float radius;

    /** An image on the classpath, e.g. {@code "/images/logo.png"}. */
    public Image(String resource) {
        Objects.requireNonNull(resource, "resource");
        String path = resource.startsWith("/") ? resource : "/" + resource;
        this.source = "classpath:" + path;
        this.url = null;
        this.bytes = () -> {
            try (InputStream in = Image.class.getResourceAsStream(path)) {
                if (in == null) {
                    throw new IllegalStateException("no such resource on the classpath");
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private Image(Path file) {
        this.source = "file:" + file.toAbsolutePath();
        this.url = null;
        this.bytes = () -> {
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private Image(String url, boolean remote) {
        this.source = url;
        this.url = url;
        this.bytes = () -> DOWNLOADS.get(url).join();
    }

    /** An image file on disk. */
    public static Image file(Path file) {
        return new Image(Objects.requireNonNull(file, "file"));
    }

    /** An image from the web, downloaded in the background. Without {@link #size} it shows as 48×48 until it arrives. */
    public static Image url(String url) {
        Objects.requireNonNull(url, "url");
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new IllegalArgumentException("Image.url needs an http(s) URL, got " + url);
        }
        return new Image(url, true);
    }

    /** For URL images: false while downloading, true once it's here (or failed, then the placeholder shows). */
    private boolean ready() {
        if (url == null) {
            return true;
        }
        CompletableFuture<byte[]> download = DOWNLOADS.computeIfAbsent(url, Image::download);
        return download.isDone();
    }

    private static CompletableFuture<byte[]> download(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "unweird")
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() / 100 != 2) {
                        throw new IllegalStateException("HTTP " + response.statusCode());
                    }
                    return response.body();
                })
                .whenComplete((body, error) -> Unweird.refresh());
    }

    /** Shows the image at this size (logical pixels) instead of its natural size. */
    public Image size(float width, float height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size must be positive, got " + width + "x" + height);
        }
        requestedWidth = width;
        requestedHeight = height;
        return this;
    }

    /** Rounds the corners; use half the size for a circle (avatars). */
    public Image radius(float radius) {
        this.radius = Math.max(0, radius);
        return this;
    }

    @Override public Image below(Widget other) { super.below(other); return this; }
    @Override public Image above(Widget other) { super.above(other); return this; }
    @Override public Image rightOf(Widget other) { super.rightOf(other); return this; }
    @Override public Image leftOf(Widget other) { super.leftOf(other); return this; }
    @Override public Image alignLeft(Widget other) { super.alignLeft(other); return this; }
    @Override public Image alignRight(Widget other) { super.alignRight(other); return this; }
    @Override public Image gap(float pixels) { super.gap(pixels); return this; }
    @Override public Image visibleIf(java.util.function.BooleanSupplier condition) { super.visibleIf(condition); return this; }
    @Override public Image enabledIf(java.util.function.BooleanSupplier condition) { super.enabledIf(condition); return this; }

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        float width;
        float height;
        if (!Float.isNaN(requestedWidth)) {
            width = requestedWidth;
            height = requestedHeight;
        } else if (!ready()) {
            width = height = PLACEHOLDER;
        } else {
            int handle = canvas.image(source, bytes);
            if (handle < 0) {
                width = height = PLACEHOLDER;
            } else {
                int[] natural = canvas.imageSize(handle);
                width = natural[0];
                height = natural[1];
            }
        }
        if (width > maxWidth && width > 0) {
            height *= maxWidth / width;
            width = maxWidth;
        }
        return new Size(width, height);
    }

    @Override
    protected void draw(Canvas canvas) {
        if (!ready()) {
            canvas.fillRect(getX(), getY(), getWidth(), getHeight(), radius, canvas.getTheme().getMutedText().withAlpha(0.1f));
            return;
        }
        int handle = canvas.image(source, bytes);
        if (handle < 0) {
            var theme = canvas.getTheme();
            canvas.fillRect(getX(), getY(), getWidth(), getHeight(), radius, theme.getMutedText().withAlpha(0.15f));
            float cx = getX() + getWidth() / 2;
            float cy = getY() + getHeight() / 2;
            float s = Math.min(getWidth(), getHeight()) / 6;
            canvas.polyline(new float[]{cx - s, cy - s, cx + s, cy + s}, 2, theme.getMutedText());
            canvas.polyline(new float[]{cx + s, cy - s, cx - s, cy + s}, 2, theme.getMutedText());
            return;
        }
        canvas.drawImage(handle, getX(), getY(), getWidth(), getHeight(), radius);
    }
}
