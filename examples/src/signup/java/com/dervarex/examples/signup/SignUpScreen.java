package com.dervarex.examples.signup;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Checkbox;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Toast;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.style.Color;

/**
 * Validation without a framework: each hint is a live text that is "" while there's nothing to say,
 * and empty texts take no space. The button is enabled once everything checks out.
 */
@Title("Create account")
public class SignUpScreen extends Screen {

    private static final Color ERROR = Color.hex("#f87171");

    Text heading      = new Text("Create an account");
    Text subtitle     = new Text("Takes ten seconds.");

    Field email       = new Field("Email");
    Text emailHint    = new Text(this::emailProblem).color(ERROR).gap(4);

    Field password    = new Field("Password").masked();
    Text passwordHint = new Text(this::passwordProblem).color(ERROR).gap(4);

    Field repeat      = new Field("Repeat password").masked().onSubmit(this::submit);
    Text repeatHint   = new Text(this::repeatProblem).color(ERROR).gap(4);

    Checkbox terms    = new Checkbox("I accept the terms");
    Button create     = new Button("Create account").enabledIf(this::valid).onClick(this::submit);

    private String emailProblem() {
        String value = email.value();
        return value.isEmpty() || value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+") ? "" : "That doesn't look like an email address.";
    }

    private String passwordProblem() {
        int length = password.value().length();
        return length == 0 || length >= 8 ? "" : (8 - length) + " more characters, please.";
    }

    private String repeatProblem() {
        return repeat.value().isEmpty() || repeat.value().equals(password.value()) ? "" : "Doesn't match.";
    }

    private boolean valid() {
        return !email.value().isEmpty() && emailProblem().isEmpty()
                && password.value().length() >= 8
                && repeat.value().equals(password.value())
                && terms.checked();
    }

    private void submit() {
        if (valid()) {
            toast(Toast.success("Welcome aboard, " + email.value()));
        }
    }
}
