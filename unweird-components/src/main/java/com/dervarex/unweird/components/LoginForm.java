package com.dervarex.unweird.components;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Checkbox;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.State;
import lombok.Getter;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A ready-made sign-in form: heading, username, password, "Remember me", an optional
 * "Forgot password?" link, a sign-in button and an error line.
 *
 * <pre>{@code
 * LoginForm form = new LoginForm().onSignIn(this::signIn);
 *
 * private void signIn(LoginForm.Credentials credentials) {
 *     if (!accounts.check(credentials)) form.fail("Wrong username or password.");
 *     else go(HomeScreen.class);
 * }
 * }</pre>
 *
 * Empty fields are caught before {@link #onSignIn} handlers run. Every part has a getter, e.g.
 * {@code form.getHeading().text("Welcome to Foo")}.
 */
public class LoginForm extends Component {

    /** What the user entered. */
    public record Credentials(String username, String password, boolean remember) {

        @Override
        public String toString() {
            return "Credentials[username=" + username + ", password=***, remember=" + remember + "]";
        }
    }

    @State private String error = "";
    private final List<Consumer<Credentials>> signInHandlers = new CopyOnWriteArrayList<>();
    private final List<Runnable> forgotHandlers = new CopyOnWriteArrayList<>();

    @Getter private final Text heading = new Text("Welcome back");
    @Getter private final Text subtitle = new Text("Sign in to continue");
    @Getter private final Field username = new Field("Username");
    @Getter private final Field password = new Field("Password").masked().onSubmit(this::submit);

    // one row: "Remember me" lined up with the fields' left edge, the link with their right edge
    @Getter private final Button forgot = new Button("Forgot password?").ghost()
            .alignRight(password).gap(4)
            .visibleIf(() -> !forgotHandlers.isEmpty())
            .onClick(this::forgot);
    @Getter private final Checkbox remember = new Checkbox("Remember me").leftOf(forgot).alignLeft(password);

    @Getter private final Button signIn = new Button("Sign in").onClick(this::submit);
    @Getter private final Text message = new Text(() -> error).muted();

    /** Runs {@code handler} with the entered credentials when the user signs in (button or Enter). */
    public LoginForm onSignIn(Consumer<Credentials> handler) {
        signInHandlers.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    /** Shows a "Forgot password?" link that runs {@code handler}. Without one, there's no link. */
    public LoginForm onForgotPassword(Runnable handler) {
        forgotHandlers.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    /** Shows {@code message} under the form, e.g. after a wrong password. */
    public void fail(String message) {
        error = Objects.requireNonNull(message, "message");
    }

    /** The current error line ("" when there is none). */
    public String error() {
        return error;
    }

    /** Clears the password and the error line; the username stays. Handy in a screen's {@code onShow()}. */
    public void reset() {
        password.value("");
        error = "";
    }

    void submit() {
        String name = username.value().strip();
        if (name.isEmpty() || password.value().isEmpty()) {
            error = name.isEmpty() && password.value().isEmpty() ? "Please enter a username and password."
                    : name.isEmpty() ? "Please enter a username." : "Please enter a password.";
            return;
        }
        error = "";
        Credentials credentials = new Credentials(name, password.value(), remember.checked());
        for (Consumer<Credentials> handler : signInHandlers) {
            handler.accept(credentials);
        }
    }

    private void forgot() {
        for (Runnable handler : forgotHandlers) {
            handler.run();
        }
    }
}
