package com.dervarex.unweird.style;

/** An RGBA color, components in 0..1. */
public record Color(float r, float g, float b, float a) {

    public static final Color WHITE = new Color(1, 1, 1, 1);
    public static final Color BLACK = new Color(0, 0, 0, 1);
    public static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    /** Parses {@code #rgb}, {@code #rrggbb} or {@code #rrggbbaa} (the {@code #} is optional). */
    public static Color hex(String hex) {
        String h = hex.startsWith("#") ? hex.substring(1) : hex;
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        }
        if (h.length() != 6 && h.length() != 8) {
            throw new IllegalArgumentException("Not a hex color: " + hex);
        }
        long v = Long.parseLong(h, 16);
        if (h.length() == 6) {
            v = (v << 8) | 0xff;
        }
        return new Color(
                ((v >> 24) & 0xff) / 255f,
                ((v >> 16) & 0xff) / 255f,
                ((v >> 8) & 0xff) / 255f,
                (v & 0xff) / 255f);
    }

    public static Color rgba(int r, int g, int b, float a) {
        return new Color(r / 255f, g / 255f, b / 255f, a);
    }

    /** Blends towards {@code other}; {@code t = 0} is this color, {@code t = 1} is {@code other}. */
    public Color mix(Color other, float t) {
        return new Color(
                r + (other.r - r) * t,
                g + (other.g - g) * t,
                b + (other.b - b) * t,
                a + (other.a - a) * t);
    }

    public Color withAlpha(float alpha) {
        return new Color(r, g, b, alpha);
    }
}
