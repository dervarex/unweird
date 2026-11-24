package com.dervarex.unweird;

import static org.lwjgl.glfw.GLFW.*;

/**
 * What's held down right now, for games and anything that moves while a key is held:
 * {@code if (Keyboard.held("Left")) x -= speed * dt;}. Key names as in {@link KeyPress} (letters follow the keyboard
 * layout), plus {@code Shift}, {@code Ctrl}, {@code Alt} for either side. UI thread only.
 */
public final class Keyboard {

    private Keyboard() {
    }

    /** Whether the key named {@code key} (e.g. {@code "A"}, {@code "Space"}, {@code "Left"}, {@code "Shift"}) is held. */
    public static boolean held(String key) {
        String wanted = key.strip();
        for (int code : Unweird.heldKeys()) {
            if (name(code).equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the key went down since the last frame: for things that happen once per press (jumping, shooting).
     * Unlike checking {@link #held} each frame, this doesn't miss a tap shorter than a frame.
     */
    public static boolean pressed(String key) {
        String wanted = key.strip();
        for (int code : Unweird.pressedKeys()) {
            if (name(code).equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** Whether any of the keys is held, e.g. {@code anyHeld("Left", "A")}. */
    public static boolean anyHeld(String... keys) {
        for (String key : keys) {
            if (held(key)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a mouse button is held. */
    public static boolean held(Pointer.Button button) {
        int glfw = switch (button) {
            case LEFT -> GLFW_MOUSE_BUTTON_LEFT;
            case RIGHT -> GLFW_MOUSE_BUTTON_RIGHT;
            case MIDDLE -> GLFW_MOUSE_BUTTON_MIDDLE;
            case NONE -> -1;
        };
        return Unweird.heldButtons().contains(glfw);
    }

    static String name(int code) {
        return switch (code) {
            case GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT -> "Shift";
            case GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL -> "Ctrl";
            case GLFW_KEY_LEFT_ALT, GLFW_KEY_RIGHT_ALT -> "Alt";
            default -> new KeyPress(code, 0, false).name();
        };
    }
}
