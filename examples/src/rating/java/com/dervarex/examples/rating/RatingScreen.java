package com.dervarex.examples.rating;

import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Title;

@Title("Rating")
public class RatingScreen extends Screen {

    private static final String[] WORDS = {"Not rated", "Awful", "Meh", "Fine", "Good", "Loved it"};

    Text heading  = new Text("How was it?");
    Text subtitle = new Text("Click a star, or Tab to it and use the arrow keys.");
    Stars stars   = new Stars().value(4);
    Text verdict  = new Text(() -> WORDS[stars.value()]);
}
