package com.dervarex.example.screens;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

@Title("unweird — M1")
public class HomeScreen extends Screen {

    @State int clicks = 0;

    Text heading  = new Text("Welcome home!");
    Text subtitle = new Text("This is milestone 1: text, buttons and a layout you didn't write.");
    Text counter  = new Text(() -> switch (clicks) {
        case 0 -> "Nothing clicked yet.";
        case 1 -> "Clicked once.";
        default -> "Clicked " + clicks + " times.";
    });
    Button plus   = new Button("Click me").onClick(() -> clicks++);
    Button reset  = new Button("Reset").onClick(() -> clicks = 0);
}
