package com.dervarex.examples.search;

import java.util.List;
import java.util.Locale;

/** Pretends to be a slow web API. */
public final class Library {

    private static final List<Book> BOOKS = List.of(
            new Book("The Pragmatic Programmer", "Hunt & Thomas", 1999),
            new Book("Effective Java", "Joshua Bloch", 2018),
            new Book("Designing Data-Intensive Applications", "Martin Kleppmann", 2017),
            new Book("A Philosophy of Software Design", "John Ousterhout", 2018),
            new Book("Structure and Interpretation of Computer Programs", "Abelson & Sussman", 1985),
            new Book("The Mythical Man-Month", "Fred Brooks", 1975),
            new Book("Crafting Interpreters", "Robert Nystrom", 2021));

    private Library() {
    }

    public static List<Book> search(String query) throws InterruptedException {
        Thread.sleep(400);
        String q = query.strip().toLowerCase(Locale.ROOT);
        return BOOKS.stream()
                .filter(b -> b.title().toLowerCase(Locale.ROOT).contains(q) || b.author().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }
}
