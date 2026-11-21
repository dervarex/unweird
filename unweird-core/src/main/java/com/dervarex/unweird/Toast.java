package com.dervarex.unweird;

import java.time.Duration;
import java.util.Objects;

/**
 * A short message in the corner that goes away by itself (click to dismiss early).
 * {@code toast("Saved")}, {@code toast(Toast.error("Couldn't connect"))}.
 */
public record Toast(String message, Kind kind, Duration duration) {

    public enum Kind { INFO, SUCCESS, ERROR }

    public Toast {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(duration, "duration");
    }

    public static Toast info(String message) {
        return new Toast(message, Kind.INFO, Duration.ofSeconds(4));
    }

    public static Toast success(String message) {
        return new Toast(message, Kind.SUCCESS, Duration.ofSeconds(4));
    }

    /** Errors stay a bit longer. */
    public static Toast error(String message) {
        return new Toast(message, Kind.ERROR, Duration.ofSeconds(8));
    }

    public Toast lasting(Duration duration) {
        return new Toast(message, kind, duration);
    }
}
