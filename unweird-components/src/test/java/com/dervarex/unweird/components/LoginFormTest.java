package com.dervarex.unweird.components;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginFormTest {

    @Test
    void emptyFieldsAreCaughtBeforeHandlersRun() {
        List<LoginForm.Credentials> seen = new ArrayList<>();
        LoginForm form = new LoginForm().onSignIn(seen::add);
        form.submit();
        assertEquals("Please enter a username and password.", form.error());
        form.getUsername().value("alex");
        form.submit();
        assertEquals("Please enter a password.", form.error());
        assertTrue(seen.isEmpty());
    }

    @Test
    void validInputReachesHandlersAndClearsTheError() {
        List<LoginForm.Credentials> seen = new ArrayList<>();
        LoginForm form = new LoginForm().onSignIn(seen::add);
        form.fail("old error");
        form.getUsername().value("  alex ");
        form.getPassword().value("secret");
        form.getRemember().checked(true);
        form.submit();
        assertEquals(List.of(new LoginForm.Credentials("alex", "secret", true)), seen);
        assertEquals("", form.error());
    }

    @Test
    void credentialsNeverPrintThePassword() {
        assertFalse(new LoginForm.Credentials("alex", "secret", false).toString().contains("secret"));
    }

    @Test
    void resetKeepsTheUsername() {
        LoginForm form = new LoginForm();
        form.getUsername().value("alex");
        form.getPassword().value("secret");
        form.fail("nope");
        form.reset();
        assertEquals("alex", form.getUsername().value());
        assertEquals("", form.getPassword().value());
        assertEquals("", form.error());
    }
}
