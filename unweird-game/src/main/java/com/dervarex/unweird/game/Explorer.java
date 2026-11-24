package com.dervarex.unweird.game;

import com.dervarex.unweird.Gamepad;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * You, in first person. Click into the game to look around with the mouse (Escape lets go of it).
 * <ul>
 *   <li>W A S D or arrows: walk, Ctrl: run, Space: jump (swim up in water)</li>
 *   <li>F: fly on/off; while flying Space goes up and Shift down</li>
 *   <li>Left click: break a block (or click a {@link Thing}), right click: place one</li>
 *   <li>1–9 or the mouse wheel: choose the block to place from the bar at the bottom (all your blocks)</li>
 * </ul>
 * A game controller works too: left stick walks, right stick looks, A jumps, Y flies, B goes down, right trigger
 * breaks, left trigger places, bumpers choose.
 *
 * <p>{@link Speed @Speed} sets the walking speed (default 4.5), {@link Jump @Jump} the jump height (default 1.25),
 * {@link Health @Health} lets it get hurt, {@link Inventory @Inventory} makes it collect blocks before building.
 * Optional methods: {@code broke(Block block)}, {@code placed(Block block)}, {@code hurt()}, {@code died()}.
 */
public class Explorer extends Thing {

    static final double EYE = 1.62;
    static final double REACH = 6;

    boolean flying;
    float pitch;

    private int health = -1;
    float invulnerable;
    /** Highest point of the current fall, for fall damage. */
    private double fallFrom = Double.NaN;
    /** Where it first stood: where it starts again. */
    double spawnX = Double.NaN, spawnY, spawnZ;
    final Map<Block, Integer> inventory = new LinkedHashMap<>();

    {
        body.width = 0.6;
        body.height = 1.8;
    }

    @Override
    boolean falls() {
        return !flying;
    }

    /** Health left, see {@link Health @Health}; 0 without it. */
    public final int getHealth() {
        if (health < 0) {
            health = getMaxHealth();
        }
        return health;
    }

    public final int getMaxHealth() {
        Health annotation = getClass().getAnnotation(Health.class);
        return annotation == null ? 0 : Math.max(1, annotation.value());
    }

    /** How many of a block it carries (see {@link Inventory @Inventory}). */
    public final int count(Block block) {
        return inventory.getOrDefault(block, 0);
    }

    /** Hands it blocks (see {@link Inventory @Inventory}); a negative amount takes them away. */
    public final void give(Block block, int amount) {
        int next = Math.max(0, count(block) + amount);
        if (next == 0) {
            inventory.remove(block);
        } else {
            inventory.put(block, next);
        }
    }

    final boolean collects() {
        return getClass().isAnnotationPresent(Inventory.class);
    }

    /** Takes damage (only with {@link Health @Health}). */
    public final void hurt(int amount) {
        if (getMaxHealth() == 0 || invulnerable > 0 || amount <= 0) {
            return;
        }
        invulnerable = 0.5f;
        health = getHealth() - amount;
        Sound.effect(getClass(), Assets.snake(getClass().getSimpleName()) + "_hurt", "hurt");
        if (game != null) {
            game.hurtFlash();
        }
        Hooks.of(getClass()).call(this, "hurt");
        if (health <= 0 && !Hooks.of(getClass()).call(this, "died")) {
            respawn();
        }
    }

    /** Sets the health directly (a saved world coming back). */
    void hurtTo(int value) {
        health = Math.max(1, Math.min(getMaxHealth(), value));
    }

    /** Back to where it started, healthy. */
    public final void respawn() {
        health = getMaxHealth();
        if (!Double.isNaN(spawnX)) {
            moveTo(spawnX, spawnY, spawnZ);
        }
        fallFrom = Double.NaN;
    }

    /** Whether it's flying right now. */
    public final boolean isFlying() {
        return flying;
    }

    @Override
    void behave(double dt) {
        invulnerable = (float) Math.max(0, invulnerable - dt);
        if (pressed("F") || Gamepad.pressed(Gamepad.Button.Y)) {
            flying = !flying;
            body.vy = 0;
        }
        double forward = (held("W") || held("Up") ? 1 : 0) - (held("S") || held("Down") ? 1 : 0);
        double strafe = (held("D") || held("Right") ? 1 : 0) - (held("A") || held("Left") ? 1 : 0);
        if (forward == 0 && strafe == 0) {
            forward = -Gamepad.axis(Gamepad.Axis.LEFT_Y);
            strafe = Gamepad.axis(Gamepad.Axis.LEFT_X);
        }
        boolean jump = held("Space") || Gamepad.held(Gamepad.Button.A);
        boolean down = held("Shift") || Gamepad.held(Gamepad.Button.B);
        double speed = speed(4.5f) * (held("Ctrl") ? 1.4 : 1) * (flying ? 2.2 : 1) * (body.inLiquid && !flying ? 0.5 : 1);
        double yawRad = Math.toRadians(yaw);
        double fx = -Math.sin(yawRad), fz = -Math.cos(yawRad); // where the camera looks
        double rx = Math.cos(yawRad), rz = -Math.sin(yawRad);
        double mx = fx * forward + rx * strafe;
        double mz = fz * forward + rz * strafe;
        double length = Math.sqrt(mx * mx + mz * mz);
        if (length > 1) {
            mx /= length;
            mz /= length;
        }
        double response = body.onGround || flying ? 14 : 4; // less control in the air
        body.vx += (mx * speed - body.vx) * Math.min(1, response * dt);
        body.vz += (mz * speed - body.vz) * Math.min(1, response * dt);
        if (flying) {
            double vertical = (jump ? 1 : 0) - (down ? 1 : 0);
            body.vy += (vertical * speed - body.vy) * Math.min(1, 10 * dt);
        } else if (body.inLiquid) {
            if (jump) {
                body.vy = Math.max(body.vy, 3);
            }
        } else if (jump && body.onGround) {
            body.vy = Math.sqrt(2 * Body.GRAVITY * jumpHeight());
            Sound.effect(getClass(), Assets.snake(getClass().getSimpleName()) + "_jump");
        }
        if (body.onGround && length > 0) {
            walked += (float) (speed * dt);
        }
    }

    /** After moving: fall damage when it lands. */
    void landed() {
        if (flying || body.inLiquid) {
            fallFrom = Double.NaN;
            return;
        }
        if (!body.onGround) {
            fallFrom = Double.isNaN(fallFrom) ? body.y : Math.max(fallFrom, body.y);
            return;
        }
        if (!Double.isNaN(fallFrom)) {
            double fall = fallFrom - body.y;
            fallFrom = Double.NaN;
            if (fall > 3.5) {
                hurt((int) Math.ceil(fall - 3));
            }
        }
    }

    private double jumpHeight() {
        Jump jump = getClass().getAnnotation(Jump.class);
        return jump != null ? jump.value() : 1.25;
    }

    private static boolean pressed(String key) {
        return com.dervarex.unweird.Keyboard.pressed(key);
    }

    /** Eye position and look direction: {x, y, z, dx, dy, dz}. */
    double[] eye() {
        double yawRad = Math.toRadians(yaw), pitchRad = Math.toRadians(pitch);
        return new double[]{body.x, body.y + EYE, body.z,
                -Math.sin(yawRad) * Math.cos(pitchRad), Math.sin(pitchRad), -Math.cos(yawRad) * Math.cos(pitchRad)};
    }
}
