package com.dervarex.unweird;

import java.util.Objects;
import java.util.function.Function;

/**
 * A decoded picture: RGBA bytes (not premultiplied), top row first. {@link #decode} reads PNG, JPEG and whatever
 * else the platform can decode (Java's ImageIO on desktop, Android's own decoders there, including WebP).
 */
public record Pixels(int width, int height, byte[] rgba) {

    public Pixels {
        Objects.requireNonNull(rgba, "rgba");
        if (width <= 0 || height <= 0 || rgba.length != width * height * 4) {
            throw new IllegalArgumentException("need width * height * 4 bytes for " + width + "x" + height + ", got " + rgba.length);
        }
    }

    /** How encoded images are decoded on this platform; the Android backend replaces it. */
    static volatile Function<byte[], Pixels> decoder = Pixels::decodeWithImageIo;

    /** Decodes an image file's bytes. Throws {@link IllegalArgumentException} if they aren't an image this platform reads. */
    public static Pixels decode(byte[] encoded) {
        Pixels pixels = decoder.apply(Objects.requireNonNull(encoded, "encoded"));
        if (pixels == null) {
            throw new IllegalArgumentException("not an image format this platform can read");
        }
        return pixels;
    }

    private static Pixels decodeWithImageIo(byte[] encoded) {
        java.awt.image.BufferedImage image;
        try {
            image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(encoded));
        } catch (java.io.IOException e) {
            return null;
        }
        return image == null ? null : of(image);
    }

    /** From a {@code BufferedImage}. Desktop only: Android has no {@code java.awt}. */
    public static Pixels of(java.awt.image.BufferedImage image) {
        return fromArgb(image.getWidth(), image.getHeight(), image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
    }

    /** From packed ARGB ints (as {@code BufferedImage.getRGB} and Android's {@code Bitmap.getPixels} give them). */
    public static Pixels fromArgb(int width, int height, int[] argb) {
        byte[] rgba = new byte[width * height * 4];
        for (int i = 0; i < width * height; i++) {
            int p = argb[i];
            rgba[i * 4] = (byte) (p >> 16);
            rgba[i * 4 + 1] = (byte) (p >> 8);
            rgba[i * 4 + 2] = (byte) p;
            rgba[i * 4 + 3] = (byte) (p >>> 24);
        }
        return new Pixels(width, height, rgba);
    }
}
