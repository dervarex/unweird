package com.dervarex.examples.todo;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Each;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Shortcut;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

import java.util.ArrayList;
import java.util.List;

@Title("Todo")
public class TodoScreen extends Screen {

    @State final List<Task> tasks = new ArrayList<>(List.of(new Task("Read the docs"), new Task("Steal a snippet")));

    Text heading  = new Text("Todo");
    Text subtitle = new Text(this::summary);

    Field input   = new Field("What needs doing?").onSubmit(this::add);
    Button add    = new Button("Add").rightOf(input).onClick(this::add);

    Each<Task> rows = new Each<>(() -> tasks, task -> new TaskRow(task, tasks::remove));

    Button clear  = new Button("Clear finished").secondary()
            .visibleIf(() -> tasks.stream().anyMatch(t -> t.done))
            .onClick(() -> tasks.removeIf(t -> t.done));

    private String summary() {
        long open = tasks.stream().filter(t -> !t.done).count();
        return open == 0 ? "All done." : open + " open  ·  Ctrl+N for a new one";
    }

    private void add() {
        if (!input.value().isBlank()) {
            tasks.add(new Task(input.value().strip()));
            input.value("");
        }
    }

    @Shortcut("Ctrl+N")
    void focusInput() {
        input.focus();
    }
}
