package com.dervarex.example.android;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Dialog;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.ListView;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toast;
import com.dervarex.unweird.annotation.Shortcut;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

import java.util.ArrayList;
import java.util.List;

@Title("Tasks")
public class TasksScreen extends Screen {

    @State final List<String> tasks = new ArrayList<>(List.of("Drag the list to scroll", "Swipe back to leave",
            "Open the settings", "Try the dialog", "Water the plants", "Call mum", "Buy oat milk",
            "Read a chapter", "Fix the bike", "Answer emails", "Plan the weekend", "Go for a run"));

    Text heading  = new Text(() -> tasks.isEmpty() ? "All done." : tasks.size() + " tasks");
    Field task    = new Field("What needs doing?").onSubmit(this::add);
    Button add    = new Button("Add").rightOf(task).onClick(this::add);

    ListView<String> list = new ListView<>(() -> tasks).empty("Nothing to do. Nice.");
    Button done   = new Button(() -> list.selected() == null ? "Tap a task to select it" : "Done: " + list.selected())
            .onClick(this::finish);
    Button clear  = new Button("Clear all…").secondary().onClick(this::clear);
    Button settings = new Button("Settings").secondary().onClick(() -> go(SettingsScreen.class));
    Button about  = new Button("About").secondary().rightOf(settings).onClick(() -> go(AboutScreen.class));

    private void add() {
        if (!task.value().isBlank()) {
            tasks.add(0, task.value().strip());
            task.value("");
        }
    }

    private void finish() {
        if (list.selected() != null) {
            toast(Toast.success("Done: " + list.selected()));
            tasks.remove(list.selected());
        }
    }

    private void clear() {
        show(new Dialog("Clear all tasks?", "This removes " + tasks.size() + " tasks.")
                .action("Clear", tasks::clear).danger()
                .cancel("Keep them"));
    }

    @Shortcut("Escape")
    void back() {
        go(WelcomeScreen.class);
    }
}
