package com.dervarex.unweird;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A picture. {@code new Image("/images/logo.png")} loads from the classpath (e.g. src/main/resources);
 * {@code Image.file(path)} loads from disk. PNG, JPEG, BMP, GIF and TGA work.
 *
 * <p>Shown at its natural size (scaled down to fit), or at {@link #size(float, float)}.
 * If it can't be loaded, a placeholder is drawn and the reason is printed once.
 */
public class Image extends Widget {

    private static final float PLACEHOLDER = 48;

    private final String source;
    private final Supplier<byte[]> bytes;
    private float requestedWidth = Float.NaN;
    private float requestedHeight = Float.NaN;
    private float radius;

    /** An image on the classpath, e.g. {@code "/images/logo.png"}. */
    public Image(String resource) {
        Objects.requireNonNull(resource, "resource");
        String path = resource.startsWith("/") ? resource : "/" + resource;
        this.source = "classpath:" + path;
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
        this.bytes = () -> {
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    /** An image file on disk. */
    public static Image file(Path file) {
        return new Image(Objects.requireNonNull(file, "file"));
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

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        float width;
        float height;
        if (!Float.isNaN(requestedWidth)) {
            width = requestedWidth;
            height = requestedHeight;
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
