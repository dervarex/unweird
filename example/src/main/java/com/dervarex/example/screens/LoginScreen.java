package com.dervarex.example.screens;

import com.dervarex.example.components.Header;
import com.dervarex.example.components.Sidebar;
import com.dervarex.unweird.Image;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.annotation.Without;
import com.dervarex.unweird.components.LoginForm;

@Title("Sign in")
@Without({Sidebar.class, Header.class})
public class LoginScreen extends Screen {

    Image logo     = new Image("/images/logo.png").size(56, 56);
    LoginForm form = new LoginForm()
            .onSignIn(this::signIn)
            .onForgotPassword(() -> form().fail("Try \"hunter2\"."));

    @Override
    protected void onShow() {
        form.reset();   // never keep a password around after logging out
    }

    private LoginForm form() {
        return form;
    }

    private void signIn(LoginForm.Credentials credentials) {
        shared(Header.class).setUser(credentials.username());
        go(HomeScreen.class);
    }
}
