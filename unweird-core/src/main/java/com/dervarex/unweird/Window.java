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
import java.nio.file.Path;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.nanovg.NanoVG.nvgAddFallbackFontId;
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

    private static final String FONT_DIR = "/com/dervarex/unweird/fonts/";
    private static final double BLINK_SECONDS = 0.53;
    private static final double MULTI_CLICK_SECONDS = 0.4;
    private static final double MULTI_CLICK_DISTANCE = 4;

    private final long handle;
    private final long vg;
    private final java.util.EnumMap<Cursor, Long> cursors = new java.util.EnumMap<>(Cursor.class);
    private final ByteBuffer regularFontData;
    private final ByteBuffer semiboldFontData;
    private final List<ByteBuffer> fallbackData = new java.util.ArrayList<>();
    private final Canvas canvas;
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final Queue<Runnable> uiTasks = new ConcurrentLinkedQueue<>();
    /** Timers by due time (glfwGetTime seconds). Guarded by itself. */
    private final java.util.PriorityQueue<Timer> timers = new java.util.PriorityQueue<>();
    private long timerSequence;
    /** Visible toasts, oldest first. UI thread only. */
    private final List<ShownToast> toasts = new java.util.ArrayList<>();
    private final Thread uiThread = Thread.currentThread();

    private Theme theme;
    private Screen screen;
    private Widget hovered;
    private Widget pressed;
    private Widget focused;
    /** Open overlays, bottom to top. The top one gets input. */
    private final List<Overlay> overlays = new java.util.ArrayList<>();
    private int pressedButton = -1;
    private Widget tooltipFor;
    private boolean tooltipShown;
    private float tooltipX;
    private float tooltipY;
    private Runnable tooltipTimer;
    private static final double TOOLTIP_DELAY = 0.6;
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

    private final App app;
    private final Path boundsFile;
    /** Last position/size while not maximized, so a maximized window still remembers its normal bounds. */
    private int normalX;
    private int normalY;
    private int normalWidth;
    private int normalHeight;

    Window(Theme theme, App app) {
        this.theme = theme;
        this.app = app;
        this.boundsFile = app.remember ? WindowBounds.file(app.name) : null;

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

        WindowBounds saved = boundsFile == null ? null : WindowBounds.load(boundsFile);
        int width = saved != null ? saved.width() : app.width;
        int height = saved != null ? saved.height() : app.height;
        handle = glfwCreateWindow(width, height, app.name, NULL, NULL);
        if (handle == NULL) {
            glfwTerminate();
            throw new IllegalStateException("unweird: could not create a window (OpenGL 3.3 is required)");
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
        // characters Noto Sans does not have: symbols, arrows and math, emoji, then whatever the app adds (CJK, ...)
        List<String> fallbacks = new java.util.ArrayList<>(List.of(FONT_DIR + "NotoSansSymbols2-Regular.ttf", FONT_DIR + "NotoSansMath-Regular.ttf", FONT_DIR + "NotoEmoji.ttf"));
        fallbacks.addAll(app.fallbackFonts);
        for (String resource : fallbacks) {
            ByteBuffer data;
            try {
                data = loadResource(resource.startsWith("/") ? resource : "/" + resource);
            } catch (RuntimeException e) {
                System.err.println("unweird: can't load fallback font " + resource + ": " + e.getMessage());
                continue;
            }
            int font = nvgCreateFontMem(vg, "fallback-" + fallbackData.size(), data, false);
            if (font < 0) {
                MemoryUtil.memFree(data);
                System.err.println("unweird: " + resource + " isn't a font NanoVG can read");
                continue;
            }
            fallbackData.add(data);
            nvgAddFallbackFontId(vg, regular, font);
            nvgAddFallbackFontId(vg, semibold, font);
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
        glfwSetWindowTitle(handle, title == null ? app.name
                : app.name.equals(Unweird.DEFAULT_NAME) || title.value().equals(app.name) ? title.value()
                : title.value() + " — " + app.name);

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

    private boolean frameRequested;
    private double lastFrameTime;
    private static final double MIN_FRAME_SECONDS = 1.0 / 120; // cap for animations where vsync doesn't throttle

    /** UI thread (from draw): render again right after this frame. */
    void requestFrame() {
        frameRequested = true;
    }

    /** Thread-safe: ends the loop after the current iteration. */
    void requestClose() {
        glfwSetWindowShouldClose(handle, true);
        glfwPostEmptyEvent();
    }

    /** Thread-safe: asks the UI thread to re-evaluate and redraw. */
    void requestRender() {
        dirty.set(true);
        glfwPostEmptyEvent();
    }

    /** Runs {@code task} on the UI thread after {@code seconds}. Thread-safe. Returns a handle to cancel it. */
    Runnable schedule(double seconds, Runnable task) {
        Timer timer;
        synchronized (timers) {
            timer = new Timer(glfwGetTime() + Math.max(0, seconds), timerSequence++, task);
            timers.add(timer);
        }
        if (Thread.currentThread() != uiThread) {
            glfwPostEmptyEvent(); // wake the loop so it recomputes how long to sleep
        }
        return () -> {
            synchronized (timers) {
                timers.remove(timer);
            }
        };
    }

    private void runDueTimers() {
        double now = glfwGetTime();
        while (true) {
            Timer due;
            synchronized (timers) {
                Timer next = timers.peek();
                if (next == null || next.due > now) {
                    return;
                }
                due = timers.poll();
            }
            safely(null, due.task);
            dirty.set(true);
        }
    }

    private double nextTimerDue() {
        synchronized (timers) {
            Timer next = timers.peek();
            return next == null ? Double.MAX_VALUE : next.due;
        }
    }

    private record Timer(double due, long sequence, Runnable task) implements Comparable<Timer> {
        @Override
        public int compareTo(Timer other) {
            int byTime = Double.compare(due, other.due);
            return byTime != 0 ? byTime : Long.compare(sequence, other.sequence);
        }
    }

    // ---- toasts ----

    private static final int MAX_TOASTS = 4;
    private static final float TOAST_WIDTH = 340;

    private static final class ShownToast {
        final Toast toast;
        float x;
        float y;
        float width;
        float height;

        ShownToast(Toast toast) {
            this.toast = toast;
        }
    }

    /** UI thread. */
    void toast(Toast toast) {
        ShownToast shown = new ShownToast(toast);
        toasts.add(shown);
        while (toasts.size() > MAX_TOASTS) {
            toasts.removeFirst();
        }
        schedule(toast.duration().toMillis() / 1000.0, () -> toasts.remove(shown));
        dirty.set(true);
    }

    private void drawToasts(float windowWidth, float windowHeight) {
        var theme = canvas.getTheme();
        float y = windowHeight - 20;
        for (int i = toasts.size() - 1; i >= 0; i--) { // newest at the bottom
            ShownToast shown = toasts.get(i);
            float width = Math.min(TOAST_WIDTH, windowWidth - 40);
            float textWidth = width - 44;
            Size text = canvas.measureText(shown.toast.message(), Canvas.Weight.REGULAR, 14, textWidth);
            float height = Math.max(44, text.height() + 24);
            y -= height;
            shown.x = windowWidth - 20 - width;
            shown.y = y;
            shown.width = width;
            shown.height = height;
            var stripe = switch (shown.toast.kind()) {
                case INFO -> theme.getAccent();
                case SUCCESS -> theme.getSuccess();
                case ERROR -> theme.getDanger();
            };
            float radius = theme.getRadius() * 1.25f;
            canvas.shadow(shown.x, shown.y, width, height, radius, 18, theme.getShadow().withAlpha(Math.min(1, theme.getShadow().a() * 2)));
            canvas.fillRect(shown.x, shown.y, width, height, radius, theme.getSurface());
            canvas.strokeRect(shown.x, shown.y, width, height, radius, 1, theme.getBorder());
            canvas.fillRect(shown.x + 12, shown.y + 12, 4, height - 24, 2, stripe);
            canvas.text(shown.toast.message(), shown.x + 28, shown.y + (height - text.height()) / 2, textWidth, Canvas.Weight.REGULAR, 14,
                    theme.getText(), Canvas.Align.LEFT);
            y -= 10;
        }
    }

    /** A click on a toast dismisses it. Returns whether the click hit one. */
    private boolean dismissToastAt(float x, float y) {
        for (ShownToast shown : toasts) {
            if (x >= shown.x && y >= shown.y && x < shown.x + shown.width && y < shown.y + shown.height) {
                toasts.remove(shown);
                dirty.set(true);
                return true;
            }
        }
        return false;
    }

    /** Queues {@code task} for the next loop iteration, even when called on the UI thread. */
    void uiTasksLater(Runnable task) {
        uiTasks.add(task);
        dirty.set(true);
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
        if (boundsFile != null) {
            glfwSetWindowPosCallback(handle, (window, x, y) -> rememberNormalBounds());
            glfwSetWindowSizeCallback(handle, (window, w, h) -> rememberNormalBounds());
        }
        while (!glfwWindowShouldClose(handle)) {
            for (Runnable task; (task = uiTasks.poll()) != null; ) {
                safely(null, task);
            }
            runDueTimers();
            if (focused != null && focused.blinks() && glfwGetTime() >= nextBlink) {
                focused.caretOn = !focused.caretOn;
                nextBlink = glfwGetTime() + BLINK_SECONDS;
                dirty.set(true);
            }
            if (dirty.getAndSet(false)) {
                double sinceLast = glfwGetTime() - lastFrameTime;
                if (frameRequested && sinceLast < MIN_FRAME_SECONDS) {
                    dirty.set(true);
                    glfwWaitEventsTimeout(MIN_FRAME_SECONDS - sinceLast); // still handle input while animating
                    continue;
                }
                frameRequested = false;
                lastFrameTime = glfwGetTime();
                render();
                if (frameRequested) {
                    dirty.set(true); // someone is animating
                }
                continue;
            }
            double wake = nextTimerDue();
            if (focused != null && focused.blinks()) {
                wake = Math.min(wake, nextBlink);
            }
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

    void close() {
        if (boundsFile != null) {
            boolean maximized = glfwGetWindowAttrib(handle, GLFW_MAXIMIZED) == GLFW_TRUE;
            rememberNormalBounds();
            if (normalWidth > 0 && normalHeight > 0) {
                new WindowBounds(normalX, normalY, normalWidth, normalHeight, maximized).save(boundsFile);
            }
        }
        org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(handle);
        canvas.free();
        nvgDelete(vg);
        MemoryUtil.memFree(regularFontData);
        MemoryUtil.memFree(semiboldFontData);
        fallbackData.forEach(MemoryUtil::memFree);
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
        canvas.beginFrame(scale, glfwGetTime(), fbWidth[0], fbHeight[0]);
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
        drawToasts(logicalWidth, logicalHeight);
        drawTooltip(logicalWidth, logicalHeight);
        nvgEndFrame(vg);
        canvas.collectSurfaces();
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
                if (dismissToastAt(logical(cursorX), logical(cursorY))) {
                    return;
                }
                updateHover();
                if (button == GLFW_MOUSE_BUTTON_LEFT) {
                    countClicks();
                }
                Overlay top = topOverlay();
                if (top != null && !top.modal() && !top.contains(logical(cursorX), logical(cursorY))) {
                    closeOverlay(top); // a click outside a popup only closes it
                    return;
                }
                hideTooltip();
                if (hovered == null || !hovered.interactive()) {
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
            hideTooltip();
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
            hideTooltip();
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

    /** Focus asked for from code ({@link Widget#focus()}): only widgets that can take it and are reachable now. */
    void requestFocus(Widget widget) {
        Overlay modal = modalOverlay();
        List<Widget> reachable = modal != null ? modal.focusables() : screen == null ? List.of() : screen.tabOrder();
        if (reachable.contains(widget)) {
            focus(widget, true);
            if (modal == null) {
                screen.reveal(widget);
            }
        }
    }

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
            armTooltip(target);
            dirty.set(true);
        }
        // while dragging (e.g. a text selection), keep the pressed widget's cursor even outside it
        Widget shapeSource = pressed != null ? pressed : hovered;
        glfwSetCursor(handle, shapeSource == null ? NULL : cursorHandle(shapeSource.cursor()));
    }

    private void armTooltip(Widget target) {
        hideTooltip();
        if (target == null || target.tooltipText() == null || pressed != null) {
            return;
        }
        tooltipFor = target;
        tooltipTimer = schedule(TOOLTIP_DELAY, () -> {
            if (hovered == target && pressed == null && target.tooltipText() != null) {
                tooltipShown = true;
                tooltipX = logical(cursorX);
                tooltipY = logical(cursorY);
            }
        });
    }

    private void hideTooltip() {
        if (tooltipTimer != null) {
            tooltipTimer.run();
            tooltipTimer = null;
        }
        if (tooltipShown) {
            dirty.set(true);
        }
        tooltipShown = false;
        tooltipFor = null;
    }

    private void drawTooltip(float windowWidth, float windowHeight) {
        Widget target = tooltipFor;
        if (!tooltipShown || target == null || target != hovered) {
            return;
        }
        String text = target.tooltipText();
        if (text == null) {
            return;
        }
        var theme = canvas.getTheme();
        Size size = canvas.measureText(text, Canvas.Weight.REGULAR, 13, 280);
        float width = size.width() + 20;
        float height = size.height() + 12;
        float x = Math.max(6, Math.min(tooltipX + 12, windowWidth - width - 6));
        float y = tooltipY + 22;
        if (y + height > windowHeight - 6) {
            y = tooltipY - height - 10; // no room below the cursor: show it above
        }
        canvas.fillRect(x, y, width, height, 6, theme.getText().withAlpha(0.92f));
        canvas.text(text, x + 10, y + 6, size.width(), Canvas.Weight.REGULAR, 13, theme.getBackground(), Canvas.Align.LEFT);
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
            focus(next.initialFocus(), false);
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
        try (InputStream in = Window.class.getResourceAsStream(resource.startsWith("/") ? resource : "/" + resource)) {
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
