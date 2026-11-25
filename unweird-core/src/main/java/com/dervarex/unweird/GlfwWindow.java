package com.dervarex.unweird;

import com.dervarex.unweird.style.Theme;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * The desktop backend: a GLFW window with an OpenGL 3.3 context, feeding input into {@link Window} and running its loop.
 *
 * <p>Rendering is event-driven: the loop sleeps in {@code glfwWaitEvents} and only draws
 * when something changed (or a focused field's caret blinks).
 */
final class GlfwWindow implements Platform {

    private final long handle;
    private final java.util.EnumMap<Cursor, Long> cursors = new java.util.EnumMap<>(Cursor.class);
    private final Window window;
    private final App app;
    private volatile int lastErrorCode;
    private volatile String lastError = "no details";
    private final Path boundsFile;
    /** Last position/size while not maximized, so a maximized window still remembers its normal bounds. */
    private int normalX;
    private int normalY;
    private int normalWidth;
    private int normalHeight;

    GlfwWindow(Theme theme, App app) {
        this.app = app;
        this.boundsFile = app.remember ? WindowBounds.file(app.name) : null;

        // remember GLFW's last error, so failures can be explained instead of just reported
        glfwSetErrorCallback((code, description) -> {
            lastErrorCode = code;
            lastError = GLFWErrorCallback.getDescription(description);
            System.err.println("unweird: GLFW: " + lastError);
        });
        if (!glfwInit()) {
            GLFWErrorCallback callback = glfwSetErrorCallback(null);
            if (callback != null) {
                callback.free();
            }
            throw new IllegalStateException(explainFailure(lastErrorCode, lastError, false));
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

        WindowBounds saved = boundsFile == null ? null : WindowBounds.load(boundsFile);
        int width = saved != null ? saved.width() : app.width;
        int height = saved != null ? saved.height() : app.height;
        handle = glfwCreateWindow(width, height, app.name, NULL, NULL);
        if (handle == NULL) {
            String message = explainFailure(lastErrorCode, lastError, true);
            glfwTerminate();
            GLFWErrorCallback callback = glfwSetErrorCallback(null);
            if (callback != null) {
                callback.free();
            }
            throw new IllegalStateException(message);
        }
        if (app.minWidth > 0 || app.minHeight > 0) {
            glfwSetWindowSizeLimits(handle, app.minWidth > 0 ? app.minWidth : GLFW_DONT_CARE,
                    app.minHeight > 0 ? app.minHeight : GLFW_DONT_CARE, GLFW_DONT_CARE, GLFW_DONT_CARE);
        }
        boolean wayland = glfwGetPlatform() == GLFW_PLATFORM_WAYLAND; // no window positions or icons there
        if (!wayland && saved != null && onSomeMonitor(saved)) {
            glfwSetWindowPos(handle, saved.x(), saved.y());
        } else if (!wayland) {
            centerOnPrimaryMonitor();
        }
        if (app.icon != null && !wayland) {
            setIcon(app.icon);
        }
        boolean startMaximized = saved != null ? saved.maximized() : app.maximized;
        if (startMaximized) {
            glfwMaximizeWindow(handle);
        }
        rememberNormalBounds();

        glfwMakeContextCurrent(handle);
        GL.createCapabilities();
        glfwSwapInterval(1);

        window = new Window(theme, app, this, new LwjglVg());
        installCallbacks();
    }

    Window window() {
        return window;
    }

    private double lastFrameTime;
    private static final double MIN_FRAME_SECONDS = 1.0 / 120; // cap for animations where vsync doesn't throttle

    /** Runs until the window is closed. Blocks the calling (UI) thread. */
    void run() {
        glfwShowWindow(handle);
        if (boundsFile != null) {
            glfwSetWindowPosCallback(handle, (w, x, y) -> rememberNormalBounds());
            glfwSetWindowSizeCallback(handle, (w, width, height) -> rememberNormalBounds());
        }
        while (!glfwWindowShouldClose(handle)) {
            window.runPending();
            if (window.takeDirty()) {
                double sinceLast = glfwGetTime() - lastFrameTime;
                if (window.animating() && sinceLast < MIN_FRAME_SECONDS) {
                    window.markDirty();
                    glfwWaitEventsTimeout(MIN_FRAME_SECONDS - sinceLast); // still handle input while animating
                    continue;
                }
                // Handle input before every frame. With vsync each frame takes ~16 ms, so an animating screen would
                // otherwise render back to back and never get to the input (clicks and drags did nothing).
                glfwPollEvents();
                lastFrameTime = glfwGetTime();
                render();
                if (window.animating()) {
                    window.markDirty(); // someone is animating
                }
                continue;
            }
            double wake = window.nextWake();
            if (wake == Double.MAX_VALUE) {
                glfwWaitEvents();
            } else {
                double wait = wake - glfwGetTime();
                if (wait > 0) {
                    glfwWaitEventsTimeout(wait);
                }
            }
        }
    }

    /** Saves the window bounds and frees everything. */
    void destroy() {
        if (boundsFile != null) {
            boolean maximized = glfwGetWindowAttrib(handle, GLFW_MAXIMIZED) == GLFW_TRUE;
            rememberNormalBounds();
            if (normalWidth > 0 && normalHeight > 0) {
                new WindowBounds(normalX, normalY, normalWidth, normalHeight, maximized).save(boundsFile);
            }
        }
        org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(handle);
        window.free();
        cursors.values().forEach(cursor -> {
            if (cursor != NULL) {
                glfwDestroyCursor(cursor);
            }
        });
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
        window.render(fbWidth[0], fbHeight[0], scale, framebufferPerWindow / scale);
        glfwSwapBuffers(handle);
    }

    private void installCallbacks() {
        glfwSetCursorPosCallback(handle, (w, x, y) -> window.pointerMoved(x, y));
        glfwSetCursorEnterCallback(handle, (w, entered) -> {
            if (entered) {
                double[] at = cursorPosition();
                window.pointerMoved(at[0], at[1]);
            } else {
                window.pointerLeft();
            }
        });
        glfwSetMouseButtonCallback(handle, (w, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                syncCursor(); // a press can arrive before any move event (e.g. right after entering the window)
                window.pointerButton(button, true);
            } else if (action == GLFW_RELEASE) {
                window.pointerButton(button, false);
            }
        });
        glfwSetKeyCallback(handle, (w, key, scancode, action, mods) -> {
            if (action != GLFW_RELEASE) {
                window.key(layoutKey(key, scancode), mods, action == GLFW_REPEAT);
            }
        });
        glfwSetCharCallback(handle, (w, codePoint) -> window.typed(codePoint));
        glfwSetScrollCallback(handle, (w, dx, dy) -> window.scroll(dx, dy));
        glfwSetDropCallback(handle, (w, count, names) -> {
            List<Path> files = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) {
                try {
                    files.add(Path.of(org.lwjgl.glfw.GLFWDropCallback.getName(names, i)));
                } catch (java.nio.file.InvalidPathException ignored) {
                    // not a local file
                }
            }
            syncCursor();
            window.dropped(files);
        });
        glfwSetFramebufferSizeCallback(handle, (w, width, height) -> window.markDirty());
        glfwSetWindowContentScaleCallback(handle, (w, sx, sy) -> window.markDirty());
        glfwSetWindowRefreshCallback(handle, w -> render());
    }

    /** Letter keys as the keyboard layout labels them (Ctrl+Z is the key with Z on it, also on QWERTZ). */
    private static int layoutKey(int key, int scancode) {
        if (key >= GLFW_KEY_A && key <= GLFW_KEY_Z) {
            String name = glfwGetKeyName(key, scancode);
            if (name != null && name.length() == 1) {
                char c = Character.toLowerCase(name.charAt(0));
                if (c >= 'a' && c <= 'z') {
                    return GLFW_KEY_A + (c - 'a');
                }
            }
        }
        return key;
    }

    private double[] cursorPosition() {
        double[] x = new double[1];
        double[] y = new double[1];
        glfwGetCursorPos(handle, x, y);
        return new double[]{x[0], y[0]};
    }

    /** Reads the real cursor position, in case no move event told us yet. */
    private void syncCursor() {
        double[] at = cursorPosition();
        window.pointerAt(at[0], at[1]);
    }

    // ---- Platform ----

    @Override
    public double time() {
        return glfwGetTime();
    }

    @Override
    public void wake() {
        glfwPostEmptyEvent();
    }

    @Override
    public void close() {
        glfwSetWindowShouldClose(handle, true);
        glfwPostEmptyEvent();
    }

    @Override
    public String clipboard() {
        return glfwGetClipboardString(handle);
    }

    @Override
    public void clipboard(String text) {
        glfwSetClipboardString(handle, text);
    }

    @Override
    public void cursor(Cursor cursor) {
        glfwSetCursor(handle, cursor == null ? NULL : cursorHandle(cursor));
    }

    @Override
    public void title(String title) {
        glfwSetWindowTitle(handle, title);
    }

    @Override
    public int modifiers() {
        int mods = 0;
        if (held(GLFW_KEY_LEFT_SHIFT) || held(GLFW_KEY_RIGHT_SHIFT)) {
            mods |= GLFW_MOD_SHIFT;
        }
        if (held(GLFW_KEY_LEFT_CONTROL) || held(GLFW_KEY_RIGHT_CONTROL)) {
            mods |= GLFW_MOD_CONTROL;
        }
        if (held(GLFW_KEY_LEFT_ALT) || held(GLFW_KEY_RIGHT_ALT)) {
            mods |= GLFW_MOD_ALT;
        }
        if (held(GLFW_KEY_LEFT_SUPER) || held(GLFW_KEY_RIGHT_SUPER)) {
            mods |= GLFW_MOD_SUPER;
        }
        return mods;
    }

    private boolean held(int key) {
        return glfwGetKey(handle, key) == GLFW_PRESS;
    }

    private long cursorHandle(Cursor cursor) {
        return cursors.computeIfAbsent(cursor, c -> glfwCreateStandardCursor(switch (c) {
            case ARROW -> GLFW_ARROW_CURSOR;
            case HAND -> GLFW_POINTING_HAND_CURSOR;
            case TEXT -> GLFW_IBEAM_CURSOR;
            case CROSSHAIR -> GLFW_CROSSHAIR_CURSOR;
            case MOVE -> GLFW_RESIZE_ALL_CURSOR;
            case RESIZE_HORIZONTAL -> GLFW_RESIZE_EW_CURSOR;
            case RESIZE_VERTICAL -> GLFW_RESIZE_NS_CURSOR;
            case NOT_ALLOWED -> GLFW_NOT_ALLOWED_CURSOR;
        }));
    }

    /** Turns GLFW's last error into a message that says what's wrong and what to do. */
    static String explainFailure(int code, String description, boolean creatingWindow) {
        String details = " (GLFW: " + description + ")";
        if (!creatingWindow) {
            return "unweird: there's no display to open a window on" + details
                    + ". Is DISPLAY (X11) or WAYLAND_DISPLAY set? Over SSH or in a container there may be none.";
        }
        String lower = description.toLowerCase(java.util.Locale.ROOT);
        boolean noLibrary = code == GLFW_API_UNAVAILABLE || lower.contains("failed to load") || lower.contains("glx") && lower.contains("load");
        if (noLibrary) {
            return "unweird: couldn't load OpenGL" + details + ". The graphics driver's libGL wasn't found. "
                    + "On NixOS this happens with a JDK from the Nix store: use a regular JDK (it runs through nix-ld), "
                    + "start with LD_LIBRARY_PATH=/run/current-system/sw/share/nix-ld/lib (if nix-ld is enabled), "
                    + "or use a dev shell that provides libGL.";
        }
        if (code == GLFW_VERSION_UNAVAILABLE) {
            return "unweird: the graphics driver doesn't offer OpenGL 3.3" + details
                    + ". Update the driver; in a virtual machine, turn on 3D acceleration.";
        }
        return "unweird: couldn't create a window" + details;
    }

    private void rememberNormalBounds() {
        if (glfwGetWindowAttrib(handle, GLFW_MAXIMIZED) == GLFW_TRUE || glfwGetWindowAttrib(handle, GLFW_ICONIFIED) == GLFW_TRUE) {
            return;
        }
        int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
        glfwGetWindowPos(handle, x, y);
        glfwGetWindowSize(handle, w, h);
        normalX = x[0];
        normalY = y[0];
        normalWidth = w[0];
        normalHeight = h[0];
    }

    /** Whether the saved window's top-left area is on a monitor that's connected now. */
    private static boolean onSomeMonitor(WindowBounds bounds) {
        var monitors = glfwGetMonitors();
        if (monitors == null) {
            return false;
        }
        for (int i = 0; i < monitors.limit(); i++) {
            int[] x = new int[1], y = new int[1], w = new int[1], h = new int[1];
            glfwGetMonitorWorkarea(monitors.get(i), x, y, w, h);
            int probeX = bounds.x() + 40;
            int probeY = bounds.y() + 20;
            if (probeX >= x[0] && probeY >= y[0] && probeX < x[0] + w[0] && probeY < y[0] + h[0]) {
                return true;
            }
        }
        return false;
    }

    private void setIcon(String resource) {
        try (InputStream in = GlfwWindow.class.getResourceAsStream(resource.startsWith("/") ? resource : "/" + resource)) {
            java.awt.image.BufferedImage image = in == null ? null : javax.imageio.ImageIO.read(in);
            if (image == null) {
                System.err.println("unweird: can't load window icon " + resource);
                return;
            }
            int w = image.getWidth();
            int h = image.getHeight();
            int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
            ByteBuffer pixels = MemoryUtil.memAlloc(w * h * 4);
            try (org.lwjgl.glfw.GLFWImage.Buffer images = org.lwjgl.glfw.GLFWImage.malloc(1)) {
                for (int pixel : argb) {
                    pixels.put((byte) (pixel >> 16)).put((byte) (pixel >> 8)).put((byte) pixel).put((byte) (pixel >>> 24));
                }
                pixels.flip();
                images.get(0).set(w, h, pixels);
                glfwSetWindowIcon(handle, images);
            } finally {
                MemoryUtil.memFree(pixels);
            }
        } catch (IOException e) {
            System.err.println("unweird: can't load window icon " + resource + ": " + e.getMessage());
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
}
