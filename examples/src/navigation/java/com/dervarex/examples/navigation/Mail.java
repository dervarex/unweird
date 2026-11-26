package com.dervarex.examples.navigation;

import java.util.ArrayList;
import java.util.List;

/** The app's data. Static here to keep the example short; normally a service you pass around. */
public record Mail(String from, String subject) {

    static final List<Mail> inbox = new ArrayList<>(List.of(
            new Mail("Ada", "Lunch on Friday?"),
            new Mail("Linus", "Re: patch v3"),
            new Mail("Grace", "Slides for the talk")));

    static final List<Mail> archive = new ArrayList<>();

    @Override
    public String toString() {
        return from + "  —  " + subject;
    }
}
