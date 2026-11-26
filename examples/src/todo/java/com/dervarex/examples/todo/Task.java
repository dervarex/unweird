package com.dervarex.examples.todo;

/** A plain class: Each matches rows by equals, and identity is exactly right here. */
public class Task {

    final String title;
    boolean done;

    Task(String title) {
        this.title = title;
    }
}
