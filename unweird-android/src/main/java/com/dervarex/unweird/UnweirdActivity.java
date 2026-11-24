package com.dervarex.unweird;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.window.OnBackInvokedDispatcher;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * An Android app made of unweird screens. The type argument is the first screen:
 *
 * <pre>{@code
 * public class Main extends UnweirdActivity<LoginScreen> {
 * }
 * }</pre>
 *
 * Screens, components, dialogs and toasts work as on desktop. Taps click, dragging scrolls, the on-screen keyboard
 * types into fields, and the back gesture works like Escape: it closes a popup or dialog, or runs the screen's
 * {@code @Shortcut("Escape")} method; with nothing to go back to, the app goes to the background.
 * Override {@link #app()} for the theme or extra fonts.
 */
public abstract class UnweirdActivity<S extends Screen> extends Activity {

    private AndroidWindow window;

    /** App settings: {@code return Unweird.app("Notes").theme(Theme.dark());}. Window sizes don't apply on Android. */
    protected App app() {
        return new App(String.valueOf(getTitle()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidWindow.edgeToEdge(this);
        window = new AndroidWindow(this, app(), firstScreen());
        setContentView(window.view());
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, window::back);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        window.back(); // before Android 13
    }

    @Override
    protected void onResume() {
        super.onResume();
        window.resume();
    }

    @Override
    protected void onPause() {
        window.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        window.destroy();
        super.onDestroy();
    }

    /** The {@code S} in {@code extends UnweirdActivity<S>}. */
    @SuppressWarnings("unchecked")
    private Class<? extends Screen> firstScreen() {
        for (Class<?> c = getClass(); c != null && c != UnweirdActivity.class; c = c.getSuperclass()) {
            Type parent = c.getGenericSuperclass();
            if (parent instanceof ParameterizedType type && type.getRawType() == UnweirdActivity.class
                    && type.getActualTypeArguments()[0] instanceof Class<?> screen) {
                return (Class<? extends Screen>) screen;
            }
        }
        throw new IllegalStateException("unweird: say which screen comes first: class "
                + getClass().getSimpleName() + " extends UnweirdActivity<FirstScreen>");
    }
}
