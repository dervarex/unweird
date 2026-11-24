package com.dervarex.unweird.game;

import com.dervarex.unweird.scene3d.Node;

/**
 * Anything in a {@link Game3D} that isn't a block: animals, items, signs, furniture. What it does comes from what it
 * extends: {@link Creature} walks around, {@link Explorer} is you. A plain {@code Thing} stays where it is put.
 *
 * <p>Its look is found by name: {@code pig.obj} (with {@code pig.png} as its texture), otherwise a placeholder
 * (a box, or an animal for creatures) in a color that fits the name.
 *
 * <pre>{@code
 * Pig pig     = new Pig().near(me);       // somewhere close to the explorer, on the ground
 * Chest chest = new Chest().at(10, 0);    // x, z: on the ground there
 * Sign sign   = new Sign().at(4, 50, 8);  // x, y, z
 * }</pre>
 *
 * Optional methods: {@code tick()} every frame, {@code clicked()} when the explorer clicks it, {@code touch(Pig p)}
 * when it starts touching another thing.
 */
public class Thing implements Cloneable {

    final Body body = new Body();
    Node node;
    /** Leg nodes of a placeholder animal, swung while walking. */
    Node[] legs = new Node[0];
    Game3D game;
    boolean removed;
    /** Turned around the vertical axis, degrees. */
    float yaw;
    float walked;

    /** Waiting for its place to be loaded before it appears. */
    boolean placed;
    boolean onGround = true;
    double startX, startY = Double.NaN, startZ;
    Thing near;

    {
        body.width = 0.9;
        body.height = 0.9;
    }

    // ---- placing ----

    /** At (x, y, z): x east, y up, z south, in blocks. */
    @SuppressWarnings("unchecked")
    public final <T extends Thing> T at(double x, double y, double z) {
        startX = x;
        startY = y;
        startZ = z;
        onGround = false;
        near = null;
        return (T) this;
    }

    /** On the ground at (x, z). */
    @SuppressWarnings("unchecked")
    public final <T extends Thing> T at(double x, double z) {
        startX = x;
        startZ = z;
        startY = Double.NaN;
        onGround = true;
        near = null;
        return (T) this;
    }

    /** On the ground a few blocks from {@code other}. */
    @SuppressWarnings("unchecked")
    public final <T extends Thing> T near(Thing other) {
        near = other;
        return (T) this;
    }

    public final double getX() {
        return body.x;
    }

    public final double getY() {
        return body.y;
    }

    public final double getZ() {
        return body.z;
    }

    /** Moves it right now. */
    public final void moveTo(double x, double y, double z) {
        body.x = x;
        body.y = y;
        body.z = z;
        body.vx = body.vy = body.vz = 0;
    }

    /** Takes it out of the game. */
    public final void remove() {
        removed = true;
    }

    public final boolean isRemoved() {
        return removed;
    }

    /** Distance to another thing, in blocks. */
    public final double distanceTo(Thing other) {
        double dx = body.x - other.body.x, dy = body.y - other.body.y, dz = body.z - other.body.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Puts another thing into the world. */
    protected final void spawn(Thing thing) {
        game().spawn(thing);
    }

    /** Whether a key is held. */
    protected final boolean held(String key) {
        return com.dervarex.unweird.Keyboard.held(key);
    }

    // ---- behavior, overridden by the kinds ----

    boolean falls() {
        return false;
    }

    void behave(double dt) {
    }

    final Game3D game() {
        if (game == null) {
            throw new IllegalStateException("This " + getClass().getSimpleName() + " isn't in a running game yet: "
                    + "declare it as a field of your Game3D or add it with spawn(...)");
        }
        return game;
    }

    final float speed(float fallback) {
        Speed speed = getClass().getAnnotation(Speed.class);
        return speed != null ? speed.value() : fallback;
    }

    Thing copy() {
        try {
            Thing copy = (Thing) super.clone();
            copy.node = null;
            return copy;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + String.format(" at (%.1f, %.1f, %.1f)", body.x, body.y, body.z);
    }
}
