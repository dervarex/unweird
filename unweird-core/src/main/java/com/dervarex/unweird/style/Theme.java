package com.dervarex.unweird.style;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.With;

/**
 * Colors and shapes used by every widget.
 *
 * <p>Start from a built-in and change what you need: {@code Theme.dark().withAccent(Color.hex("#7c5cff"))},
 * or build your own with the constructor.
 */
@Getter
@With
@AllArgsConstructor
public class Theme {

    /** Window background. */
    private final Color background;
    /** Cards and raised surfaces. */
    private final Color surface;
    private final Color border;
    private final Color shadow;
    private final Color text;
    /** Secondary text such as subtitles. */
    private final Color mutedText;
    private final Color accent;
    /** Text drawn on top of the accent color. */
    private final Color onAccent;
    /** Destructive actions, e.g. {@code new Button("Delete").danger()}. */
    private final Color danger;
    private final Color onDanger;
    /** Corner radius of buttons and fields; cards use twice this. */
    private final float radius;

    public static Theme light() {
        return new Theme(
                Color.hex("#f5f5f7"),
                Color.hex("#ffffff"),
                Color.hex("#e6e6eb"),
                Color.rgba(0, 0, 0, 0.08f),
                Color.hex("#1c1c21"),
                Color.hex("#6e6e78"),
                Color.hex("#5b5bd6"),
                Color.WHITE,
                Color.hex("#d93d42"),
                Color.WHITE,
                8);
    }

    /** Whether this is a dark theme (judged by the background's brightness). */
    public boolean isDark() {
        float luminance = 0.2126f * background.r() + 0.7152f * background.g() + 0.0722f * background.b();
        return luminance < 0.5f;
    }

    public static Theme dark() {
        return new Theme(
                Color.hex("#111114"),
                Color.hex("#1a1a1f"),
                Color.hex("#2b2b33"),
                Color.rgba(0, 0, 0, 0.45f),
                Color.hex("#ececf1"),
                Color.hex("#9c9ca8"),
                Color.hex("#7c6cff"),
                Color.WHITE,
                Color.hex("#e5484d"),
                Color.WHITE,
                8);
    }
}
