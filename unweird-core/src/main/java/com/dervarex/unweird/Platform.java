package com.dervarex.unweird;

/**
 * What {@link Window} needs from the system it runs on: GLFW on desktop ({@link GlfwWindow}), an Activity on Android
 * (unweird-android). Everything runs on the UI thread unless it says otherwise.
 */
interface Platform {

    /** Seconds on a monotonic clock. Thread-safe. */
    double time();

    /** Wakes the UI thread so it runs queued tasks and renders. Thread-safe. */
    void wake();

    /** Ends the app as if the user closed it. Thread-safe. */
    void close();

    String clipboard();

    void clipboard(String text);

    /** The pointer shape over the current widget; null for the default arrow. */
    void cursor(Cursor cursor);

    void title(String title);

    /** Modifier keys held right now, as {@link KeyPress} mod bits. */
    int modifiers();

    /** A text field was tapped (true) or text input ended (false): shows or hides an on-screen keyboard. */
    default void textInput(boolean active) {
    }
}
