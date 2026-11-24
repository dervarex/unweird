package com.dervarex.example.screens;

import com.dervarex.example.games.CoinJump;
import com.dervarex.example.games.Wilds;
import com.dervarex.unweird.Button;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Title;
import com.dervarex.unweird.game.GameView;

@Title("Games")
public class GamesScreen extends Screen {

    Text heading = new Text("Games");
    Text subtitle = new Text("Made with unweird-game, each one a single short class. Escape goes back.");

    Button coinJump = new Button("Coin Jump (2D)").onClick(() -> go(CoinJump.class));
    Button wilds = new Button("Wilds (3D)").onClick(() -> go(Wilds.class));

    Text embedded = new Text("A game can also sit inside a page (arrow keys and Space):");
    GameView preview = new GameView(CoinJump.class).height(300);
}
