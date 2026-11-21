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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
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
    private final java.util.EnumMap<Cursor, Long> cursors = new java.util.EnumMap<>(Cursor.class);
    private final ByteBuffer regularFontData;
    private final ByteBuffer semiboldFontData;
    private final Canvas canvas;
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final Queue<Runnable> uiTasks = new ConcurrentLinkedQueue<>();
    private final Thread uiThread = Thread.currentThread();

    private Theme theme;
    private Screen screen;
    private Widget hovered;
    private Widget pressed;
    private Widget focused;
    /** Open overlays, bottom to top. The top one gets input. */
    private final List<Overlay> overlays = new java.util.ArrayList<>();
    private int pressedButton = -1;
    private double lastDragX;
    private double lastDragY;
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
        while (!overlays.isEmpty()) {
            closeOverlay(overlays.getFirst());
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

    /** Runs {@code task} on the UI thread: right away if we're on it, otherwise as soon as the loop wakes up. */
    void runOnUiThread(Runnable task) {
        if (Thread.currentThread() == uiThread) {
            task.run();
        } else {
            uiTasks.add(task);
            requestRender();
        }
    }

    /** Runs until the window is closed. Blocks the calling (UI) thread. */
    void run() {
        glfwShowWindow(handle);
        while (!glfwWindowShouldClose(handle)) {
            for (Runnable task; (task = uiTasks.poll()) != null; ) {
                safely(null, task);
            }
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
        windowToLogical = framebufferPerWindow / scale;

        Color bg = theme.getBackground();
        glViewport(0, 0, fbWidth[0], fbHeight[0]);
        glClearColor(bg.r(), bg.g(), bg.b(), 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

        nvgBeginFrame(vg, fbWidth[0] / scale, fbHeight[0] / scale, scale);
        canvas.setTheme(theme);
        float logicalWidth = fbWidth[0] / scale;
        float logicalHeight = fbHeight[0] / scale;
        if (screen != null) {
            screen.render(canvas, logicalWidth, logicalHeight);
        }
        for (Overlay overlay : List.copyOf(overlays)) {
            overlay.update();
            overlay.position(canvas, logicalWidth, logicalHeight);
            overlay.draw(canvas);
        }
        nvgEndFrame(vg);
        glfwSwapBuffers(handle);

        if (focused != null && !focused.interactive()) {
            focus(null, false); // it was disabled or hidden by the last change
        }
        updateHover(); // the layout may have moved something under the cursor
    }

    private void installCallbacks() {
        glfwSetCursorPosCallback(handle, (window, x, y) -> {
            cursorX = x;
            cursorY = y;
            if (pressed != null) {
                Widget dragging = pressed;
                float dx = (float) (x - lastDragX) * windowToLogical;
                float dy = (float) (y - lastDragY) * windowToLogical;
                lastDragX = x;
                lastDragY = y;
                safely(dragging, () -> dragging.pointerDrag(pointer(buttonOf(pressedButton), 0, dx, dy)));
                resetBlink();
                dirty.set(true);
            }
            updateHover();
            Widget over = hovered;
            if (over != null && pressed == null) {
                boolean[] changed = {false};
                safely(over, () -> changed[0] = over.pointerMove(pointer(Pointer.Button.NONE, 0, 0, 0)));
                if (changed[0]) {
                    dirty.set(true);
                }
            }
        });
        glfwSetCursorEnterCallback(handle, (window, entered) -> {
            if (entered) {
                syncCursor();
            } else {
                cursorX = -1;
                cursorY = -1;
            }
            updateHover();
        });
        glfwSetMouseButtonCallback(handle, (window, button, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (pressed != null) {
                    return; // one button at a time
                }
                syncCursor(); // a press can arrive before any move event (e.g. right after entering the window)
                updateHover();
                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    countClicks();
                }
                Overlay top = topOverlay();
                if (top != null && !top.modal() && !top.contains(logical(cursorX), logical(cursorY))) {
                    closeOverlay(top); // a click outside a popup only closes it
                    return;
                }
                if (hovered == null) {
                    if (top == null || !top.modal()) {
                        focus(null, false); // clicking empty space drops focus
                    }
                    return;
                }
                pressed = hovered;
                pressedButton = button;
                lastDragX = cursorX;
                lastDragY = cursorY;
                pressed.pressed = button == GLFW_MOUSE_BUTTON_LEFT;
                if (pressed.focusable()) {
                    focus(pressed, false);
                }
                Widget target = pressed;
                int clicks = button == GLFW_MOUSE_BUTTON_LEFT ? clickCount : 1;
                safely(target, () -> target.pointerDown(pointer(buttonOf(button), clicks, 0, 0)));
                dirty.set(true);
            } else if (action == GLFW_RELEASE && pressed != null && button == pressedButton) {
                Widget released = pressed;
                pressed = null;
                pressedButton = -1;
                released.pressed = false;
                safely(released, () -> released.pointerUp(pointer(buttonOf(button), 0, 0, 0)));
                if (button == GLFW_MOUSE_BUTTON_LEFT && released == hovered && released.clickable()) {
                    safely(released, () -> released.activate(this));
                }
                updateHover();
                dirty.set(true);
            }
        });
        glfwSetKeyCallback(handle, (window, key, scancode, action, mods) -> {
            if (action == GLFW_RELEASE) {
                return;
            }
            resetBlink();
            int code = layoutKey(key, scancode);
            Overlay top = topOverlay();
            if (code == GLFW_KEY_TAB) {
                moveFocus((mods & GLFW_MOD_SHIFT) != 0 ? -1 : 1);
            } else {
                boolean[] used = {false};
                Widget target = focused;
                if (target != null) {
                    safely(target, () -> used[0] = target.keyPressed(code, mods, this));
                }
                if (!used[0] && top != null) {
                    if (code == GLFW_KEY_ESCAPE) {
                        safely(top, top::escape);
                        if (!top.modal()) {
                            closeOverlay(top);
                        }
                        used[0] = true;
                    } else if (top.modal()) {
                        safely(top, () -> used[0] = top.keyPressed(code, mods, this));
                    }
                }
                if (!used[0] && (top == null || !top.modal())) {
                    shortcuts.accept(new KeyPress(code, mods, action == GLFW_REPEAT), focused);
                }
            }
            dirty.set(true); // the key may have changed state that lambdas read
        });
        glfwSetCharCallback(handle, (window, codePoint) -> {
            resetBlink();
            Widget target = focused;
            if (target != null) {
                safely(target, () -> target.typed(codePoint, this));
            }
            dirty.set(true);
        });
        glfwSetScrollCallback(handle, (window, dx, dy) -> {
            Pointer at = pointer(Pointer.Button.NONE, 0, 0, 0);
            for (Widget w = hovered; w != null; w = w.parent) { // the widget under the mouse first, then its parents
                Widget candidate = w;
                boolean[] used = {false};
                safely(candidate, () -> used[0] = candidate.scrolled(at, dx, dy));
                if (used[0]) {
                    dirty.set(true);
                    return;
                }
            }
            Overlay top = topOverlay();
            if (top != null) {
                if (top.contains(at.x(), at.y()) && top.scrolled(at, dx, dy)) {
                    dirty.set(true);
                }
                return; // the page doesn't scroll away under an open popup or dialog
            }
            if (screen != null && screen.scrollBy(at.x(), at.y(), dy)) {
                dirty.set(true);
            }
        });
        glfwSetFramebufferSizeCallback(handle, (window, w, h) -> dirty.set(true));
        glfwSetWindowContentScaleCallback(handle, (window, sx, sy) -> dirty.set(true));
        glfwSetWindowRefreshCallback(handle, window -> render());
    }

    /** Called for keys nobody used: screen, component and app-wide shortcuts. Set by {@link Unweird}. */
    java.util.function.BiConsumer<KeyPress, Widget> shortcuts = (key, focus) -> { };

    private void updateHover() {
        Widget target = null;
        if (cursorX >= 0 && cursorY >= 0) {
            float x = logical(cursorX);
            float y = logical(cursorY);
            Overlay top = topOverlay();
            if (top != null) {
                target = top.contains(x, y) || top.modal() ? top.hitTest(x, y) : null;
            } else if (screen != null) {
                target = screen.hitTest(x, y);
            }
        }
        if (target != hovered) {
            if (hovered != null) {
                hovered.hovered = false;
            }
            hovered = target;
            if (target != null) {
                target.hovered = true;
            }
            dirty.set(true);
        }
        // while dragging (e.g. a text selection), keep the pressed widget's cursor even outside it
        Widget shapeSource = pressed != null ? pressed : hovered;
        glfwSetCursor(handle, shapeSource == null ? NULL : cursorHandle(shapeSource.cursor()));
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

    private Pointer pointer(Pointer.Button button, int clicks, float dx, float dy) {
        boolean shift = glfwGetKey(handle, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS || glfwGetKey(handle, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS;
        boolean ctrl = glfwGetKey(handle, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS || glfwGetKey(handle, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS;
        boolean alt = glfwGetKey(handle, GLFW_KEY_LEFT_ALT) == GLFW_PRESS || glfwGetKey(handle, GLFW_KEY_RIGHT_ALT) == GLFW_PRESS;
        return new Pointer(logical(cursorX), logical(cursorY), button, clicks, shift, ctrl, alt, dx, dy);
    }

    private static Pointer.Button buttonOf(int glfwButton) {
        return switch (glfwButton) {
            case GLFW_MOUSE_BUTTON_LEFT -> Pointer.Button.LEFT;
            case GLFW_MOUSE_BUTTON_RIGHT -> Pointer.Button.RIGHT;
            case GLFW_MOUSE_BUTTON_MIDDLE -> Pointer.Button.MIDDLE;
            default -> Pointer.Button.NONE;
        };
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

    private Overlay topOverlay() {
        return overlays.isEmpty() ? null : overlays.getLast();
    }

    /** The topmost modal overlay (a dialog), or null. Input below it is blocked. */
    private Overlay modalOverlay() {
        for (int i = overlays.size() - 1; i >= 0; i--) {
            if (overlays.get(i).modal()) {
                return overlays.get(i);
            }
        }
        return null;
    }

    /** Reads the real cursor position, in case no move event told us yet. */
    private void syncCursor() {
        double[] x = new double[1];
        double[] y = new double[1];
        glfwGetCursorPos(handle, x, y);
        cursorX = x[0];
        cursorY = y[0];
    }

    private float logical(double windowCoordinate) {
        return (float) windowCoordinate * windowToLogical;
    }

    private void focus(Widget next, boolean viaKeyboard) {
        Overlay top = topOverlay();
        if (top != null && !top.modal() && top.owner() != next) {
            closeOverlay(top); // focus moving away closes a popup
        }
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
        Overlay modal = modalOverlay();
        List<Widget> order = modal != null ? modal.focusables() : screen.tabOrder(); // a dialog keeps Tab inside it
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
        if (modal == null) {
            screen.reveal(target);
        }
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
    public void openOverlay(Overlay next) {
        // a new popup replaces open popups above the topmost dialog; dialogs stack
        Overlay top;
        while ((top = topOverlay()) != null && !top.modal() && !next.modal()) {
            closeOverlay(top);
        }
        overlays.add(next);
        if (next.modal()) {
            Widget first = next.focusables().stream().findFirst().orElse(null);
            focus(first, false);
        }
        updateHover();
        dirty.set(true);
    }

    @Override
    public void closeOverlay(Overlay closing) {
        int index = overlays.indexOf(closing);
        if (index < 0) {
            return;
        }
        while (overlays.size() > index) { // anything opened on top of it goes too
            Overlay removed = overlays.removeLast();
            if (isInside(hovered, removed)) {
                hovered.hovered = false;
                hovered = null;
            }
            if (isInside(pressed, removed)) {
                pressed.pressed = false;
                pressed = null;
            }
            if (isInside(focused, removed)) {
                focused.focused = false;
                focused.focusVisible = false;
                focused = null;
            }
            removed.closed();
        }
        Overlay top = topOverlay();
        if (focused == null && top != null && top.modal()) {
            focus(top.focusables().stream().findFirst().orElse(null), false);
        }
        dirty.set(true);
    }

    private static boolean isInside(Widget widget, Overlay overlay) {
        for (Widget w = widget; w != null; w = w.parent) {
            if (w == overlay) {
                return true;
            }
        }
        return widget != null && overlay.owns(widget);
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
    public void safely(Widget source, Runnable handler) {
        try {
            handler.run();
        } catch (RuntimeException e) {
            // A broken handler shouldn't take the whole app down.
            System.err.println(source == null ? "unweird: a task on the UI thread threw:"
                    : "unweird: a handler on " + source.getClass().getSimpleName() + " threw:");
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
