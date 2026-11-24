package com.dervarex.example.screens;

import com.dervarex.example.games.CoinJump;
import com.dervarex.unweird.Button;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Title;

@Title("Games")
public class GamesScreen extends Screen {

    Text heading = new Text("Games");
    Text subtitle = new Text("Made with unweird-game. Each one is a single short class.");

    Button coinJump = new Button("Coin Jump (2D)").onClick(() -> go(CoinJump.class));
}
