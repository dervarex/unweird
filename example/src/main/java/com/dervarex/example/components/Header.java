package com.dervarex.example.components;

import com.dervarex.example.screens.LoginScreen;
import com.dervarex.unweird.Button;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Side;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Default;
import com.dervarex.unweird.annotation.State;
import lombok.Getter;
import lombok.Setter;

@Default(Side.TOP)
public class Header extends Component {

    /** Who is signed in. Set by the login screen through shared(Header.class). */
    @State @Getter @Setter private String user = "";

    Text greeting = new Text(() -> "Hi, " + user);

    @Getter private final Button logout = new Button("Log out")
            .tooltip("Back to the sign-in screen. Ctrl+Q quits the app.")
            .onClick(() -> go(LoginScreen.class));
}
