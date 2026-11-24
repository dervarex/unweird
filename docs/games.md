# Games (`unweird-game`)

```kotlin
implementation("com.github.dervarex.unweird:unweird-game:v0.1.2")
```

Games are classes like screens are. Everything in the game is a field, every kind of thing is a class, and **what a
thing does comes from what it extends**. No game loop, no coordinates math, no file names: pictures and sounds are
found by class name, and anything without a picture gets a placeholder that fits, so a game is playable before
anyone draws anything.

- [2D](#2d): platformers and top-down games
- [3D](#3d): endless block worlds you walk around in, dig and build
- [Sound](#sound)
- [Inside a page](#inside-a-page): a game as a widget

## 2D

```java
public class CoinJump extends Game {
    int coins;

    Hero hero     = new Hero().at(2, 1);
    Meadow meadow = new Meadow();
    Text hud      = new Text(() -> "Coins: " + coins);

    class Hero extends Player {}
    class Ground extends Wall {}
    class Plank extends Platform {}
    class Coin extends Pickup {
        void picked() { coins++; }
    }
    class Spike extends Hazard {}
    class Slime extends Enemy {}
    class Flag extends Goal {}

    class Meadow extends Level {
        Ground floor = new Ground().from(0, 0).to(60, 0);
        Plank ledge  = new Plank().from(8, 4).to(12, 4);
        Coin coins   = new Coin().from(8, 5).to(12, 5);
        Spike spikes = new Spike().from(20, 1).to(21, 1);
        Slime slime  = new Slime().at(30, 1);
        Flag flag    = new Flag().at(58, 1);
    }
}
```

```java
Unweird.start(CoinJump.class);   // a Game is a Screen: go(CoinJump.class) works too
```

Arrow keys run, Space jumps (hold it for a higher jump). Coins disappear when touched and count up, spikes start the
level over, jumping on the slime defeats it, and the flag goes to the next level.

### What things do

| Extends | Does |
|---|---|
| `Player` | You. Arrows or A/D run, Space/Up/W jump, X or Ctrl shoots (with `@Shoots`). The camera follows it. |
| `Wall` | Solid. `from(..).to(..)` makes one long wall with the picture repeated. |
| `Platform` | A wall you can jump through from below and stand on. |
| `Pickup` | Disappears when the player touches it, calls `picked()`. |
| `Hazard` | Hurts the player: the level starts over (or your `hurt()` decides). |
| `Enemy` | Walks back and forth, turns at walls and edges. Hurts from the side, defeated by jumping on it or a `Shot`. |
| `Shot` | Flies straight until it hits a wall or an enemy. `@Shoots(Fireball.class)` on the player fires them. |
| `Goal` | The end of the level. After the last level your game's `won()` runs. |
| `Sprite` | Scenery: drawn, nothing happens. |

Tune them with annotations: `@Speed(9)` (tiles per second), `@Jump(5)` (tiles), `@Shoots(Fireball.class)`.

### Your own rules

Add methods with these names to any class; they need no `@Override` or `public`:

| Method | On | Runs |
|---|---|---|
| `void touch(Spike spike)` | any sprite | it starts touching a `Spike` (any class, also walls) |
| `void tick()` | sprite, level, game | every frame |
| `void clicked()` | sprite | it was clicked with the mouse |
| `void picked()` | `Pickup` | the player collected it |
| `void hurt()` | `Player` | a hazard or enemy got it; without this method the level starts over |
| `void defeated()` | `Enemy` | it was jumped on or shot |
| `void started()` | level, game | a level begins |
| `void won()` | game | the last level is done; without it: "You won!" and back to the first level |

Sprites are inner classes, so they see the game's fields: `coins++` just works. For more, a sprite has
`remove()`, `spawn(new Coin().at(x, y))`, `restart()`, `nextLevel()`, `held("Space")`, `velocity(x, y)`,
`distanceTo(other)`, `touches(other)` and getters for position, size and speed.

```java
class Hero extends Player {
    int lives = 3;

    void hurt() {
        lives--;
        if (lives == 0) restart();
    }

    void touch(Spring spring) {
        velocity(getVelocityX(), 25);
    }
}
```

### Where things are

Positions are in **tiles on the world's grid**: x to the right, y up, `at(0, 0)` is the bottom-left tile. The
camera moves over the world; the coordinates never change with it or with the window. `from(x, y).to(x, y)` fills
an area (corners included). A thing's size comes from its picture (a 16 × 32 picture is 1 × 2 tiles); `size(w, h)`
overrides it.

### Levels

Each `Level` subclass is one level, its fields are what's in it. List the levels as fields of the game in playing
order. A level starts fresh whenever it's played, so coins come back after a restart. Things declared on the game
itself (usually the player) go from level to level and are put back at their start. A game without levels is
one level made of its own fields.

### Pictures

| File | Used for |
|---|---|
| `hero.png` | a `Hero`'s picture (class `CoinBox` → `coin_box.png`) |
| `hero_1.png`, `hero_2.png`, … | an animation instead (8 frames per second) |
| `hero_walk.png` / `hero_walk_1.png`, … | while moving |
| `hero_jump.png`, `hero_fall.png` | in the air, going up / down |
| `background.png`, `meadow_background.png` | behind everything, scrolling slowly |

Draw sprites facing right; they're mirrored when facing left. Files are looked up next to the game class, at the
resources root and in `images/`, `sprites/`, `textures/`. A subclass without pictures uses its superclass's.

Without a picture, the placeholder fits the kind and the name: a `Brick` wall is bricks, `Grass` and `Ground` are
dirt with a grassy top, a `Gem` pickup is a diamond, `Lava` is a glowing pool, a `Door` goal is a door.

### The screen

- `@Resolution(width = 320, height = 180, tile = 16)` (the default): how much of the world fits on screen. The game
  is scaled up by whole numbers so pixels stay sharp; bigger windows see a little more.
- `@TopDown`: seen from above. No gravity, the player walks in all directions, enemies come after it.
- `@Sky(0x1b1f3a)`: the background color, on the game or a level.
- Widgets declared on the game (`Text`, `Button`) are drawn on top, along the top edge.

## 3D

```java
public class MyWorld extends Game3D {
    Grass grass = new Grass();
    Dirt dirt = new Dirt();
    Stone stone = new Stone();
    Log log = new Log();
    Leaves leaves = new Leaves();
    Water water = new Water();

    Land land = new Land();
    Me me = new Me();
    Pig pig = new Pig().near(me);

    class Land extends Terrain {
        Hills hills = new Hills();
        Surface surface = new Surface(grass, dirt, stone);
        Sea sea = new Sea(water);
        Caves caves = new Caves();
        Trees trees = new Trees(log, leaves);
    }

    class Grass extends Block {}
    class Dirt extends Block {}
    class Stone extends Block {}
    class Log extends Block {}
    @SeeThrough class Leaves extends Block {}
    @Liquid class Water extends Block {}

    class Me extends Explorer {}
    class Pig extends Creature {}
}
```

An endless world with hills, a sea, caves and trees, a pig nearby, and you in the middle. Click into the game to
look around with the mouse.

### Blocks

One class per kind of block, one field per class. The fields are also what you can build with, in that order.

| Annotation | Block |
|---|---|
| (none) | solid, hides what's behind it |
| `@SeeThrough` | solid with see-through pixels: leaves, glass |
| `@Liquid` | half see-through, you swim in it: water, lava |
| `@Plant` | two crossed pictures you walk through: flowers, tall grass |
| `@Unbreakable` | can't be broken: the bottom of the world |

Pictures: `grass_top.png`, `grass_side.png`, `grass_bottom.png`, or `grass.png` for all sides; 16 × 16 works well.
Without pictures, placeholders follow the name: grass, dirt, stone, cobble, sand, gravel, snow, ice, log, leaves,
planks, bricks, glass, water, lava, bedrock, coal, iron, gold, diamond, … anything else gets a color of its own.

Optional methods on a block class: `broken()`, `placed()`.

### Terrain

A `Terrain` is made of parts, run in the order they're declared. Leave out what you don't want, tune with short
calls:

| Part | Does |
|---|---|
| `Hills` | rolling hills; `.height(40)`, `.rise(12)`, `.width(90)` |
| `Flat` | flat ground, `new Flat(32)` |
| `Mountains` | tall ridges in some regions (after `Hills`); `.height(45)` |
| `Surface(top, under, rest)` | grass on dirt on stone; `.depth(3)` |
| `Sea(water)` | fills everything below `.level(34)` |
| `Beach(sand)` | sand near the water's edge |
| `Caves` | winding tunnels; `.size(1.5)` |
| `Ores(coal)` | lumps deep in the ground; `.rare()`, `.common()` |
| `Bottom(bedrock)` | a floor at y = 0 |
| `Trees(log, leaves)` | trees on dry land; `.dense()`, `.sparse()` |
| `Plants(flower, grass)` | plants on dry land; `.dense()` |

For something of your own, subclass a part or `Part` and override `height(x, z, height)`,
`block(x, y, z, depth, block)` or `decorate(x, z, top, area)`; `noise(...)` and `chance(...)` help.

```java
class Pillars extends Part {
    Block block(int x, int y, int z, int depth, Block block) {
        return x % 32 == 0 && z % 32 == 0 && depth > -20 ? stone : block;
    }
}
```

Without a terrain the world is empty: build it with `place(x, y, z, block)` in `started()`.

### Things

| Extends | Does |
|---|---|
| `Explorer` | You, in first person (see below). |
| `Creature` | Wanders around, hops up single blocks, swims. `@Speed(2)`. |
| `Thing` | Stays where it's put. |

Place them with `.at(x, z)` (on the ground), `.at(x, y, z)` or `.near(other)`. A thing looks like `pig.obj`
(textured with `pig.png`), otherwise like a placeholder: creatures are four-legged animals colored by name (pig,
cow, sheep, chicken, wolf, fox, …), other things are boxes. Optional methods: `tick()`, `clicked()`,
`touch(Pig pig)`; things have `remove()`, `spawn(...)`, `moveTo(...)`, `distanceTo(...)` and position getters.

### The explorer

| Keys | |
|---|---|
| W A S D or arrows | walk; Ctrl runs |
| Space | jump; swim up in water |
| F | fly on/off; flying: Space up, Shift down |
| left click | break the block you look at (or click a thing) |
| right click | place the chosen block |
| 1–9, mouse wheel | choose a block from the bar at the bottom |
| Escape | let go of the mouse |

`@Speed` and `@Jump` tune it; `broke(Block block)` and `placed(Block block)` methods react.

### The world

- `@ViewDistance(8)`: how far you see, in chunks of 16 blocks (default 6).
- `@Seed(42)`: the same world every time. Without it, every new world is different.
- `@Saved`: keeps the world between runs (every changed block, where you were, the seed) in
  `~/.local/share/unweird-game/<game class>/`. Delete the folder for a new world.
- `@Sky(0x9ccfff)`: sky and fog color.
- `@DayLength(10)`: days and nights, one day in 10 minutes; `timeOfDay()` and `isNight()` tell where you are.
- On the game: `block(x, y, z)`, `place(x, y, z, block)` (null removes), `spawn(thing)`, `explorer()`, and
  optional `tick()` and `started()` methods.

## Sound

```java
Sound explosion = new Sound();     // plays explosion.ogg (or .wav)
...
explosion.play();
```

A `Sound` is named after its field; it works on games, levels and things. `loop()` and `stop()` for music,
`volume(0.5f)` for loudness. Files are found like pictures, also in `sounds/`, `audio/` and `music/`.

The engine plays some sounds by itself, from a file if there is one, otherwise a built-in effect:

| When | File (first that exists) |
|---|---|
| the player jumps | `hero_jump`, `jump` |
| a pickup is collected | `coin`, `pickup` |
| the player is hurt | `hero_hurt`, `hurt` |
| a goal is reached | `flag`, `win` |
| an enemy is defeated | `slime_defeated`, `stomp` |
| a shot is fired | `fireball`, `shoot` |
| a block is broken / placed | `stone_break`, `break` / `stone_place`, `place` |

Music: a file named after the level (`meadow.ogg`), the game (`coin_jump.ogg`) or `music.ogg` loops while it plays.
Without a sound device the game simply runs silently.

## Inside a page

A game is a screen, but it can also be a widget among others:

```java
public class Arcade extends Screen {
    Text heading  = new Text("Today's level");
    GameView play = new GameView(CoinJump.class).height(360);
}
```

The game's own widgets are drawn on top of it; `play.game()` gives you the game object, e.g. to read its score.
