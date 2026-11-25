package com.dervarex.unweird;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The Android backend: a {@link GLSurfaceView} whose GL thread is unweird's UI thread. Input arrives on Android's
 * main thread and is handed over as UI tasks; drawing happens on demand ({@code RENDERMODE_WHEN_DIRTY}).
 */
final class AndroidWindow implements Platform, GLSurfaceView.Renderer {

    private final Activity activity;
    private final App app;
    private final Class<? extends Screen> firstScreen;
    private final Surface view;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable wakeUp;
    private final float density;
    /** Looked up on the main thread: older Android versions can't create it on the GL thread. */
    private final ClipboardManager clipboard;

    /** Created on the GL thread once the surface exists. */
    private volatile Window window;
    private int framebufferWidth;
    private int framebufferHeight;
    private volatile int modifiers;
    private Boolean lightBars;

    AndroidWindow(Activity activity, App app, Class<? extends Screen> firstScreen) {
        this.activity = activity;
        this.app = app;
        this.firstScreen = firstScreen;
        this.density = activity.getResources().getDisplayMetrics().density;
        this.clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        view = new Surface(activity, this);
        view.setEGLContextClientVersion(3);
        view.setEGLConfigChooser(8, 8, 8, 8, 0, 8); // NanoVG needs a stencil buffer
        view.setPreserveEGLContextOnPause(true);
        view.setRenderer(this);
        view.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            applyInsets(insets);
            return insets;
        });
        wakeUp = view::requestRender;
        Unweird.opener = this::open;
        Pixels.decoder = AndroidVg::decode; // no java.awt on Android
    }

    View view() {
        return view;
    }

    void resume() {
        view.onResume();
        view.requestFocus();
    }

    void pause() {
        view.onPause();
    }

    /** The activity is going away: run onHide and whileShown hooks. */
    void destroy() {
        main.removeCallbacksAndMessages(null);
        if (window != null) {
            Unweird.end();
            window = null;
        }
        Unweird.opener = null;
    }

    /** Android's back gesture: works like Escape (closes a popup or dialog, runs an {@code @Shortcut("Escape")}). */
    void back() {
        Window current = window;
        if (current == null) {
            activity.moveTaskToBack(true);
            return;
        }
        current.runOnUiThread(() -> {
            if (!current.key(Window.KEY_ESCAPE, 0, false)) {
                main.post(() -> activity.moveTaskToBack(true));
            }
        });
    }

    // ---- GLSurfaceView.Renderer, on the GL thread ----

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        Vg vg = new AndroidVg(); // a fresh context: the old one (if any) is gone with its images and fonts
        if (window == null) {
            Window created = new Window(Unweird.theme(), app, this, vg);
            window = created;
            Unweird.begin(created, firstScreen);
            main.post(view::requestApplyInsets); // insets that arrived before the window existed
        } else {
            window.replaceVg(vg);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        framebufferWidth = width;
        framebufferHeight = height;
        window.markDirty();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        Window current = window;
        if (current == null || framebufferWidth == 0) {
            return;
        }
        current.runPending();
        current.takeDirty();
        // GLSurfaceView shows whatever is in the buffer after this call, so every call draws a full frame.
        current.render(framebufferWidth, framebufferHeight, density, 1 / density);
        updateBarColors(current);
        main.removeCallbacks(wakeUp);
        if (current.animating() || current.takeDirty()) {
            view.requestRender();
            return;
        }
        double wake = current.nextWake();
        if (wake != Double.MAX_VALUE) {
            long delay = (long) Math.max(1, (wake - time()) * 1000);
            main.postDelayed(wakeUp, delay);
        }
    }

    /** Dark status bar icons on light themes and the other way round. */
    private void updateBarColors(Window current) {
        var bg = current.theme().getBackground();
        boolean light = 0.2126 * bg.r() + 0.7152 * bg.g() + 0.0722 * bg.b() > 0.5;
        if (lightBars != null && lightBars == light) {
            return;
        }
        lightBars = light;
        main.post(() -> SystemBars.light(activity, light));
    }

    // ---- Platform ----

    @Override
    public double time() {
        return System.nanoTime() / 1e9;
    }

    @Override
    public void wake() {
        view.requestRender();
    }

    @Override
    public void close() {
        main.post(activity::finish);
    }

    @Override
    public String clipboard() {
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(activity);
        return text == null ? null : text.toString();
    }

    @Override
    public void clipboard(String text) {
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("text", text));
        }
    }

    @Override
    public void cursor(Cursor cursor) {
        // touch screens have no pointer shape
    }

    @Override
    public void title(String title) {
        main.post(() -> activity.setTitle(title)); // shows in the recent apps list
    }

    @Override
    public int modifiers() {
        return modifiers;
    }

    @Override
    public void textInput(boolean active, boolean masked) {
        main.post(() -> {
            InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard == null) {
                return;
            }
            if (active) {
                if (!view.editing || view.masked != masked) {
                    view.editing = true;
                    view.masked = masked;
                    keyboard.restartInput(view); // a fresh connection; a password keyboard for masked fields
                }
                view.requestFocus();
                keyboard.showSoftInput(view, 0);
            } else if (view.editing) {
                view.editing = false;
                keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
                keyboard.restartInput(view);
            }
        });
    }

    private boolean open(String target) {
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(target.strip()));
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        } catch (RuntimeException e) {
            System.err.println("unweird: couldn't open " + target + ": " + e.getMessage());
            return false;
        }
    }

    // ---- input, on the main thread ----

    private void post(java.util.function.Consumer<Window> event) {
        Window current = window;
        if (current != null) {
            current.runOnUiThread(() -> event.accept(current));
        }
    }

    private boolean touch(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE) {
            return mouse(event, x, y);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> post(w -> w.touchDown(x, y));
            case MotionEvent.ACTION_MOVE -> post(w -> w.touchMoved(x, y));
            case MotionEvent.ACTION_UP -> post(w -> w.touchUp(x, y, false));
            case MotionEvent.ACTION_CANCEL -> post(w -> w.touchUp(x, y, true));
            default -> {
                return true; // more fingers: ignored for now
            }
        }
        return true;
    }

    /** A real mouse (Chromebooks, desktop mode): hover, buttons and the wheel like on desktop. */
    private boolean mouse(MotionEvent event, float x, float y) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_MOVE -> post(w -> w.pointerMoved(x, y));
            case MotionEvent.ACTION_HOVER_EXIT -> post(Window::pointerLeft);
            case MotionEvent.ACTION_BUTTON_PRESS -> {
                int button = mouseButton(event.getActionButton());
                post(w -> {
                    w.pointerAt(x, y);
                    w.pointerButton(button, true);
                });
            }
            case MotionEvent.ACTION_BUTTON_RELEASE -> {
                int button = mouseButton(event.getActionButton());
                post(w -> w.pointerButton(button, false));
            }
            case MotionEvent.ACTION_SCROLL -> {
                float dx = -event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                float dy = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                post(w -> {
                    w.pointerAt(x, y);
                    w.scroll(dx, dy);
                });
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private static int mouseButton(int androidButton) {
        return switch (androidButton) {
            case MotionEvent.BUTTON_SECONDARY -> Window.BUTTON_RIGHT;
            case MotionEvent.BUTTON_TERTIARY -> Window.BUTTON_MIDDLE;
            default -> Window.BUTTON_LEFT;
        };
    }

    private boolean keyDown(KeyEvent event) {
        modifiers = Keys.modifiers(event.getMetaState());
        int code = Keys.code(event.getKeyCode());
        int mods = modifiers;
        boolean repeat = event.getRepeatCount() > 0;
        int character = event.getUnicodeChar(event.getMetaState());
        boolean types = character >= 32 && (mods & (KeyPress.MOD_CONTROL | KeyPress.MOD_ALT | KeyPress.MOD_SUPER)) == 0;
        if (code < 0 && !types) {
            return false;
        }
        post(w -> {
            if (code >= 0) {
                w.key(code, mods, repeat);
            }
            if (types) {
                w.typed(character);
            }
        });
        return true;
    }

    private void typeText(CharSequence text) {
        String typed = text.toString();
        post(w -> typed.codePoints().forEach(codePoint -> {
            if (codePoint == '\n') {
                w.key(Keys.ENTER, 0, false);
            } else {
                w.typed(codePoint);
            }
        }));
    }

    private void pressKey(int glfwKey, int times) {
        for (int i = 0; i < times; i++) {
            post(w -> w.key(glfwKey, 0, false));
        }
    }

    private void applyInsets(WindowInsets insets) {
        float[] px = SystemBars.insets(insets);
        float left = px[0] / density;
        float top = px[1] / density;
        float right = px[2] / density;
        float bottom = px[3] / density;
        post(w -> w.insets(left, top, right, bottom));
    }

    /** The view: touch, keys and the on-screen keyboard's input connection. */
    private static final class Surface extends GLSurfaceView {

        private final AndroidWindow owner;
        boolean masked;
        /**
         * Whether a text field is being typed into. Only then does the view say it's a text editor: otherwise Android
         * pops the keyboard up on its own as soon as the app starts.
         */
        boolean editing;
        /** Code points of the IME's current composing text, which we typed already and replace on each update. */
        private int composing;

        Surface(Context context, AndroidWindow owner) {
            super(context);
            this.owner = owner;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return owner.touch(event);
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent event) {
            if (event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) && owner.mouse(event, event.getX(), event.getY())) {
                return true;
            }
            return super.onGenericMotionEvent(event);
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                return super.onKeyDown(keyCode, event);
            }
            return owner.keyDown(event) || super.onKeyDown(keyCode, event);
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            owner.modifiers = Keys.modifiers(event.getMetaState());
            return super.onKeyUp(keyCode, event);
        }

        @Override
        public boolean onCheckIsTextEditor() {
            return editing;
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo info) {
            if (!editing) {
                return null; // hardware keys still arrive through onKeyDown
            }
            info.inputType = masked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT;
            info.imeOptions = EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_FULLSCREEN
                    | (masked ? EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING : 0);
            composing = 0;
            return new BaseInputConnection(this, false) {
                @Override
                public boolean commitText(CharSequence text, int newCursorPosition) {
                    replaceComposing(text);
                    composing = 0;
                    return true;
                }

                @Override
                public boolean setComposingText(CharSequence text, int newCursorPosition) {
                    replaceComposing(text);
                    composing = text.toString().codePointCount(0, text.length());
                    return true;
                }

                @Override
                public boolean finishComposingText() {
                    composing = 0;
                    return true;
                }

                @Override
                public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                    owner.pressKey(Keys.BACKSPACE, beforeLength);
                    owner.pressKey(Keys.DELETE, afterLength);
                    return true;
                }

                @Override
                public boolean sendKeyEvent(KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        owner.keyDown(event);
                    }
                    return true;
                }

                @Override
                public boolean performEditorAction(int actionCode) {
                    owner.pressKey(Keys.ENTER, 1);
                    return true;
                }
            };
        }

        /** Takes back the composing text typed so far and types {@code text} instead. */
        private void replaceComposing(CharSequence text) {
            owner.pressKey(Keys.BACKSPACE, composing);
            owner.typeText(text);
        }
    }

    /** Status bar insets and colors, across Android versions. */
    private static final class SystemBars {

        /** {left, top, right, bottom} in pixels: system bars, display cutout and the on-screen keyboard. */
        static float[] insets(WindowInsets insets) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                return new float[]{bars.left, bars.top, bars.right, bars.bottom};
            }
            @SuppressWarnings("deprecation")
            float[] legacy = {insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()};
            return legacy;
        }

        /** Draws under the status and navigation bars; {@link #insets} says how much they cover. */
        @SuppressWarnings("deprecation")
        static void edgeToEdge(Activity activity) {
            android.view.Window window = activity.getWindow();
            window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
            window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false);
            } else {
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
            // the keyboard reports itself as an inset instead of covering the content
            // and it stays down until a field is tapped
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }

        @SuppressWarnings("deprecation")
        static void light(Activity activity, boolean light) {
            android.view.Window window = activity.getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                var controller = window.getInsetsController();
                if (controller != null) {
                    int both = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                    controller.setSystemBarsAppearance(light ? both : 0, both);
                }
                return;
            }
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            int lightFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            decor.setSystemUiVisibility(light ? flags | lightFlags : flags & ~lightFlags);
        }
    }

    static void edgeToEdge(Activity activity) {
        SystemBars.edgeToEdge(activity);
    }
}
