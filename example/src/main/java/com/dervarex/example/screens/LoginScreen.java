package com.dervarex.example.screens;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

@Title("Sign in")
public class LoginScreen extends Screen {

    @State String message = "";

    Text heading  = new Text("Welcome back");
    Text greeting = new Text(this::greeting);            // live: updates while you type
    Field user    = new Field("Username");
    Field pass    = new Field("Password").masked().onSubmit(this::signIn);
    Button signIn = new Button("Sign in").onClick(this::signIn);
    Text status   = new Text(() -> message);

    private String greeting() {
        return user.value().isBlank() ? "Sign in to continue" : "Hi " + user.value() + "!";
    }

    private void signIn() {
        if (user.value().isBlank() || pass.value().isEmpty()) {
            message = "Please enter a username and password.";
        } else {
            message = "Signed in as " + user.value() + ". Navigation arrives in M3.";
        }
    }
}
