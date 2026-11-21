package com.dervarex.unweird;

import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.style.Color;
import com.dervarex.unweird.style.Theme;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgCreateFontMem;
import static org.lwjgl.nanovg.NanoVG.nvgEndFrame;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;
import static org.lwjgl.nanovg.NanoVGGL3.nvgDelete;
import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * The native window: GLFW for the window and input, OpenGL + NanoVG for drawing.
 * Everything here runs on the UI thread, except {@link #requestRender()}.
 *
 * <p>Rendering is event-driven: the loop sleeps in {@code glfwWaitEvents} and only draws
 * when something changed (or a focused field's caret blinks).
 */
final class Window implements Host {

    private static final int DEFAULT_WIDTH = 960;
    private static final int DEFAULT_HEIGHT = 640;
    private static final String FONT_DIR = "/com/dervarex/unweird/fonts/";
    private static final double BLINK_SECONDS = 0.53;
    private static final double MULTI_CLICK_SECONDS = 0.4;
    private static final double MULTI_CLICK_DISTANCE = 4;

    private final long handle;
    private final long vg;
    private final long handCursor;
    private final long ibeamCursor;
    private final ByteBuffer regularFontData;
    private final ByteBuffer semiboldFontData;
    private final Canvas canvas;
    private final AtomicBoolean dirty = new AtomicBoolean(true);

    private Theme theme;
    private Screen screen;
    private Widget hovered;
    private Widget pressed;
    private Widget focused;
    private double nextBlink;

    private int clickCount;
    private double lastClickTime;
    private double lastClickX;
    private double lastClickY;

    /** Cursor position in window coordinates; negative when the cursor is outside. */
    private double cursorX = -1;
    private double cursorY = -1;
    /** Window coordinates → logical pixels. */
    private float windowToLogical = 1;

    Window(Theme theme) {
        this.theme = theme;

        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) {
            throw new IllegalStateException("unweird: could not initialise GLFW. Is a display available?");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        glfwWindowHint(GLFW_STENCIL_BITS, 8);

        handle = glfwCreateWindow(DEFAULT_WIDTH, DEFAULT_HEIGHT, "unweird", NULL, NULL);
        if (handle == NULL) {
            glfwTerminate();
            throw new IllegalStateException("unweird: could not create a window (OpenGL 3.3 is required)");
        }
        centerOnPrimaryMonitor();

        glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        glfwSwapInterval(1);

        vg = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (vg == NULL) {
            throw new IllegalStateException("unweird: could not initialise NanoVG");
        }
        regularFontData = loadResource(FONT_DIR + "NotoSans-Regular.ttf");
        semiboldFontData = loadResource(FONT_DIR + "NotoSans-SemiBold.ttf");
        int regular = nvgCreateFontMem(vg, "regular", regularFontData, false);
        int semibold = nvgCreateFontMem(vg, "semibold", semiboldFontData, false);
        if (regular < 0 || semibold < 0) {
            throw new IllegalStateException("unweird: could not load the bundled fonts");
        }
        canvas = new Canvas(vg, regular, semibold, theme);
        handCursor = glfwCreateStandardCursor(GLFW_POINTING_HAND_CURSOR);
        ibeamCursor = glfwCreateStandardCursor(GLFW_IBEAM_CURSOR);

        installCallbacks();
    }

    void show(Screen next) {
        if (hovered != null) {
            hovered.hovered = false;
            hovered = null;
        }
        if (pressed != null) {
            pressed.pressed = false;
            pressed = null;
        }
        focus(null, false);
        screen = next;
        Title title = next.getClass().getAnnotation(Title.class);
        glfwSetWindowTitle(handle, title != null ? title.value() : "unweird");

        // Put the cursor in the first text field, so a login form can be typed into right away.
        for (Widget widget : next.focusables()) {
            if (widget instanceof Field) {
                focus(widget, false);
                break;
            }
        }
        dirty.set(true);
    }

    void theme(Theme next) {
        theme = next;
        dirty.set(true);
    }

    /** Thread-safe: asks the UI thread to re-evaluate and redraw. */
    void requestRender() {
        dirty.set(true);
        glfwPostEmptyEvent();
    }

    /** Runs until the window is closed. Blocks the calling (UI) thread. */
    void run() {
        glfwShowWindow(handle);
        while (!glfwWindowShouldClose(handle)) {
            if (dirty.getAndSet(false)) {
                render();
            } else if (focused != null && focused.blinks()) {
                double wait = nextBlink - glfwGetTime();
                if (wait <= 0) {
                    focused.caretOn = !focused.caretOn;
                    nextBlink = glfwGetTime() + BLINK_SECONDS;
                    dirty.set(true);
                } else {
                    glfwWaitEventsTimeout(wait);
                }
            } else {
                glfwWaitEvents();
            }
        }
    }

    void close() {
        org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(handle);
        canvas.free();
        nvgDelete(vg);
        MemoryUtil.memFree(regularFontData);
        MemoryUtil.memFree(semiboldFontData);
        glfwDestroyCursor(handCursor);
        glfwDestroyCursor(ibeamCursor);
        glfwDestroyWindow(handle);
        glfwTerminate();
        GLFWErrorCallback previous = glfwSetErrorCallback(null);
        if (previous != null) {
            previous.free();
        }
    }

    private void render() {
        int[] fbWidth = new int[1], fbHeight = new int[1], winWidth = new int[1], winHeight = new int[1];
        float[] scaleX = new float[1], scaleY = new float[1];
        glfwGetFramebufferSize(handle, fbWidth, fbHeight);
        glfwGetWindowSize(handle, winWidth, winHeight);
        glfwGetWindowContentScale(handle, scaleX, scaleY);
        if (fbWidth[0] == 0 || fbHeight[0] == 0 || winWidth[0] == 0) {
            return; // minimized
        }

        // Some platforms (macOS, Wayland) already scale window coordinates and only the framebuffer is larger;
        // others (X11, Windows) report physical pixels and a content scale. Logical pixels cover both.
        float framebufferPerWindow = fbWidth[0] / (float) winWidth[0];
        float scale = Math.max(framebufferPerWindow, scaleX[0]);
        windowToLogical = framebufferPerWindow / scale;

        Color bg = theme.getBackground();
        glViewport(0, 0, fbWidth[0], fbHeight[0]);
        glClearColor(bg.r(), bg.g(), bg.b(), 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

        nvgBeginFrame(vg, fbWidth[0] / scale, fbHeight[0] / scale, scale);
        canvas.setTheme(theme);
        if (screen != null) {
            screen.render(canvas, fbWidth[0] / scale, fbHeight[0] / scale);
        }
        nvgEndFrame(vg);
        glfwSwapBuffers(handle);

        updateHover(); // the layout may have moved something under the cursor
    }

    private void installCallbacks() {
        glfwSetCursorPosCallback(handle, (window, x, y) -> {
            cursorX = x;
            cursorY = y;
            if (pressed != null && pressed.focusable()) {
                pressed.mouseDrag(logical(x), logical(y));
                resetBlink();
                dirty.set(true);
            }
            updateHover();
        });
        glfwSetCursorEnterCallback(handle, (window, entered) -> {
            if (!entered) {
                cursorX = -1;
                cursorY = -1;
                updateHover();
            }
        });
        glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
            if (button != GLFW_MOUSE_BUTTON_LEFT) {
                return;
            }
            if (action == GLFW_PRESS) {
                countClicks();
                if (hovered == null) {
                    focus(null, false); // clicking empty space drops focus
                    return;
                }
                pressed = hovered;
                pressed.pressed = true;
                if (pressed.focusable()) {
                    focus(pressed, false);
                    pressed.mouseDown(logical(cursorX), logical(cursorY), clickCount, (mods & GLFW_MOD_SHIFT) != 0);
                }
                dirty.set(true);
            } else if (action == GLFW_RELEASE && pressed != null) {
                Widget released = pressed;
                pressed = null;
                released.pressed = false;
                if (released == hovered && released.clickable()) {
                    safely(released, released::click);
                }
                dirty.set(true);
            }
        });
        glfwSetKeyCallback(handle, (window, key, scancode, action, mods) -> {
            if (action == GLFW_RELEASE) {
                return;
            }
            resetBlink();
            if (key == GLFW_KEY_TAB) {
                moveFocus((mods & GLFW_MOD_SHIFT) != 0 ? -1 : 1);
            } else if (focused != null) {
                focused.keyPressed(key, mods, this);
            }
            dirty.set(true); // the key may have changed state that lambdas read
        });
        glfwSetCharCallback(handle, (window, codePoint) -> {
            resetBlink();
            if (focused != null) {
                focused.typed(codePoint, this);
            }
            dirty.set(true);
        });
        glfwSetFramebufferSizeCallback(handle, (window, w, h) -> dirty.set(true));
        glfwSetWindowContentScaleCallback(handle, (window, sx, sy) -> dirty.set(true));
        glfwSetWindowRefreshCallback(handle, window -> render());
    }

    private void updateHover() {
        Widget target = null;
        if (screen != null && cursorX >= 0 && cursorY >= 0) {
            target = screen.hitTest(logical(cursorX), logical(cursorY));
        }
        if (target != hovered) {
            if (hovered != null) {
                hovered.hovered = false;
            }
            hovered = target;
            if (target != null) {
                target.hovered = true;
            }
            // while dragging a selection, keep the I-beam even when leaving the field
            Widget shapeSource = pressed != null ? pressed : target;
            glfwSetCursor(handle, shapeSource == null ? NULL : switch (shapeSource.cursorShape()) {
                case HAND -> handCursor;
                case IBEAM -> ibeamCursor;
                case ARROW -> NULL;
            });
            dirty.set(true);
        }
    }

    private float logical(double windowCoordinate) {
        return (float) windowCoordinate * windowToLogical;
    }

    private void focus(Widget next, boolean viaKeyboard) {
        if (focused == next) {
            if (next != null) {
                next.focusVisible = viaKeyboard;
            }
            return;
        }
        if (focused != null) {
            focused.focused = false;
            focused.focusVisible = false;
        }
        focused = next;
        if (next != null) {
            next.focused = true;
            next.focusVisible = viaKeyboard;
            resetBlink();
        }
        dirty.set(true);
    }

    /** Tab / Shift+Tab: next or previous focusable widget, wrapping around. */
    private void moveFocus(int direction) {
        if (screen == null) {
            return;
        }
        List<Widget> order = screen.focusables();
        if (order.isEmpty()) {
            return;
        }
        int current = order.indexOf(focused);
        int next = current < 0
                ? (direction > 0 ? 0 : order.size() - 1)
                : Math.floorMod(current + direction, order.size());
        Widget target = order.get(next);
        if (target instanceof Field field) {
            field.selectAllForTab();
        }
        focus(target, true);
    }

    private void resetBlink() {
        if (focused != null) {
            focused.caretOn = true;
        }
        nextBlink = glfwGetTime() + BLINK_SECONDS;
    }

    private void countClicks() {
        double now = glfwGetTime();
        boolean quick = now - lastClickTime < MULTI_CLICK_SECONDS;
        boolean near = Math.abs(cursorX - lastClickX) <= MULTI_CLICK_DISTANCE && Math.abs(cursorY - lastClickY) <= MULTI_CLICK_DISTANCE;
        clickCount = quick && near ? clickCount + 1 : 1;
        lastClickTime = now;
        lastClickX = cursorX;
        lastClickY = cursorY;
    }

    // ---- Host ----

    @Override
    public String clipboard() {
        return glfwGetClipboardString(handle);
    }

    @Override
    public void clipboard(String text) {
        glfwSetClipboardString(handle, text);
    }

    @Override
    public void safely(Widget source, Runnable handler) {
        try {
            handler.run();
        } catch (RuntimeException e) {
            // A broken handler shouldn't take the whole app down.
            System.err.println("unweird: a handler on " + source.getClass().getSimpleName() + " threw:");
            e.printStackTrace();
        }
    }

    private void centerOnPrimaryMonitor() {
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode mode = monitor == NULL ? null : glfwGetVideoMode(monitor);
        if (mode == null) {
            return;
        }
        int[] w = new int[1], h = new int[1];
        glfwGetWindowSize(handle, w, h);
        glfwSetWindowPos(handle, (mode.width() - w[0]) / 2, (mode.height() - h[0]) / 2);
    }

    private static ByteBuffer loadResource(String path) {
        try (InputStream in = Window.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("unweird: missing resource " + path);
            }
            byte[] bytes = in.readAllBytes();
            return MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
