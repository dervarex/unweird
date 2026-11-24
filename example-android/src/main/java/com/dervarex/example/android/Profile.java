package com.dervarex.example.android;

import com.dervarex.unweird.Component;
import com.dervarex.unweird.Side;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Default;
import com.dervarex.unweird.annotation.State;

/** A bar on top of every screen except the welcome one. */
@Default(Side.TOP)
public class Profile extends Component {

    @State String name = "";

    Text greeting = new Text(() -> "Hi, " + name);
}
