package com.dervarex.examples.settings;

import com.dervarex.unweird.Checkbox;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Slider;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toggle;

public class NotificationsSection extends Component {

    Text title      = new Text("Notifications");
    Toggle enabled  = new Toggle("Show notifications").checked(true);
    Checkbox sounds = new Checkbox("Play a sound").checked(true).enabledIf(() -> enabled.checked());
    Checkbox badges = new Checkbox("Badge on the app icon").enabledIf(() -> enabled.checked());
    Slider volume   = new Slider("Volume", 0, 100)
            .step(5)
            .value(60)
            .format(v -> Math.round(v) + "%")
            .enabledIf(() -> enabled.checked() && sounds.checked());
}
