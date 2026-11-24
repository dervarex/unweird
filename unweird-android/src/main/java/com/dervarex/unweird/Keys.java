package com.dervarex.unweird;

import android.view.KeyEvent;

/** Android key codes and meta state → the GLFW codes and mod bits unweird uses everywhere. */
final class Keys {

    static final int ENTER = 257;
    static final int BACKSPACE = 259;
    static final int DELETE = 261;

    private Keys() {
    }

    /** The GLFW key code for an Android key code, or -1 for keys unweird doesn't know. */
    static int code(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return 65 + keyCode - KeyEvent.KEYCODE_A;
        }
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return 48 + keyCode - KeyEvent.KEYCODE_0;
        }
        if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return 320 + keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        }
        if (keyCode >= KeyEvent.KEYCODE_F1 && keyCode <= KeyEvent.KEYCODE_F12) {
            return 290 + keyCode - KeyEvent.KEYCODE_F1;
        }
        return switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER -> ENTER;
            case KeyEvent.KEYCODE_NUMPAD_ENTER -> 335;
            case KeyEvent.KEYCODE_DEL -> BACKSPACE;
            case KeyEvent.KEYCODE_FORWARD_DEL -> DELETE;
            case KeyEvent.KEYCODE_TAB -> 258;
            case KeyEvent.KEYCODE_ESCAPE -> 256;
            case KeyEvent.KEYCODE_SPACE -> 32;
            case KeyEvent.KEYCODE_DPAD_RIGHT -> 262;
            case KeyEvent.KEYCODE_DPAD_LEFT -> 263;
            case KeyEvent.KEYCODE_DPAD_DOWN -> 264;
            case KeyEvent.KEYCODE_DPAD_UP -> 265;
            case KeyEvent.KEYCODE_PAGE_UP -> 266;
            case KeyEvent.KEYCODE_PAGE_DOWN -> 267;
            case KeyEvent.KEYCODE_MOVE_HOME -> 268;
            case KeyEvent.KEYCODE_MOVE_END -> 269;
            case KeyEvent.KEYCODE_INSERT -> 260;
            case KeyEvent.KEYCODE_EQUALS -> 61;
            case KeyEvent.KEYCODE_MINUS -> 45;
            case KeyEvent.KEYCODE_NUMPAD_ADD -> 334;
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> 333;
            default -> -1;
        };
    }

    /** {@link KeyPress} mod bits for an Android meta state. */
    static int modifiers(int metaState) {
        int mods = 0;
        if ((metaState & KeyEvent.META_SHIFT_ON) != 0) {
            mods |= KeyPress.MOD_SHIFT;
        }
        if ((metaState & KeyEvent.META_CTRL_ON) != 0) {
            mods |= KeyPress.MOD_CONTROL;
        }
        if ((metaState & KeyEvent.META_ALT_ON) != 0) {
            mods |= KeyPress.MOD_ALT;
        }
        if ((metaState & KeyEvent.META_META_ON) != 0) {
            mods |= KeyPress.MOD_SUPER;
        }
        return mods;
    }
}
