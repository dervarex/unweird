package com.dervarex.example.android;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.annotation.Without;

@Title("Welcome")
@Without(Profile.class)
public class WelcomeScreen extends Screen {

    Text heading  = new Text("unweird on Android");
    Text subtitle = new Text("The same screens as on desktop. Tap a field to type.");
    Field name    = new Field("Your name");
    Field secret  = new Field("Password").masked();
    Button start  = new Button("Continue").onClick(this::start);

    private void start() {
        shared(Profile.class).name = name.value().isBlank() ? "there" : name.value().strip();
        go(TasksScreen.class);
    }
}
