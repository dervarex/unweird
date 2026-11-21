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
 * when something changed.
 */
final class Window {

    private static final int DEFAULT_WIDTH = 960;
    private static final int DEFAULT_HEIGHT = 640;
    private static final String FONT_DIR = "/com/dervarex/unweird/fonts/";

    private final long handle;
    private final long vg;
    private final long handCursor;
    private final ByteBuffer regularFontData;
    private final ByteBuffer semiboldFontData;
    private final Canvas canvas;
    private final AtomicBoolean dirty = new AtomicBoolean(true);

    private Theme theme;
    private Screen screen;
    private Widget hovered;
    private Widget pressed;

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

        installCallbacks();
    }

    void show(Screen next) {
        if (hovered != null) {
            hovered.hovered = false;
            hovered = null;
        }
        screen = next;
        Title title = next.getClass().getAnnotation(Title.class);
        glfwSetWindowTitle(handle, title != null ? title.value() : "unweird");
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
            if (action == GLFW_PRESS && hovered != null) {
                pressed = hovered;
                pressed.pressed = true;
                dirty.set(true);
            } else if (action == GLFW_RELEASE && pressed != null) {
                Widget released = pressed;
                pressed = null;
                released.pressed = false;
                if (released == hovered) {
                    click(released);
                }
                dirty.set(true);
            }
        });
        glfwSetFramebufferSizeCallback(handle, (window, w, h) -> dirty.set(true));
        glfwSetWindowContentScaleCallback(handle, (window, sx, sy) -> dirty.set(true));
        glfwSetWindowRefreshCallback(handle, window -> render());
    }

    private void updateHover() {
        Widget target = null;
        if (screen != null && cursorX >= 0 && cursorY >= 0) {
            target = screen.hitTest((float) cursorX * windowToLogical, (float) cursorY * windowToLogical);
        }
        if (target != hovered) {
            if (hovered != null) {
                hovered.hovered = false;
            }
            hovered = target;
            if (target != null) {
                target.hovered = true;
            }
            glfwSetCursor(handle, target != null ? handCursor : NULL);
            dirty.set(true);
        }
    }

    private static void click(Widget widget) {
        try {
            widget.click();
        } catch (RuntimeException e) {
            // A broken handler shouldn't take the whole app down.
            System.err.println("unweird: a click handler on " + widget.getClass().getSimpleName() + " threw:");
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
