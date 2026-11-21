package com.dervarex.example.components;

import com.dervarex.example.screens.HomeScreen;
import com.dervarex.example.screens.SettingsScreen;
import com.dervarex.unweird.Button;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Side;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Default;
import lombok.Getter;

@Default(Side.LEFT)
public class Sidebar extends Component {

    Text brand = new Text("unweird");

    @Getter private final Button home     = new Button("Home").onClick(() -> go(HomeScreen.class));
    @Getter private final Button settings = new Button("Settings").onClick(() -> go(SettingsScreen.class));
}
