package com.dervarex.example.components;

import com.dervarex.unweird.Component;
import com.dervarex.unweird.Dropdown;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toggle;
import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Color;
import com.dervarex.unweird.style.Theme;

import java.util.Arrays;
import java.util.List;

/** A settings section; its first text becomes a section title automatically. */
public class AppearanceSection extends Component {

    enum Accent {
        INDIGO("Indigo", "#5b5bd6"),
        TEAL("Teal", "#0d9488"),
        ORANGE("Orange", "#ea580c"),
        PINK("Pink", "#db2777");

        final String label;
        final Color color;

        Accent(String label, String hex) {
            this.label = label;
            this.color = Color.hex(hex);
        }

        static Accent of(Color color) {
            return Arrays.stream(values()).filter(a -> a.color.equals(color)).findFirst().orElse(null);
        }
    }

    Text title  = new Text("Appearance");
    Text hint   = new Text("Switches every screen at once.");

    Toggle dark = new Toggle("Dark mode")
            .checked(Unweird.theme().isDark())
            .onChange(on -> Unweird.theme((on ? Theme.dark() : Theme.light()).withAccent(Unweird.theme().getAccent())));

    Dropdown<Accent> accent = new Dropdown<>("Accent color", List.of(Accent.values()))
            .labels(a -> a.label)
            .select(Accent.of(Unweird.theme().getAccent()))
            .onChange(a -> Unweird.theme(Unweird.theme().withAccent(a.color)));
}
