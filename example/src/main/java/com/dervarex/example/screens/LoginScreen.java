package com.dervarex.example.screens;

import com.dervarex.example.components.Header;
import com.dervarex.example.components.Sidebar;
import com.dervarex.unweird.Button;
import com.dervarex.unweird.Checkbox;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.annotation.Without;

@Title("Sign in")
@Without({Sidebar.class, Header.class})
public class LoginScreen extends Screen {

    @State String message = "";

    Text heading  = new Text("Welcome back");
    Text greeting = new Text(this::greeting);            // live: updates while you type
    Field user    = new Field("Username");
    Field pass    = new Field("Password").masked().onSubmit(this::signIn);

    // one row: the checkbox lined up with the fields' left edge, the link with their right edge
    Button forgot = new Button("Forgot password?").ghost().alignRight(pass).gap(4)
            .onClick(() -> message = "Try \"hunter2\".");
    Checkbox remember = new Checkbox("Remember me").leftOf(forgot).alignLeft(pass);

    Button signIn = new Button("Sign in").onClick(this::signIn);
    Text status   = new Text(() -> message);

    @Override
    protected void onShow() {
        pass.value("");   // never keep a password around after logging out
        message = "";
    }

    private String greeting() {
        return user.value().isBlank() ? "Sign in to continue" : "Hi " + user.value() + "!";
    }

    private void signIn() {
        if (user.value().isBlank() || pass.value().isEmpty()) {
            message = "Please enter a username and password.";
            return;
        }
        shared(Header.class).setUser(user.value().strip());
        go(HomeScreen.class);
    }
}
