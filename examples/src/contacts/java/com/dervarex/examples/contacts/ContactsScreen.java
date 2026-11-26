package com.dervarex.examples.contacts;

import com.dervarex.unweird.Field;
import com.dervarex.unweird.ListView;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Title;

import java.util.List;
import java.util.Locale;

@Title("Contacts")
public class ContactsScreen extends Screen {

    private final List<Contact> all = List.of(
            new Contact("Ada Lovelace", "ada@example.com", "+44 20 7946 0001", "Wrote the first program. Ask her about the Analytical Engine."),
            new Contact("Alan Turing", "alan@example.com", "+44 20 7946 0002", "Codebreaker. Likes long-distance running."),
            new Contact("Grace Hopper", "grace@example.com", "+1 202 555 0103", "Found an actual bug in a relay. Has a nanosecond in her pocket."),
            new Contact("Margaret Hamilton", "margaret@example.com", "+1 617 555 0104", "Flight software. Coined \"software engineering\"."));

    Text heading = new Text("Contacts");
    Field filter = new Field("Filter");

    ListView<Contact> list = new ListView<>(this::visible)
            .labels(Contact::name)
            .empty("Nobody matches.");

    ContactDetails details = new ContactDetails(() -> list.selected());

    private List<Contact> visible() {
        String q = filter.value().strip().toLowerCase(Locale.ROOT);
        return all.stream().filter(c -> c.name().toLowerCase(Locale.ROOT).contains(q)).toList();
    }
}
