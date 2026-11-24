package com.dervarex.unweird.game;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * Anything in a 2D {@link Game}. What it does comes from what it extends: {@link Player}, {@link Wall},
 * {@link Platform}, {@link Pickup}, {@link Hazard}, {@link Goal}, {@link Enemy}, {@link Shot}. A plain {@code Sprite}
 * is scenery: drawn, but nothing touches it.
 *
 * <p>Its picture is found by class name: {@code class Coin extends Pickup {}} shows {@code coin.png}. Animations are
 * numbered frames ({@code coin_1.png}, {@code coin_2.png}, …), per state: {@code hero_walk_1.png}…,
 * {@code hero_jump.png}, {@code hero_fall.png}. Draw sprites facing right; they're flipped when facing left.
 * Without a picture you get a placeholder that fits what it is.
 *
 * <p>Positions are in tiles on the world's grid (not the screen): x to the right, y up, {@code at(0, 0)} is the
 * bottom-left tile. The camera moves; the coordinates don't.
 *
 * <pre>{@code
 * Hero hero   = new Hero().at(2, 1);
 * Ground floor = new Ground().from(0, 0).to(40, 0);   // a row of ground
 * Coin coins  = new Coin().from(5, 3).to(9, 3);       // five coins
 * }</pre>
 *
 * <p>For your own rules, add methods with these names (any visibility, no {@code @Override} needed):
 * <ul>
 *   <li>{@code void touch(Spike spike)}: it started touching a Spike (any class works, also walls);</li>
 *   <li>{@code void tick()}: every frame;</li>
 *   <li>{@code void clicked()}: it was clicked with the mouse.</li>
 * </ul>
 * The kinds add their own: {@link Pickup} {@code picked()}, {@link Player} {@code hurt()}.
 */
public class Sprite implements Cloneable {

    /** Left edge, in tiles. */
    @Getter float x;
    /** Bottom edge, in tiles. */
    @Getter float y;
    /** Width in tiles (from the picture, 1 for placeholders). */
    @Getter float width = Float.NaN;
    /** Height in tiles. */
    @Getter float height = Float.NaN;
    /** Speed to the right, tiles per second. */
    @Getter float velocityX;
    /** Speed upwards, tiles per second. */
    @Getter float velocityY;
    /** Standing on something (after gravity pulled it down). */
    @Getter(AccessLevel.PROTECTED) boolean onGround;
    /** 1 = facing right, -1 = facing left. */
    int facing = 1;
    /** Last direction it moved in, for top-down shooting: (dx, dy). */
    float lookX = 1;
    float lookY;

    /** Fill to here (inclusive), from {@link #to}; NaN = a single one. */
    float toX = Float.NaN;
    float toY = Float.NaN;

    boolean removed;
    World world;
    float animation;

    /** The state of this sprite when its level started, for restarting things declared on the game itself. */
    Sprite start;

    // ---- placing ----

    /** Puts it at tile (x, y): x to the right, y up. */
    @SuppressWarnings("unchecked")
    public final <S extends Sprite> S at(float x, float y) {
        this.x = x;
        this.y = y;
        return (S) this;
    }

    /** First corner of an area to fill, see {@link #to}. Same as {@link #at}. */
    public final <S extends Sprite> S from(float x, float y) {
        return at(x, y);
    }

    /**
     * Fills the area from {@link #from} to here (both corners included). Walls become one long wall with the picture
     * repeated, everything else one copy per tile.
     */
    @SuppressWarnings("unchecked")
    public final <S extends Sprite> S to(float x, float y) {
        toX = x;
        toY = y;
        return (S) this;
    }

    /** Overrides the size from the picture, in tiles. */
    @SuppressWarnings("unchecked")
    public final <S extends Sprite> S size(float width, float height) {
        this.width = width;
        this.height = height;
        return (S) this;
    }

    /** Sets its speed in tiles per second (x to the right, y up). Walls still stop it. */
    @SuppressWarnings("unchecked")
    public final <S extends Sprite> S velocity(float x, float y) {
        velocityX = x;
        velocityY = y;
        return (S) this;
    }

    // ---- doing things ----

    /** Takes it out of the game. */
    public final void remove() {
        removed = true;
    }

    /** Whether it was removed (picked up, shot, …). */
    public final boolean isRemoved() {
        return removed;
    }

    /** Puts another sprite into the running game, e.g. {@code spawn(new Coin().at(getX(), getY() + 2))}. */
    protected final void spawn(Sprite sprite) {
        world().spawn(sprite);
    }

    /** Starts the current level over (after the player was hurt, for example). */
    protected final void restart() {
        world().requestRestart();
    }

    /** Goes to the next level (what a {@link Goal} does). */
    protected final void nextLevel() {
        world().requestNextLevel();
    }

    /** Whether a key is held, e.g. {@code held("Space")}. Names as in {@link com.dervarex.unweird.KeyPress}. */
    protected final boolean held(String key) {
        return com.dervarex.unweird.Keyboard.held(key);
    }

    /** Distance to another sprite's center, in tiles. */
    public final float distanceTo(Sprite other) {
        float dx = centerX() - other.centerX();
        float dy = centerY() - other.centerY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** Whether it overlaps {@code other} right now. */
    public final boolean touches(Sprite other) {
        return other != this && !removed && !other.removed && x < other.x + other.width && other.x < x + width
                && y < other.y + other.height && other.y < y + height;
    }

    // ---- behavior, overridden by the kinds ----

    /** Gravity pulls it down (in side-view games). */
    boolean falls() {
        return false;
    }

    /** Walls stop it when it moves. */
    boolean blockedByWalls() {
        return true;
    }

    /** Drawing order: higher is in front. */
    int layer() {
        return 1;
    }

    /** Per-step behavior: input, walking, AI. */
    void behave(World world, float dt) {
    }

    /** It ran into a wall while moving; {@code horizontal} tells which way. */
    void bumped(Sprite wall, boolean horizontal) {
    }

    /** The placeholder look when there's no picture. */
    Placeholder.Kind placeholder() {
        return Placeholder.Kind.THING;
    }

    /** The built-in reaction when this starts touching {@code other} (before your touch methods). */
    void touched(Sprite other) {
    }

    // ---- internal ----

    final float centerX() {
        return x + width / 2;
    }

    final float centerY() {
        return y + height / 2;
    }

    World world() {
        if (world == null) {
            throw new IllegalStateException("This " + getClass().getSimpleName() + " isn't in a running game yet: "
                    + "declare it as a field of your Game or Level, or add it with spawn(...)");
        }
        return world;
    }

    /** Annotation value on this sprite's class, or the fallback. */
    final float speed(float fallback) {
        Speed speed = getClass().getAnnotation(Speed.class);
        return speed != null ? speed.value() : fallback;
    }

    Sprite copy() {
        try {
            Sprite copy = (Sprite) super.clone();
            copy.start = null;
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    /** Puts position and motion back to the snapshot in {@link #start}. */
    void reset() {
        if (start != null) {
            x = start.x;
            y = start.y;
            width = start.width;
            height = start.height;
            toX = start.toX;
            toY = start.toY;
            velocityX = start.velocityX;
            velocityY = start.velocityY;
            facing = start.facing;
            removed = false;
            onGround = false;
            animation = 0;
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " at (" + x + ", " + y + ")";
    }
}
