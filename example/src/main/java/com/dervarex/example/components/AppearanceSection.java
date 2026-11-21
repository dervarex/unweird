package com.dervarex.example.components;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

/** A settings section; its first text becomes a section title automatically. */
public class AppearanceSection extends Component {

    Text title = new Text("Appearance");
    Text hint  = new Text("Switches every screen at once.");
    Button toggle = new Button(() -> Unweird.theme().isDark() ? "Use light mode" : "Use dark mode")
            .secondary()
            .onClick(() -> Unweird.theme(Unweird.theme().isDark() ? Theme.light() : Theme.dark()));
}
