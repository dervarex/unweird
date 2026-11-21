package com.dervarex.example.screens;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

@Title("Home")
public class HomeScreen extends Screen {

    @State int clicks = 0;

    Text heading  = new Text("Welcome home!");
    Text subtitle = new Text("This screen is cached: the counter survives a trip to Settings.");
    Text counter  = new Text(() -> switch (clicks) {
        case 0 -> "Nothing clicked yet.";
        case 1 -> "Clicked once.";
        default -> "Clicked " + clicks + " times.";
    });
    Button plus   = new Button("Click me").onClick(() -> clicks++);
    Button reset  = new Button("Reset").onClick(() -> clicks = 0);
}
