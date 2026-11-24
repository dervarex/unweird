package com.dervarex.example.android;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Unweird;
import com.dervarex.unweird.annotation.Shortcut;
import com.dervarex.unweird.annotation.Title;

/** A long page: drag (or fling) to scroll. */
@Title("About")
public class AboutScreen extends Screen {

    Text heading = new Text("About unweird");
    Text intro   = new Text("A Java UI library with its own renderer. On Android it draws with the same NanoVG as on "
            + "desktop, so apps look the same everywhere.");
    Text screens = new Text("Screens are classes, widgets are fields, and the layout is inferred from the order you "
            + "declare them. Nothing here is Android-specific except the one-line activity.");
    Text touch   = new Text("Taps click. Dragging anywhere that isn't a slider scrolls, and a quick flick keeps it "
            + "moving. The back gesture works like Escape: it closes dialogs and popups, and screens can use it "
            + "with @Shortcut(\"Escape\").");
    Text keyboard = new Text("Fields bring up the on-screen keyboard; the page moves so the field stays visible. "
            + "Password fields get a password keyboard.");
    Text symbols = new Text("Emoji and symbols come from the bundled fonts: ✓ ★ → ⚡ 🎉 🧱");
    Text filler1 = new Text("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt "
            + "ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris.");
    Text filler2 = new Text("Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat "
            + "nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt.");
    Text filler3 = new Text("Sed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque "
            + "laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto.");
    Text end     = new Text("You scrolled all the way down.");
    Button github = new Button("View on GitHub").secondary()
            .onClick(() -> Unweird.open("https://github.com/dervarex/unweird"));

    @Shortcut("Escape")
    void back() {
        go(TasksScreen.class);
    }
}
