package com.dervarex.examples.contacts;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.annotation.Card;

import java.util.function.Supplier;

/** Shows whatever contact the supplier returns right now; the screen decides which one. */
@Card
public class ContactDetails extends Component {

    final Text name;
    final Text email;
    final Text phone;
    final Text note;
    final Button write;

    ContactDetails(Supplier<Contact> contact) {
        name  = new Text(() -> contact.get() == null ? "Pick someone" : contact.get().name());
        email = new Text(() -> contact.get() == null ? "" : contact.get().email()).muted();
        phone = new Text(() -> contact.get() == null ? "" : contact.get().phone());
        note  = new Text(() -> contact.get() == null ? "" : contact.get().note()).maxLines(3);
        write = new Button("Write an email")
                .visibleIf(() -> contact.get() != null)
                .onClick(() -> Unweird.open("mailto:" + contact.get().email()));
    }
}
