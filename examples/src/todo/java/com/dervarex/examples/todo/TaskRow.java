package com.dervarex.examples.todo;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Checkbox;
import com.dervarex.unweird.Component;

import java.util.function.Consumer;

/** One row per task. Built per item by Each, so it can take constructor arguments. */
public class TaskRow extends Component {

    final Checkbox done;
    final Button remove;

    TaskRow(Task task, Consumer<Task> onRemove) {
        done = new Checkbox(task.title).checked(task.done).onChange(on -> task.done = on);
        remove = new Button("Remove").ghost().rightOf(done).onClick(() -> onRemove.accept(task));
    }
}
