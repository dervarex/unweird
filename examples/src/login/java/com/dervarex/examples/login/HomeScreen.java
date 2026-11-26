package com.dervarex.examples.login;

import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Title;

@Title("Home")
public class HomeScreen extends Screen {

    Text heading  = new Text(() -> "Welcome, " + shared(Header.class).getUser());
    Text subtitle = new Text("The header up there is a @Default component, shared by every screen.");
}
