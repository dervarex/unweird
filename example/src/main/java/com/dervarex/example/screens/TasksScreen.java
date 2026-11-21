package com.dervarex.example.screens;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.ListView;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Shortcut;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

import java.util.ArrayList;
import java.util.List;

/** A live list: the ListView re-reads {@code tasks} after every event, so adding and removing just edits the list. */
@Title("Tasks")
public class TasksScreen extends Screen {

    @State final List<String> tasks = new ArrayList<>(List.of("Try the dropdown in Settings", "Scroll the About page"));

    Text heading  = new Text("Tasks");
    Text subtitle = new Text(() -> (tasks.isEmpty() ? "All done." : tasks.size() + " open") + "  ·  Ctrl+N new, Delete finishes the selected one");

    Field task    = new Field("What needs doing?").onSubmit(this::add);
    Button add    = new Button("Add").rightOf(task).onClick(this::add);

    ListView<String> list = new ListView<>(() -> tasks).empty("Nothing to do. Nice.");
    Button done   = new Button(() -> list.selected() == null ? "Select a task to finish it" : "Done: " + list.selected())
            .secondary()
            .onClick(this::finish);

    private void add() {
        if (!task.value().isBlank()) {
            tasks.add(task.value().strip());
            task.value("");
        }
    }

    @Shortcut("Ctrl+N")
    void newTask() {
        task.focus();
    }

    @Shortcut("Delete") // a focused field uses Delete itself, so this only fires elsewhere (e.g. in the list)
    private void finish() {
        if (list.selected() != null) {
            tasks.remove(list.selected());
        }
    }
}
