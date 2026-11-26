package com.dervarex.examples.counter;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Shortcut;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

@Title("Counter")
public class CounterScreen extends Screen {

    @State int count = 0;

    Text heading  = new Text(() -> String.valueOf(count));
    Text subtitle = new Text(() -> count == 0 ? "Nothing yet. Click, or press + and -." : "Clicked " + count + " times");

    Button plus   = new Button("+1").onClick(() -> count++);
    Button minus  = new Button("-1").enabledIf(() -> count > 0).onClick(() -> count--);
    Button reset  = new Button("Reset").ghost().visibleIf(() -> count != 0).onClick(() -> count = 0);

    @Shortcut("Plus")
    void up() {
        count++;
    }

    @Shortcut("Minus")
    void down() {
        count = Math.max(0, count - 1);
    }
}
