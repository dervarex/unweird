package com.dervarex.example.screens;

import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Title;

/** A long page: too tall for the window, so it scrolls (mouse wheel, or Tab through it). */
@Title("About")
public class AboutScreen extends Screen {

    Text heading = new Text("About unweird");
    Text intro   = new Text("A Java UI library with its own renderer and an API that stays out of your way.");

    Text m1 = new Text("M1: a window, text and buttons, and a layout you didn't write. Widget fields are added "
            + "in the order you declare them; an annotation processor records that order at compile time.");
    Text m2 = new Text("M2: text fields with selection, clipboard, word movement and masked passwords. "
            + "Tab moves focus in declaration order; the first field on a screen gets the cursor.");
    Text m3 = new Text("M3: go(Screen.class) navigation with cached screens, @Fresh screens, and @Default "
            + "components docked around every screen unless it says @Without.");
    Text m4 = new Text("M4: content becomes a centered card when it's a short form, otherwise a page like this one. "
            + "below, rightOf, alignRight and gap fix what the inference gets wrong.");
    Text m5 = new Text("M5: checkboxes, toggles, dropdowns, lists, images, scrolling, and ready-made components.");
    Text why = new Text("Why the name? Because most UI toolkits feel a little weird: string IDs, layout code "
            + "everywhere, and listeners wired by hand. This one tries to read like a description of the screen.");
    Text font = new Text("Text is set in Noto Sans, licensed under the SIL Open Font License.");
    Text end = new Text("You scrolled all the way down. That's the whole story so far.");
}
