package com.dervarex.unweird;

/** What the window offers to widgets while they handle input. Lets widgets stay testable without a real window. */
interface Host {

    String clipboard();

    void clipboard(String text);

    /** Runs user code (a handler); an exception is reported instead of crashing the app. {@code source} may be null. */
    void safely(Widget source, Runnable handler);
}
