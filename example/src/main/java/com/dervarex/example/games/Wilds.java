package com.dervarex.example.games;

import com.dervarex.example.screens.GamesScreen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.Shortcut;
import com.dervarex.unweird.game.Beach;
import com.dervarex.unweird.game.Block;
import com.dervarex.unweird.game.Bottom;
import com.dervarex.unweird.game.Caves;
import com.dervarex.unweird.game.Creature;
import com.dervarex.unweird.game.Explorer;
import com.dervarex.unweird.game.Game3D;
import com.dervarex.unweird.game.Hills;
import com.dervarex.unweird.game.Liquid;
import com.dervarex.unweird.game.Mountains;
import com.dervarex.unweird.game.Ores;
import com.dervarex.unweird.game.Plant;
import com.dervarex.unweird.game.Plants;
import com.dervarex.unweird.game.Sea;
import com.dervarex.unweird.game.SeeThrough;
import com.dervarex.unweird.game.Surface;
import com.dervarex.unweird.game.Terrain;
import com.dervarex.unweird.game.Trees;
import com.dervarex.unweird.game.Unbreakable;

/** An endless block world: no pictures needed, every block gets a placeholder that fits its name. */
public class Wilds extends Game3D {

    Grass grass = new Grass();
    Dirt dirt = new Dirt();
    Stone stone = new Stone();
    Sand sand = new Sand();
    Log log = new Log();
    Leaves leaves = new Leaves();
    Planks planks = new Planks();
    Glass glass = new Glass();
    Coal coal = new Coal();
    Flower flower = new Flower();
    Water water = new Water();
    Bedrock bedrock = new Bedrock();

    Land land = new Land();
    Me me = new Me();
    Pig pig = new Pig().near(me);
    Pig piglet = new Pig().near(me);
    Sheep sheep = new Sheep().near(me);

    Text hint = new Text("WASD walk · Space jump · F fly · left click break · right click place · Esc twice: back");

    @Shortcut("Escape")
    void back() {
        go(GamesScreen.class);
    }

    class Land extends Terrain {
        Hills hills = new Hills();
        Mountains mountains = new Mountains();
        Surface surface = new Surface(grass, dirt, stone);
        Sea sea = new Sea(water);
        Beach beach = new Beach(sand);
        Caves caves = new Caves();
        Ores ores = new Ores(coal);
        Bottom bottom = new Bottom(bedrock);
        Trees trees = new Trees(log, leaves);
        Plants plants = new Plants(flower);
    }

    class Grass extends Block {}
    class Dirt extends Block {}
    class Stone extends Block {}
    class Sand extends Block {}
    class Log extends Block {}
    @SeeThrough class Leaves extends Block {}
    class Planks extends Block {}
    @SeeThrough class Glass extends Block {}
    class Coal extends Block {}
    @Plant class Flower extends Block {}
    @Liquid class Water extends Block {}
    @Unbreakable class Bedrock extends Block {}

    class Me extends Explorer {}
    class Pig extends Creature {}
    class Sheep extends Creature {}
}
