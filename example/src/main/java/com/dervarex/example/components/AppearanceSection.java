package com.dervarex.example.components;

import com.dervarex.unweird.Component;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toggle;
import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

/** A settings section; its first text becomes a section title automatically. */
public class AppearanceSection extends Component {

    Text title  = new Text("Appearance");
    Text hint   = new Text("Switches every screen at once.");
    Toggle dark = new Toggle("Dark mode")
            .checked(Unweird.theme().isDark())
            .onChange(on -> Unweird.theme(on ? Theme.dark() : Theme.light()));
}
