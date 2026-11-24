package com.dervarex.example.games;

import com.dervarex.example.screens.GamesScreen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Shortcut;
import com.dervarex.unweird.game.Enemy;
import com.dervarex.unweird.game.Game;
import com.dervarex.unweird.game.Goal;
import com.dervarex.unweird.game.Hazard;
import com.dervarex.unweird.game.Level;
import com.dervarex.unweird.game.Pickup;
import com.dervarex.unweird.game.Platform;
import com.dervarex.unweird.game.Player;
import com.dervarex.unweird.game.Wall;

/** A small platformer: no pictures needed, every sprite gets a placeholder that fits its name. */
public class CoinJump extends Game {

    int coins;

    Hero hero = new Hero().at(2, 1);
    Meadow meadow = new Meadow();
    Hills hills = new Hills();

    Text hud = new Text(() -> "Coins: " + coins);

    @Shortcut("Escape")
    void back() {
        go(GamesScreen.class);
    }

    class Hero extends Player {}
    class Ground extends Wall {}
    class Brick extends Wall {}
    class Plank extends Platform {}
    class Coin extends Pickup {
        void picked() { coins++; }
    }
    class Spike extends Hazard {}
    class Slime extends Enemy {}
    class Flag extends Goal {}

    class Meadow extends Level {
        Ground floor  = new Ground().from(0, 0).to(30, 0);
        Ground floor2 = new Ground().from(34, 0).to(60, 0);
        Brick step    = new Brick().from(12, 1).to(13, 2);
        Plank ledge   = new Plank().from(17, 4).to(21, 4);
        Coin row      = new Coin().from(17, 5).to(21, 5);
        Coin gap      = new Coin().from(31, 3).to(33, 3);
        Spike spikes  = new Spike().from(40, 1).to(41, 1);
        Slime slime   = new Slime().at(48, 1);
        Flag flag     = new Flag().at(58, 1);
    }

    class Hills extends Level {
        Ground floor   = new Ground().from(0, 0).to(80, 0);
        Brick wall     = new Brick().from(10, 1).to(10, 3);
        Plank up       = new Plank().from(13, 3).to(16, 3);
        Plank higher   = new Plank().from(19, 6).to(23, 6);
        Coin top       = new Coin().from(19, 7).to(23, 7);
        Slime one      = new Slime().at(30, 1);
        Slime two      = new Slime().at(42, 1);
        Spike pit      = new Spike().from(50, 1).to(52, 1);
        Plank bridge   = new Plank().from(50, 4).to(52, 4);
        Coin bonus     = new Coin().from(60, 1).to(66, 1);
        Flag flag      = new Flag().at(78, 1);
    }
}
