package com.dervarex.example.android;

import com.dervarex.unweird.Dropdown;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Slider;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toggle;
import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.annotation.Shortcut;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.style.Color;
import com.dervarex.unweird.style.Theme;

import java.util.List;

@Title("Settings")
public class SettingsScreen extends Screen {

    record Accent(String label, Color color) {
    }

    static final List<Accent> ACCENTS = List.of(new Accent("Indigo", Color.hex("#5b5bd6")),
            new Accent("Teal", Color.hex("#0d9488")), new Accent("Orange", Color.hex("#ea580c")));

    Text heading = new Text("Settings");
    Toggle dark = new Toggle("Dark mode")
            .checked(Unweird.theme().isDark())
            .onChange(on -> Unweird.theme((on ? Theme.dark() : Theme.light()).withAccent(Unweird.theme().getAccent())));
    Slider corners = new Slider("Corner roundness", 0, 16)
            .value(Unweird.theme().getRadius())
            .format(v -> Math.round(v) + " px")
            .onChange(v -> Unweird.theme(Unweird.theme().withRadius((float) v)));
    Dropdown<Accent> accent = new Dropdown<>("Accent color", ACCENTS)
            .labels(Accent::label)
            .onChange(a -> Unweird.theme(Unweird.theme().withAccent(a.color())));

    @Shortcut("Escape")
    void back() {
        go(TasksScreen.class);
    }
}
