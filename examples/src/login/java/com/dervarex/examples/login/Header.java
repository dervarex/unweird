package com.dervarex.examples.login;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Side;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Default;
import com.dervarex.unweird.annotation.State;
import lombok.Getter;
import lombok.Setter;

/** On every screen except the ones that say @Without(Header.class). Texts go left, buttons right. */
@Default(Side.TOP)
public class Header extends Component {

    @State @Getter @Setter private String user = "";

    Text greeting = new Text(() -> "Signed in as " + user);
    Button logout = new Button("Log out").onClick(() -> go(LoginScreen.class));
}
