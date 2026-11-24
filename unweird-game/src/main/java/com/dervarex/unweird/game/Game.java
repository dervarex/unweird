package com.dervarex.unweird.game;

import com.dervarex.unweird.Canvas;
import com.dervarex.unweird.Cursor;
import com.dervarex.unweird.Pointer;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.annotation.Without;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A 2D game. It's a {@link Screen}: start it with {@code Unweird.start(MyGame.class)} or go to it from a menu with
 * {@code go(MyGame.class)}. Widgets declared on it ({@code Text}, {@code Button}) are drawn on top of the game.
 *
 * <pre>{@code
 * public class CoinJump extends Game {
 *     int coins;
 *
 *     Hero hero = new Hero().at(2, 1);
 *     Meadow meadow = new Meadow();
 *     Text hud = new Text(() -> "Coins: " + coins);
 *
 *     class Hero extends Player {}
 *     class Ground extends Wall {}
 *     class Coin extends Pickup {
 *         void picked() { coins++; }
 *     }
 *     class Flag extends Goal {}
 *
 *     class Meadow extends Level {
 *         Ground floor = new Ground().from(0, 0).to(40, 0);
 *         Coin coins   = new Coin().from(6, 3).to(10, 3);
 *         Flag flag    = new Flag().at(38, 1);
 *     }
 * }
 * }</pre>
 *
 * Levels are the {@link Level} fields, played in declaration order. Without levels, the sprites declared on the game
 * are the one level.
 *
 * <p>Optional methods on your game: {@code tick()} every frame, {@code won()} after the last level (default: a
 * "You won!" message and back to the first level). Annotations: {@link TopDown @TopDown},
 * {@link Resolution @Resolution}, {@link Sky @Sky}. Music: a sound file named after the level, the game
 * ({@code coin_jump.ogg}) or {@code music.ogg} loops while it plays.
 */
@Without
public abstract class Game extends Screen {

    private static final float MAX_FRAME = 1f / 20;

    private final Batch batch = new Batch();
    private World world;
    private final List<Field> levelFields = new ArrayList<>();
    private int levelIndex;
    private Level level;
    /** Sprites declared on the game itself, carried through all levels. */
    private List<Sprite> carried;
    private double lastTime = -1;
    private Sound music;
    private float pixelRatio = 1;
    private float pixelsPerTile = 16;
    private int surfaceHeight;
    private int cameraPixelX;
    private int cameraPixelY;
    /** Top-left of the area the game was last drawn in, logical pixels. */
    private float areaX;
    private float areaY;

    protected Game() {
        whileShown(this::shown, this::hidden);
    }

    /** On screen (as a screen or in a {@link GameView}). */
    void shown() {
        lastTime = -1;
    }

    /** Off screen: the music stops. */
    void hidden() {
        if (music != null) {
            music.stop();
        }
    }

    @Override
    protected boolean overlay() {
        return true;
    }

    @Override
    protected String title() {
        return Assets.words(getClass().getSimpleName());
    }

    // ---- for your game code ----

    /** Starts the current level over. */
    protected final void restart() {
        if (world != null) {
            world.requestRestart();
        }
    }

    /** Goes on to the next level (or to {@code won()} after the last). */
    protected final void nextLevel() {
        if (world != null) {
            world.requestNextLevel();
        }
    }

    /** Starts over from the first level. */
    protected final void restartGame() {
        loadLevel(0);
    }

    /** The level being played, or null if the game has none. */
    protected final Level level() {
        return level;
    }

    /** Puts a sprite into the running game. */
    protected final void spawn(Sprite sprite) {
        world().spawn(sprite);
    }

    /** The {@link Player} (the first one), or null. */
    protected final Player player() {
        return world == null ? null : world.player();
    }

    // ---- running ----

    private World world() {
        if (world == null) {
            start();
        }
        return world;
    }

    private void start() {
        Resolution resolution = getClass().getAnnotation(Resolution.class);
        int tile = resolution != null ? resolution.tile() : 16;
        world = new World(getClass().isAnnotationPresent(TopDown.class), tile, new World.Factory() {
            @Override
            public <T> T create(Class<T> type) {
                return Members.create(type, Game.this, level);
            }
        });
        for (Field field : Members.fields(this)) {
            if (Level.class.isAssignableFrom(field.getType())) {
                levelFields.add(field);
            }
        }
        Members.nameSounds(this);
        carried = Members.valuesOf(this, Sprite.class);
        for (Sprite sprite : carried) {
            sprite.start = sprite.copy();
        }
        loadLevel(0);
    }

    private void loadLevel(int index) {
        levelIndex = index;
        List<Sprite> things = new ArrayList<>();
        for (Sprite sprite : carried) {
            sprite.reset();
            things.add(sprite);
        }
        level = null;
        if (!levelFields.isEmpty()) {
            Field field = levelFields.get(index);
            Level current = (Level) Members.get(field, this);
            boolean fresh = current != null && current.used;
            if (current == null || fresh) {
                @SuppressWarnings("unchecked")
                Class<? extends Level> type = (Class<? extends Level>) (current != null ? current.getClass() : field.getType());
                current = Members.create(type, this);
                try {
                    field.set(this, current); // so game code sees the level being played
                } catch (IllegalAccessException ignored) {
                    // final field: the old instance stays visible, the new one plays
                }
            }
            current.used = true;
            level = current;
            Members.nameSounds(level);
            things.addAll(Members.valuesOf(level, Sprite.class));
        }
        world.load(things);
        playMusic();
        if (level != null) {
            Hooks.of(level.getClass()).call(level, "started");
        }
        Hooks.of(getClass()).call(this, "started");
    }

    private void playMusic() {
        List<String> names = new ArrayList<>();
        if (level != null) {
            names.add(Assets.snake(level.getClass().getSimpleName()));
        }
        names.add(Assets.snake(getClass().getSimpleName()));
        names.add("music");
        for (String name : names) {
            if (Assets.find(getClass(), name, "ogg", "wav") != null) {
                if (music != null && name.equals(music.name)) {
                    music.loop(); // same music: keeps playing
                    return;
                }
                if (music != null) {
                    music.stop();
                }
                music = new Sound(name, getClass()).volume(0.6f);
                music.loop();
                return;
            }
        }
        if (music != null) {
            music.stop();
            music = null;
        }
    }

    private void afterStep() {
        World.Pending pending = world.pending;
        world.pending = World.Pending.NONE;
        switch (pending) {
            case RESTART -> loadLevel(levelIndex);
            case NEXT -> {
                if (levelIndex + 1 < Math.max(1, levelFields.size())) {
                    loadLevel(levelIndex + 1);
                } else if (!Hooks.of(getClass()).call(this, "won")) {
                    toast("You won!");
                    loadLevel(0);
                }
            }
            case NONE -> {
            }
        }
    }

    // ---- drawing ----

    @Override
    protected void draw(Canvas canvas) {
        render(canvas, getX(), getY(), getWidth(), getHeight());
        super.draw(canvas); // the widgets on top
        requestFrame();
    }

    /** One frame: moves everything, then draws the world into the given area. */
    void render(Canvas canvas, float x, float y, float width, float height) {
        World running = world();
        double now = canvas.time();
        float dt = lastTime < 0 ? 0 : (float) Math.min(MAX_FRAME, now - lastTime);
        lastTime = now;
        pixelRatio = canvas.getPixelRatio();
        areaX = x;
        areaY = y;
        layoutView(Math.round(width * pixelRatio), Math.round(height * pixelRatio));
        if (dt > 0) {
            running.update(dt);
            Hooks.of(getClass()).call(this, "tick");
            if (level != null) {
                Hooks.of(level.getClass()).call(level, "tick");
            }
            afterStep();
        } else {
            running.followCamera(0);
        }
        canvas.gl(this, x, y, width, height, 0, this::render);
    }

    /** Aspect ratio of the resolution, height / width. */
    float aspect() {
        Resolution resolution = getClass().getAnnotation(Resolution.class);
        return resolution != null ? (float) resolution.height() / resolution.width() : 180f / 320;
    }

    /** Fits the resolution into the surface: whole-number scaling when it's bigger, so pixels stay square. */
    private void layoutView(int width, int height) {
        Resolution resolution = getClass().getAnnotation(Resolution.class);
        float virtualWidth = resolution != null ? resolution.width() : 320;
        float virtualHeight = resolution != null ? resolution.height() : 180;
        float fit = Math.min(width / virtualWidth, height / virtualHeight);
        float scale = fit >= 1 ? (float) Math.floor(fit) : fit;
        pixelsPerTile = Math.max(1e-3f, world.tile * scale);
        world.viewWidth = width / pixelsPerTile;
        world.viewHeight = height / pixelsPerTile;
    }

    private void render(int width, int height) {
        surfaceHeight = height;
        float ppt = pixelsPerTile;
        com.dervarex.unweird.style.Color sky = skyColor();
        batch.begin(width, height);
        batch.clear(sky.r(), sky.g(), sky.b());
        cameraPixelX = Math.round(world.cameraX * ppt);
        cameraPixelY = Math.round(world.cameraY * ppt);
        drawBackground(width, height);

        List<Sprite> order = new ArrayList<>(world.sprites);
        order.sort(Comparator.comparingInt(Sprite::layer));
        for (Sprite sprite : order) {
            if (sprite.removed) {
                continue;
            }
            int left = Math.round(sprite.x * ppt);
            int bottom = Math.round(sprite.y * ppt);
            int w = Math.round((sprite.x + sprite.width) * ppt) - left;
            int h = Math.round((sprite.y + sprite.height) * ppt) - bottom;
            int x = left - cameraPixelX;
            int y = bottom - cameraPixelY;
            if (x > width || y > height || x + w < 0 || y + h < 0) {
                continue;
            }
            Art art = Art.of(sprite.getClass(), world.tile);
            BufferedImage frame = art.frame(state(sprite), sprite.animation);
            float u = 1;
            float v = 1;
            if (sprite instanceof Wall) {
                float[] natural = world.naturalSize(sprite.getClass());
                u = sprite.width / natural[0];
                v = sprite.height / natural[1];
            }
            float alpha = sprite instanceof Player player && player.invulnerable > 0 && (int) (world.time * 16) % 2 == 0 ? 0.35f : 1;
            batch.image(frame, x, y, w, h, u, v, sprite.facing < 0, alpha);
        }
        batch.end();
    }

    private void drawBackground(int width, int height) {
        BufferedImage background = background();
        if (background == null) {
            return;
        }
        float scale = (float) height / background.getHeight();
        float w = background.getWidth() * scale;
        float offset = -(cameraPixelX * 0.3f) % w;
        if (offset > 0) {
            offset -= w;
        }
        float repeats = (width - offset) / w + 1;
        batch.image(background, offset, 0, w * (float) Math.ceil(repeats), height, (float) Math.ceil(repeats), 1, false, 1);
    }

    private BufferedImage cachedBackground;
    private String backgroundFor;

    private BufferedImage background() {
        String key = level != null ? level.getClass().getName() : getClass().getName();
        if (!key.equals(backgroundFor)) {
            backgroundFor = key;
            cachedBackground = null;
            List<String> names = new ArrayList<>();
            if (level != null) {
                names.add(Assets.snake(level.getClass().getSimpleName()) + "_background");
            }
            names.add(Assets.snake(getClass().getSimpleName()) + "_background");
            names.add("background");
            for (String name : names) {
                URL url = Assets.find(getClass(), name, "png", "jpg");
                if (url != null) {
                    cachedBackground = Art.decode(url);
                    break;
                }
            }
        }
        return cachedBackground;
    }

    private com.dervarex.unweird.style.Color skyColor() {
        Sky sky = level != null ? level.getClass().getAnnotation(Sky.class) : null;
        if (sky == null) {
            sky = getClass().getAnnotation(Sky.class);
        }
        int rgb = sky != null ? sky.value() : world.topDown ? 0x4f8a3c : 0x8fd3ff;
        return new com.dervarex.unweird.style.Color(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1);
    }

    private Art.State state(Sprite sprite) {
        if (sprite.falls() && !sprite.onGround) {
            return sprite.velocityY > 0 ? Art.State.JUMP : Art.State.FALL;
        }
        boolean moving = Math.abs(sprite.velocityX) > 0.1f || world.topDown && Math.abs(sprite.velocityY) > 0.1f;
        return moving ? Art.State.WALK : Art.State.IDLE;
    }

    // ---- mouse ----

    @Override
    protected boolean clickable() {
        return true;
    }

    @Override
    protected Cursor cursor() {
        return Cursor.ARROW;
    }

    @Override
    protected void onPress(Pointer pointer) {
        press(pointer);
    }

    /** A click into the game: tells the sprite under the mouse. */
    void press(Pointer pointer) {
        if (world == null) {
            return;
        }
        float px = (pointer.x() - areaX) * pixelRatio + cameraPixelX;
        float py = (surfaceHeight - (pointer.y() - areaY) * pixelRatio) + cameraPixelY;
        Sprite hit = world.spriteAt(px / pixelsPerTile, py / pixelsPerTile);
        if (hit != null) {
            Hooks.of(hit.getClass()).call(hit, "clicked");
        }
    }
}
