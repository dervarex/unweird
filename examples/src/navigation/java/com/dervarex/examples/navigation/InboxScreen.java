package com.dervarex.examples.navigation;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.ListView;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Title;

/** Cached: come back later and the selection is still there. */
@Title("Inbox")
public class InboxScreen extends Screen {

    Text heading  = new Text("Inbox");
    Text subtitle = new Text(() -> Mail.inbox.size() + " messages  ·  Ctrl+1/2/3 switches screens");

    ListView<Mail> list = new ListView<>(() -> Mail.inbox).empty("Inbox zero.");

    Button archive = new Button("Archive").enabledIf(() -> list.selected() != null).onClick(this::archive);

    private void archive() {
        Mail mail = list.selected();
        Mail.inbox.remove(mail);
        Mail.archive.add(mail);
        toast("Archived");
    }
}
