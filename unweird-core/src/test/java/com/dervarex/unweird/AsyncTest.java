package com.dervarex.unweird;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Without a window, handlers run on the finishing thread; that's enough to check the plumbing. */
class AsyncTest {

    @Test
    void resultsReachThen() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> got = new AtomicReference<>();
        Unweird.async(() -> {
            start.await();
            return "worlds";
        }).then(value -> {
            got.set(value);
            delivered.countDown();
        });
        start.countDown();
        assertTrue(delivered.await(5, TimeUnit.SECONDS));
        assertEquals("worlds", got.get());
    }

    @Test
    void checkedExceptionsReachFailedUnwrapped() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<Throwable> got = new AtomicReference<>();
        Unweird.async(() -> {
            start.await();
            throw new IOException("offline");
        }).failed(error -> {
            got.set(error);
            delivered.countDown();
        });
        start.countDown();
        assertTrue(delivered.await(5, TimeUnit.SECONDS));
        assertInstanceOf(IOException.class, got.get());
        assertEquals("offline", got.get().getMessage());
    }

    @Test
    void unhandledErrorsDontThrow() throws Exception {
        Async<Object> async = Unweird.async(() -> {
            throw new IllegalStateException("nobody listens");
        });
        async.future().exceptionally(e -> null).get(5, TimeUnit.SECONDS);
    }

    @Test
    void toastsHaveSensibleDefaults() {
        assertEquals(Toast.Kind.ERROR, Toast.error("x").kind());
        assertTrue(Toast.error("x").duration().compareTo(Toast.info("x").duration()) > 0);
    }
}
