package com.dervarex.unweird;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Work in the background, result on the UI thread:
 *
 * <pre>{@code
 * async(() -> api.loadWorlds())
 *         .then(worlds -> this.worlds = worlds)
 *         .failed(error -> status = "Couldn't load: " + error.getMessage());
 * }</pre>
 *
 * The work runs on a virtual thread. {@link #then} and {@link #failed} run on the UI thread, and the UI refreshes
 * afterwards. Without a {@code failed} handler, errors are shown as an error toast (and printed), never swallowed.
 * Set up the handlers right away, in the same statement, from the UI thread (event handlers, {@code onShow()}).
 */
public final class Async<T> {

    private static final Executor WORKERS = Executors.newVirtualThreadPerTaskExecutor();

    private final CompletableFuture<T> future;
    private volatile Consumer<? super T> done;
    private volatile Consumer<Throwable> failed;

    private Async(Callable<T> work) {
        this.future = CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, WORKERS);
        future.whenComplete((value, error) -> Unweird.later(() -> deliver(value, error)));
    }

    static <T> Async<T> start(Callable<T> work) {
        return new Async<>(Objects.requireNonNull(work, "work"));
    }

    /** Runs {@code handler} with the result, on the UI thread. */
    public Async<T> then(Consumer<? super T> handler) {
        done = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /** Runs {@code handler} if the work threw, on the UI thread, instead of showing an error toast. */
    public Async<T> failed(Consumer<Throwable> handler) {
        failed = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /** The underlying future, e.g. to wait in tests. */
    public CompletableFuture<T> future() {
        return future;
    }

    private void deliver(T value, Throwable error) {
        if (error == null) {
            Consumer<? super T> handler = done;
            if (handler != null) {
                handler.accept(value);
            }
        } else {
            Throwable cause = unwrap(error);
            Consumer<Throwable> handler = failed;
            if (handler != null) {
                handler.accept(cause);
            } else {
                cause.printStackTrace();
                Unweird.toast(Toast.error(cause.getMessage() == null ? "Something went wrong (" + cause.getClass().getSimpleName() + ")" : cause.getMessage()));
            }
        }
        Unweird.refresh();
    }

    static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
