package com.dervarex.example.modrinth;

/** Modrinth couldn't be reached, answered with an error, or sent something unusable. */
public class ModrinthException extends RuntimeException {

    public ModrinthException(String message) {
        super(message);
    }

    public ModrinthException(String message, Throwable cause) {
        super(message, cause);
    }
}
