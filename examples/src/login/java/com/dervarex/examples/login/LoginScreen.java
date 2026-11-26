package com.dervarex.examples.login;

import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Toast;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.annotation.Without;
import com.dervarex.unweird.components.LoginForm;

@Title("Sign in")
@Without(Header.class)
public class LoginScreen extends Screen {

    LoginForm form = new LoginForm()
            .onSignIn(this::signIn)
            .onForgotPassword(() -> toast(Toast.info("Psst: it's hunter2")));

    @Override
    protected void onShow() {
        form.reset();
    }

    private void signIn(LoginForm.Credentials credentials) {
        if (!credentials.password().equals("hunter2")) {
            form.fail("Wrong password.");
            return;
        }
        shared(Header.class).setUser(credentials.username());
        go(HomeScreen.class);
    }
}
