package com.dervarex.unweird;

import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.style.Color;
import com.dervarex.unweird.style.Theme;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The app's one window, independent of the system it runs on: screens, overlays, focus, hover, input routing,
 * timers, toasts and tooltips. A {@link Platform} backend ({@link GlfwWindow} on desktop, an Activity on Android)
 * feeds it input in window coordinates, runs its loop and supplies a {@link Vg} to draw with.
 * Everything here runs on the UI thread, except {@link #requestRender()} and the other methods marked thread-safe.
 */
final class Window implements Host {

    private static final String FONT_DIR = "/com/dervarex/unweird/fonts/";
    private static final double BLINK_SECONDS = 0.53;
    private static final double MULTI_CLICK_SECONDS = 0.4;
    private static final double MULTI_CLICK_DISTANCE = 4;

    /** Mouse buttons, as GLFW numbers them. */
    static final int BUTTON_LEFT = 0;
    static final int BUTTON_RIGHT = 1;
    static final int BUTTON_MIDDLE = 2;

    /** Key codes are GLFW's; other platforms translate theirs. */
    static final int KEY_TAB = 258;
    static final int KEY_ESCAPE = 256;
    static final int MOD_SHIFT = 0x1;

    private final Platform platform;
    private final App app;
    private Canvas canvas;
    private final AtomicBoolean dirty = new AtomicBoolean(true);
    private final Queue<Runnable> uiTasks = new ConcurrentLinkedQueue<>();
    /** Timers by due time ({@link Platform#time()} seconds). Guarded by itself. */
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
    /** Parts of the window covered by the system (status bar, on-screen keyboard...), in logical pixels. */
    private float insetLeft;
    private float insetTop;
    private float insetRight;
    private float insetBottom;

    Window(Theme theme, App app, Platform platform, Vg vg) {
        this.theme = theme;
        this.app = app;
        this.platform = platform;
        this.canvas = createCanvas(vg);
    }

    /** Loads the fonts into {@code vg} and makes a canvas drawing with it. */
    private Canvas createCanvas(Vg vg) {
        int regular = vg.createFont("regular", loadResource(FONT_DIR + "NotoSans-Regular.ttf"));
        int semibold = vg.createFont("semibold", loadResource(FONT_DIR + "NotoSans-SemiBold.ttf"));
        if (regular < 0 || semibold < 0) {
            throw new IllegalStateException("unweird: could not load the bundled fonts");
        }
        // characters Noto Sans does not have: symbols, arrows and math, emoji, then whatever the app adds (CJK, ...)
        List<String> fallbacks = new java.util.ArrayList<>(List.of(FONT_DIR + "NotoSansSymbols2-Regular.ttf", FONT_DIR + "NotoSansMath-Regular.ttf", FONT_DIR + "NotoEmoji.ttf"));
        fallbacks.addAll(app.fallbackFonts);
        int count = 0;
        for (String resource : fallbacks) {
            byte[] data;
            try {
                data = loadResource(resource.startsWith("/") ? resource : "/" + resource);
            } catch (RuntimeException e) {
                System.err.println("unweird: can't load fallback font " + resource + ": " + e.getMessage());
                continue;
            }
            int font = vg.createFont("fallback-" + count++, data);
            if (font < 0) {
                System.err.println("unweird: " + resource + " isn't a font NanoVG can read");
                continue;
            }
            vg.addFallbackFont(regular, font);
            vg.addFallbackFont(semibold, font);
        }
        return new Canvas(vg, regular, semibold, theme);
    }

    /**
     * The drawing context was lost and {@code vg} replaces it (Android does this when the app goes to the background).
     * Images and GL surfaces are loaded again on first use.
     */
    void replaceVg(Vg vg) {
        canvas = createCanvas(vg);
        dirty.set(true);
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
            closeOverlay(overlays.get(0));
        }
        focus(null, false);
        screen = next;
        Title title = next.getClass().getAnnotation(Title.class);
        platform.title(title == null ? app.name
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

    Theme theme() {
        return theme;
    }

    private boolean frameRequested;

    /** UI thread (from draw): render again right after this frame. */
    void requestFrame() {
        frameRequested = true;
    }

    /** Whether something asked to be drawn again right after the last frame (an animation). */
    boolean animating() {
        return frameRequested;
    }

    /** Thread-safe: ends the app after the current iteration. */
    void requestClose() {
        platform.close();
    }

    /** Thread-safe: asks the UI thread to re-evaluate and redraw. */
    void requestRender() {
        dirty.set(true);
        platform.wake();
    }

    /** Whether a render is due; clears the flag. */
    boolean takeDirty() {
        return dirty.getAndSet(false);
    }

    void markDirty() {
        dirty.set(true);
    }

    /** Runs {@code task} on the UI thread after {@code seconds}. Thread-safe. Returns a handle to cancel it. */
    Runnable schedule(double seconds, Runnable task) {
        Timer timer;
        synchronized (timers) {
            timer = new Timer(platform.time() + Math.max(0, seconds), timerSequence++, task);
            timers.add(timer);
        }
        if (Thread.currentThread() != uiThread) {
            platform.wake(); // wake the loop so it recomputes how long to sleep
        }
        return () -> {
            synchronized (timers) {
                timers.remove(timer);
            }
        };
    }

    /** One loop iteration's housekeeping: queued tasks, due timers and the caret blink. */
    void runPending() {
        for (Runnable task; (task = uiTasks.poll()) != null; ) {
            safely(null, task);
        }
        double now = platform.time();
        while (true) {
            Timer due;
            synchronized (timers) {
                Timer next = timers.peek();
                if (next == null || next.due > now) {
                    break;
                }
                due = timers.poll();
            }
            safely(null, due.task);
            dirty.set(true);
        }
        if (focused != null && focused.blinks() && now >= nextBlink) {
            focused.caretOn = !focused.caretOn;
            nextBlink = now + BLINK_SECONDS;
            dirty.set(true);
        }
    }

    /** When the next timer or caret blink is due ({@link Platform#time()} seconds), or {@link Double#MAX_VALUE}. */
    double nextWake() {
        double wake;
        synchronized (timers) {
            Timer next = timers.peek();
            wake = next == null ? Double.MAX_VALUE : next.due;
        }
        if (focused != null && focused.blinks()) {
            wake = Math.min(wake, nextBlink);
        }
        return wake;
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
            toasts.remove(0);
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

    boolean onUiThread() {
        return Thread.currentThread() == uiThread;
    }

    void free() {
        canvas.free();
    }

    /**
     * Draws everything. The framebuffer is {@code framebufferWidth} x {@code framebufferHeight} physical pixels,
     * {@code scale} physical pixels make one logical pixel, and one window coordinate (what input comes in) is
     * {@code windowToLogical} logical pixels.
     */
    void render(int framebufferWidth, int framebufferHeight, float scale, float windowToLogical) {
        this.windowToLogical = windowToLogical;
        frameRequested = false;
        flingStep();
        Vg vg = canvas.vg();
        Color bg = theme.getBackground();
        vg.clear(framebufferWidth, framebufferHeight, bg.r(), bg.g(), bg.b());

        vg.beginFrame(framebufferWidth / scale, framebufferHeight / scale, scale);
        vg.translate(insetLeft, insetTop);
        canvas.setTheme(theme);
        canvas.beginFrame(scale, platform.time(), framebufferWidth, framebufferHeight);
        float logicalWidth = framebufferWidth / scale - insetLeft - insetRight;
        float logicalHeight = framebufferHeight / scale - insetTop - insetBottom;
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
        vg.endFrame();
        canvas.collectSurfaces();

        if (focused != null && !focused.interactive()) {
            focus(null, false); // it was disabled or hidden by the last change
        }
        updateHover(); // the layout may have moved something under the cursor
    }

    /**
     * Parts of the window the system covers (status and navigation bars, the on-screen keyboard), in logical pixels.
     * Screens are laid out in what's left.
     */
    void insets(float left, float top, float right, float bottom) {
        if (left == insetLeft && top == insetTop && right == insetRight && bottom == insetBottom) {
            return;
        }
        boolean shrunk = bottom > insetBottom;
        insetLeft = left;
        insetTop = top;
        insetRight = right;
        insetBottom = bottom;
        if (shrunk && focused != null && screen != null && modalOverlay() == null) {
            Widget target = focused;
            uiTasksLater(() -> screen.reveal(target)); // after the next layout: keep the field above the keyboard
        }
        dirty.set(true);
    }

    // ---- input, in window coordinates ----

    /** The pointer moved to (x, y), in window coordinates. */
    void pointerMoved(double x, double y) {
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
    }

    /** Sets where the pointer is without reporting a move (it entered the window, or a press came first). */
    void pointerAt(double x, double y) {
        cursorX = x;
        cursorY = y;
    }

    /** The pointer left the window (or a finger was lifted). */
    void pointerLeft() {
        cursorX = -1;
        cursorY = -1;
        updateHover();
    }

    void pointerButton(int button, boolean down) {
        if (down) {
            if (pressed != null) {
                return; // one button at a time
            }
            if (dismissToastAt(logicalX(cursorX), logicalY(cursorY))) {
                return;
            }
            updateHover();
            if (button == BUTTON_LEFT) {
                countClicks();
            }
            Overlay top = topOverlay();
            if (top != null && !top.modal() && !top.contains(logicalX(cursorX), logicalY(cursorY))) {
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
            pressed.pressed = button == BUTTON_LEFT;
            if (pressed.focusable()) {
                focus(pressed, false);
            }
            if (pressed.blinks()) {
                platform.textInput(true); // tapped a text field: bring up the on-screen keyboard
            }
            Widget target = pressed;
            int clicks = button == BUTTON_LEFT ? clickCount : 1;
            safely(target, () -> target.pointerDown(pointer(buttonOf(button), clicks, 0, 0)));
            dirty.set(true);
        } else if (pressed != null && button == pressedButton) {
            Widget released = pressed;
            pressed = null;
            pressedButton = -1;
            released.pressed = false;
            safely(released, () -> released.pointerUp(pointer(buttonOf(button), 0, 0, 0)));
            if (button == BUTTON_LEFT && released == hovered && released.clickable()) {
                safely(released, () -> released.activate(this));
            }
            updateHover();
            dirty.set(true);
        }
    }

    /**
     * A key went down ({@code repeat}: is held). {@code key} is a GLFW key code with letters already mapped to the
     * keyboard layout. Returns whether anything used it.
     */
    boolean key(int key, int mods, boolean repeat) {
        resetBlink();
        hideTooltip();
        dirty.set(true); // the key may have changed state that lambdas read
        Overlay top = topOverlay();
        if (key == KEY_TAB) {
            moveFocus((mods & MOD_SHIFT) != 0 ? -1 : 1);
            return true;
        }
        boolean[] used = {false};
        Widget target = focused;
        if (target != null) {
            safely(target, () -> used[0] = target.keyPressed(key, mods, this));
        }
        if (!used[0] && top != null) {
            if (key == KEY_ESCAPE) {
                safely(top, top::escape);
                if (!top.modal()) {
                    closeOverlay(top);
                }
                used[0] = true;
            } else if (top.modal()) {
                safely(top, () -> used[0] = top.keyPressed(key, mods, this));
            }
        }
        if (!used[0] && (top == null || !top.modal())) {
            used[0] = shortcuts.test(new KeyPress(key, mods, repeat), focused);
        }
        return used[0];
    }

    /** A character was typed. */
    void typed(int codePoint) {
        resetBlink();
        Widget target = focused;
        if (target != null) {
            safely(target, () -> target.typed(codePoint, this));
        }
        dirty.set(true);
    }

    /** Mouse wheel or touchpad, in wheel steps (positive: up/left). Returns whether anything scrolled. */
    boolean scroll(double dx, double dy) {
        hideTooltip();
        Pointer at = pointer(Pointer.Button.NONE, 0, 0, 0);
        for (Widget w = hovered; w != null; w = w.parent) { // the widget under the mouse first, then its parents
            Widget candidate = w;
            boolean[] used = {false};
            safely(candidate, () -> used[0] = candidate.scrolled(at, dx, dy));
            if (used[0]) {
                dirty.set(true);
                return true;
            }
        }
        Overlay top = topOverlay();
        if (top != null) {
            if (top.contains(at.x(), at.y()) && top.scrolled(at, dx, dy)) {
                dirty.set(true);
                return true;
            }
            return false; // the page doesn't scroll away under an open popup or dialog
        }
        if (screen != null && screen.scrollBy(at.x(), at.y(), dy)) {
            dirty.set(true);
            return true;
        }
        return false;
    }

    /** Files were dropped onto the window at the pointer. */
    void dropped(List<java.nio.file.Path> files) {
        if (files.isEmpty() || screen == null || modalOverlay() != null) {
            return;
        }
        Pointer at = pointer(Pointer.Button.NONE, 0, 0, 0);
        List<java.nio.file.Path> dropped = List.copyOf(files);
        for (Widget w = screen.widgetAt(at.x(), at.y()); w != null; w = w.parent) { // the widget under the mouse, then outwards
            Widget candidate = w;
            boolean[] used = {false};
            safely(candidate, () -> used[0] = candidate.onDrop(at, dropped));
            if (used[0]) {
                break;
            }
        }
        dirty.set(true);
    }

    // ---- touch ----

    /** Pixels (logical) a finger may move before a press becomes a scroll. */
    private static final float TOUCH_SLOP = 8;
    /** Logical pixels per wheel step, as {@link Screen} scrolls. */
    private static final float PIXELS_PER_STEP = 48;
    private static final double FLING_FRICTION = 4;
    private static final double FLING_MIN_SPEED = 30;

    private boolean touching;
    private boolean touchScrolling;
    private double touchStartX;
    private double touchStartY;
    private double touchLastX;
    private double touchLastY;
    private double touchLastTime;
    /** Finger speed in logical pixels per second, smoothed. */
    private double touchSpeedX;
    private double touchSpeedY;
    private double flingSpeedX;
    private double flingSpeedY;
    private double flingTime;

    /** A finger touched the screen at (x, y), window coordinates. */
    void touchDown(double x, double y) {
        flingSpeedX = flingSpeedY = 0;
        touching = true;
        touchScrolling = false;
        touchStartX = touchLastX = x;
        touchStartY = touchLastY = y;
        touchLastTime = platform.time();
        touchSpeedX = touchSpeedY = 0;
        pointerMoved(x, y);
        pointerButton(BUTTON_LEFT, true);
    }

    /**
     * The finger moved. Widgets that handle drags (sliders, custom widgets with {@code onDrag}) get it as a drag;
     * anywhere else, moving past a few pixels turns the touch into a scroll and the press is let go without a click.
     */
    void touchMoved(double x, double y) {
        if (!touching) {
            return;
        }
        double now = platform.time();
        double dt = Math.max(1e-3, now - touchLastTime);
        double alpha = Math.min(1, dt / 0.05);
        touchSpeedX += ((x - touchLastX) * windowToLogical / dt - touchSpeedX) * alpha;
        touchSpeedY += ((y - touchLastY) * windowToLogical / dt - touchSpeedY) * alpha;
        touchLastTime = now;
        if (!touchScrolling && pressed != null && capturesDrag(pressed)) {
            touchLastX = x;
            touchLastY = y;
            pointerMoved(x, y);
            return;
        }
        if (!touchScrolling) {
            double distance = Math.hypot(x - touchStartX, y - touchStartY) * windowToLogical;
            if (distance < TOUCH_SLOP) {
                return;
            }
            touchScrolling = true;
            cancelPress();
        }
        double dx = (x - touchLastX) * windowToLogical;
        double dy = (y - touchLastY) * windowToLogical;
        touchLastX = x;
        touchLastY = y;
        scroll(dx / PIXELS_PER_STEP, dy / PIXELS_PER_STEP); // the pointer stays where the touch began
    }

    /** The finger was lifted; {@code cancelled} if the system took the touch away. */
    void touchUp(double x, double y, boolean cancelled) {
        if (!touching) {
            return;
        }
        touching = false;
        if (touchScrolling) {
            if (!cancelled && Math.hypot(touchSpeedX, touchSpeedY) > FLING_MIN_SPEED * 4) {
                flingSpeedX = touchSpeedX;
                flingSpeedY = touchSpeedY;
                flingTime = platform.time();
                requestRender();
                return; // the pointer stays put until the fling ends
            }
        } else if (cancelled) {
            cancelPress();
        } else {
            pointerMoved(x, y);
            pointerButton(BUTTON_LEFT, false);
        }
        pointerLeft(); // fingers don't hover
    }

    /** Keeps a flung list moving, slowing down. Called once per frame. */
    private void flingStep() {
        if (flingSpeedX == 0 && flingSpeedY == 0) {
            return;
        }
        double now = platform.time();
        double dt = Math.min(0.05, now - flingTime);
        flingTime = now;
        double decay = Math.exp(-FLING_FRICTION * dt);
        flingSpeedX *= decay;
        flingSpeedY *= decay;
        boolean moved = scroll(flingSpeedX * dt / PIXELS_PER_STEP, flingSpeedY * dt / PIXELS_PER_STEP);
        if (!moved || Math.hypot(flingSpeedX, flingSpeedY) < FLING_MIN_SPEED) {
            flingSpeedX = flingSpeedY = 0;
            if (!touching) {
                pointerLeft();
            }
            return;
        }
        frameRequested = true;
    }

    /** Lets go of the pressed widget without clicking it. */
    private void cancelPress() {
        if (pressed == null) {
            return;
        }
        Widget released = pressed;
        int button = pressedButton;
        pressed = null;
        pressedButton = -1;
        released.pressed = false;
        safely(released, () -> released.pointerUp(pointer(buttonOf(button), 0, 0, 0)));
        dirty.set(true);
    }

    private static final java.util.Map<Class<?>, Boolean> DRAGGABLE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Whether the widget's class (or a superclass below {@link Widget}) handles drags itself. */
    static boolean capturesDrag(Widget widget) {
        return DRAGGABLE.computeIfAbsent(widget.getClass(), type -> {
            for (Class<?> c = type; c != null && c != Widget.class; c = c.getSuperclass()) {
                try {
                    c.getDeclaredMethod("onDrag", Pointer.class);
                    return true;
                } catch (NoSuchMethodException ignored) {
                    // keep looking
                }
            }
            return false;
        });
    }

    /** Called for keys nobody used: screen, component and app-wide shortcuts. Returns whether one ran. Set by {@link Unweird}. */
    java.util.function.BiPredicate<KeyPress, Widget> shortcuts = (key, focus) -> false;

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
            float x = logicalX(cursorX);
            float y = logicalY(cursorY);
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
        platform.cursor(shapeSource == null ? null : shapeSource.cursor());
    }

    private void armTooltip(Widget target) {
        hideTooltip();
        if (target == null || target.tooltipText() == null || pressed != null || touching) {
            return;
        }
        tooltipFor = target;
        tooltipTimer = schedule(TOOLTIP_DELAY, () -> {
            if (hovered == target && pressed == null && target.tooltipText() != null) {
                tooltipShown = true;
                tooltipX = logicalX(cursorX);
                tooltipY = logicalY(cursorY);
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

    private Pointer pointer(Pointer.Button button, int clicks, float dx, float dy) {
        int mods = platform.modifiers();
        return new Pointer(logicalX(cursorX), logicalY(cursorY), button, clicks,
                (mods & KeyPress.MOD_SHIFT) != 0, (mods & KeyPress.MOD_CONTROL) != 0, (mods & KeyPress.MOD_ALT) != 0, dx, dy);
    }

    private static Pointer.Button buttonOf(int button) {
        return switch (button) {
            case BUTTON_LEFT -> Pointer.Button.LEFT;
            case BUTTON_RIGHT -> Pointer.Button.RIGHT;
            case BUTTON_MIDDLE -> Pointer.Button.MIDDLE;
            default -> Pointer.Button.NONE;
        };
    }

    private Overlay topOverlay() {
        return overlays.isEmpty() ? null : overlays.get(overlays.size() - 1);
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

    private float logicalX(double windowX) {
        return (float) windowX * windowToLogical - insetLeft;
    }

    private float logicalY(double windowY) {
        return (float) windowY * windowToLogical - insetTop;
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
        if (next == null || !next.blinks()) {
            platform.textInput(false);
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
        nextBlink = platform.time() + BLINK_SECONDS;
    }

    private void countClicks() {
        double now = platform.time();
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
            Overlay removed = overlays.remove(overlays.size() - 1);
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
        return platform.clipboard();
    }

    @Override
    public void clipboard(String text) {
        platform.clipboard(text);
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

    static byte[] loadResource(String path) {
        try (InputStream in = Window.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("unweird: missing resource " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
