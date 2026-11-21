package com.dervarex.unweird;

import java.util.Locale;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * A key press for custom widgets and shortcuts. Letters follow the keyboard layout
 * (Ctrl+Z is the key labeled Z, also on QWERTZ).
 *
 * <pre>{@code
 * if (key.is("Ctrl+S")) save();
 * if (key.is("Left")) moveLeft();
 * }</pre>
 *
 * Key names: letters, digits, {@code F1}..{@code F12}, {@code Enter}, {@code Escape}, {@code Tab}, {@code Space},
 * {@code Backspace}, {@code Delete}, {@code Insert}, {@code Home}, {@code End}, {@code PageUp}, {@code PageDown},
 * {@code Left}, {@code Right}, {@code Up}, {@code Down}, {@code Plus}, {@code Minus}.
 * Modifiers: {@code Ctrl}, {@code Shift}, {@code Alt}, {@code Meta}. On macOS, {@code Ctrl} in a pattern also matches Cmd,
 * so one shortcut works everywhere.
 */
public record KeyPress(int code, int mods, boolean repeat) {

    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    private static final Map<Integer, String> NAMES = Map.ofEntries(
            Map.entry(GLFW_KEY_ENTER, "Enter"), Map.entry(GLFW_KEY_KP_ENTER, "Enter"),
            Map.entry(GLFW_KEY_ESCAPE, "Escape"), Map.entry(GLFW_KEY_TAB, "Tab"),
            Map.entry(GLFW_KEY_SPACE, "Space"), Map.entry(GLFW_KEY_BACKSPACE, "Backspace"),
            Map.entry(GLFW_KEY_DELETE, "Delete"), Map.entry(GLFW_KEY_INSERT, "Insert"),
            Map.entry(GLFW_KEY_HOME, "Home"), Map.entry(GLFW_KEY_END, "End"),
            Map.entry(GLFW_KEY_PAGE_UP, "PageUp"), Map.entry(GLFW_KEY_PAGE_DOWN, "PageDown"),
            Map.entry(GLFW_KEY_LEFT, "Left"), Map.entry(GLFW_KEY_RIGHT, "Right"),
            Map.entry(GLFW_KEY_UP, "Up"), Map.entry(GLFW_KEY_DOWN, "Down"),
            Map.entry(GLFW_KEY_EQUAL, "Plus"), Map.entry(GLFW_KEY_KP_ADD, "Plus"),
            Map.entry(GLFW_KEY_MINUS, "Minus"), Map.entry(GLFW_KEY_KP_SUBTRACT, "Minus"));

    /** The key's name, e.g. {@code "S"}, {@code "Enter"}, {@code "F5"}; {@code "?"} for keys without one. */
    public String name() {
        if (code >= GLFW_KEY_A && code <= GLFW_KEY_Z) {
            return String.valueOf((char) ('A' + code - GLFW_KEY_A));
        }
        if (code >= GLFW_KEY_0 && code <= GLFW_KEY_9) {
            return String.valueOf((char) ('0' + code - GLFW_KEY_0));
        }
        if (code >= GLFW_KEY_KP_0 && code <= GLFW_KEY_KP_9) {
            return String.valueOf((char) ('0' + code - GLFW_KEY_KP_0));
        }
        if (code >= GLFW_KEY_F1 && code <= GLFW_KEY_F25) {
            return "F" + (code - GLFW_KEY_F1 + 1);
        }
        return NAMES.getOrDefault(code, "?");
    }

    public boolean shift() {
        return (mods & GLFW_MOD_SHIFT) != 0;
    }

    public boolean ctrl() {
        return (mods & GLFW_MOD_CONTROL) != 0;
    }

    public boolean alt() {
        return (mods & GLFW_MOD_ALT) != 0;
    }

    public boolean meta() {
        return (mods & GLFW_MOD_SUPER) != 0;
    }

    /** Whether this press is exactly {@code pattern}, e.g. {@code "Ctrl+Shift+Z"} (case doesn't matter). */
    public boolean is(String pattern) {
        Shortcut shortcut = Shortcut.parse(pattern);
        return shortcut.matches(this);
    }

    /** A parsed pattern such as {@code "Ctrl+S"}. */
    record Shortcut(String key, boolean ctrl, boolean shift, boolean alt, boolean meta) {

        static Shortcut parse(String pattern) {
            String trimmed = pattern.strip();
            if (trimmed.endsWith("++")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1) + "Plus"; // "Ctrl++" means Ctrl and the plus key
            }
            String[] parts = trimmed.split("\\s*\\+\\s*", -1);
            boolean ctrl = false;
            boolean shift = false;
            boolean alt = false;
            boolean meta = false;
            String key = null;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part.isEmpty()) {
                    throw new IllegalArgumentException("Incomplete shortcut \"" + pattern + "\"");
                }
                switch (part.toLowerCase(Locale.ROOT)) {
                    case "ctrl", "control" -> ctrl = true;
                    case "shift" -> shift = true;
                    case "alt", "option" -> alt = true;
                    case "meta", "cmd", "super", "win" -> meta = true;
                    case "esc" -> key = "Escape";
                    case "return" -> key = "Enter";
                    case "del" -> key = "Delete";
                    default -> key = part.length() == 1 ? part.toUpperCase(Locale.ROOT) : capitalize(part);
                }
            }
            if (key == null) {
                throw new IllegalArgumentException("No key in shortcut \"" + pattern + "\"");
            }
            return new Shortcut(key, ctrl, shift, alt, meta);
        }

        boolean matches(KeyPress press) {
            if (!press.name().equalsIgnoreCase(key)) {
                return false;
            }
            boolean ctrlDown = press.ctrl() || (MAC && press.meta() && !meta);
            boolean metaDown = press.meta() && !(MAC && ctrl && !meta);
            return ctrlDown == ctrl && press.shift() == shift && press.alt() == alt && metaDown == meta;
        }

        private static String capitalize(String word) {
            String lower = word.toLowerCase(Locale.ROOT);
            return switch (lower) {
                case "pageup" -> "PageUp";
                case "pagedown" -> "PageDown";
                default -> Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            };
        }
    }
}
