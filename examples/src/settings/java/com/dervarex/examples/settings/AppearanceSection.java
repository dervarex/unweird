package com.dervarex.examples.settings;

import com.dervarex.unweird.Dropdown;
import com.dervarex.unweird.Slider;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toggle;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.style.Theme;

import java.util.List;

/** Inside a component the first text becomes a section title. Every change re-themes the whole app at once. */
public class AppearanceSection extends Component {

    Text title = new Text("Appearance");

    Toggle dark = new Toggle("Dark mode")
            .checked(Unweird.theme().isDark())
            .onChange(on -> Unweird.theme((on ? Theme.dark() : Theme.light())
                    .withAccent(Unweird.theme().getAccent())
                    .withRadius(Unweird.theme().getRadius())));

    Dropdown<Accent> accent = new Dropdown<>("Accent color", List.of(Accent.values()))
            .labels(a -> a.label)
            .onChange(a -> Unweird.theme(Unweird.theme().withAccent(a.color)));

    Slider radius = new Slider("Corner radius", 0, 16)
            .step(1)
            .value(Unweird.theme().getRadius())
            .format(v -> Math.round(v) + " px")
            .onChange(v -> Unweird.theme(Unweird.theme().withRadius((float) v)));
}
