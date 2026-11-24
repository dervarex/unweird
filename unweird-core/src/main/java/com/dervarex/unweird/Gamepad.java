package com.dervarex.unweird;

import org.lwjgl.glfw.GLFWGamepadState;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The first connected game controller (Xbox, PlayStation, Switch Pro and most others, mapped to the Xbox layout):
 * {@code if (Gamepad.held(Gamepad.Button.A)) jump();}, {@code float x = Gamepad.axis(Gamepad.Axis.LEFT_X);}.
 * Read before every frame the window draws (games draw continuously); UI thread only. Without a controller everything
 * is released and centered.
 */
public final class Gamepad {

    /** Buttons by their Xbox names (Cross is A, Circle is B, Square is X, Triangle is Y). */
    public enum Button { A, B, X, Y, LEFT_BUMPER, RIGHT_BUMPER, BACK, START, GUIDE, LEFT_STICK, RIGHT_STICK, UP, RIGHT, DOWN, LEFT }

    /** Sticks go -1..1 (right and down are positive), triggers 0..1. */
    public enum Axis { LEFT_X, LEFT_Y, RIGHT_X, RIGHT_Y, LEFT_TRIGGER, RIGHT_TRIGGER }

    private static final float DEAD_ZONE = 0.15f;

    private static boolean connected;
    private static final boolean[] held = new boolean[Button.values().length];
    private static final boolean[] before = new boolean[Button.values().length];
    private static final float[] axes = new float[Axis.values().length];
    private static GLFWGamepadState state;

    private Gamepad() {
    }

    /** Whether a controller is plugged in. */
    public static boolean connected() {
        return connected;
    }

    public static boolean held(Button button) {
        return held[button.ordinal()];
    }

    /** Went down since the last frame. */
    public static boolean pressed(Button button) {
        return held[button.ordinal()] && !before[button.ordinal()];
    }

    /** A stick (-1..1, small movements count as 0) or trigger (0..1). */
    public static float axis(Axis axis) {
        return axes[axis.ordinal()];
    }

    /** Reads the controller. Called by the window before every frame. */
    static void poll() {
        System.arraycopy(held, 0, before, 0, held.length);
        if (state == null) {
            state = GLFWGamepadState.malloc();
        }
        connected = false;
        for (int jid = GLFW_JOYSTICK_1; jid <= GLFW_JOYSTICK_LAST; jid++) {
            if (glfwJoystickIsGamepad(jid) && glfwGetGamepadState(jid, state)) {
                connected = true;
                break;
            }
        }
        for (int i = 0; i < held.length; i++) {
            held[i] = connected && state.buttons(i) == GLFW_PRESS;
        }
        for (int i = 0; i < axes.length; i++) {
            float value = connected ? state.axes(i) : 0;
            if (i >= Axis.LEFT_TRIGGER.ordinal()) {
                value = connected ? (value + 1) / 2 : 0; // GLFW reports triggers -1..1
                axes[i] = value < DEAD_ZONE ? 0 : value;
            } else {
                axes[i] = Math.abs(value) < DEAD_ZONE ? 0 : value;
            }
        }
    }
}
