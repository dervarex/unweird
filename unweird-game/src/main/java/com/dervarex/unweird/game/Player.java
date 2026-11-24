package com.dervarex.unweird.game;

import com.dervarex.unweird.Gamepad;

/**
 * You. Arrow keys or A/D run, Space, Up or W jumps (hold for higher jumps), X or Ctrl shoots if it has
 * {@link Shoots @Shoots}. In a {@link TopDown @TopDown} game it walks in all directions instead. The camera follows it.
 *
 * <p>Touching a {@link Hazard} or an {@link Enemy} from the side hurts it, which starts the level over. Add a
 * {@code hurt()} method to decide yourself:
 *
 * <pre>{@code
 * class Hero extends Player {
 *     void hurt() { lives--; if (lives == 0) restart(); }
 * }
 * }</pre>
 *
 * Tune it with {@link Speed @Speed} (default 7 tiles per second) and {@link Jump @Jump} (default 3.5 tiles).
 */
public class Player extends Sprite {

    static final float GRAVITY = 42;
    private static final float COYOTE = 0.1f;
    private static final float BUFFER = 0.12f;
    private static final float INVULNERABLE = 0.6f;

    private float coyote;
    private float buffer;
    private boolean jumpWasPressed;
    /** Only a jump the player started gets lower when the key is let go early (not a bounce or a push). */
    private boolean jumpCut = true;
    private boolean shootWasPressed;
    float invulnerable;
    /** Hits left before the level starts over, see {@link Lives @Lives}; -1 until it first matters. */
    private int lives = -1;

    /** How many hits it can still take (see {@link Lives @Lives}). */
    public final int getLives() {
        if (lives < 0) {
            Lives annotation = getClass().getAnnotation(Lives.class);
            lives = annotation != null ? Math.max(1, annotation.value()) : 1;
        }
        return lives;
    }

    @Override
    boolean falls() {
        return world != null && !world.topDown;
    }

    @Override
    int layer() {
        return 4;
    }

    @Override
    Placeholder.Kind placeholder() {
        return Placeholder.Kind.PLAYER;
    }

    @Override
    void behave(World world, float dt) {
        float speed = speed(7);
        float ix = (held("Right") || held("D") || Gamepad.held(Gamepad.Button.RIGHT) ? 1 : 0)
                - (held("Left") || held("A") || Gamepad.held(Gamepad.Button.LEFT) ? 1 : 0);
        if (ix == 0) {
            ix = Gamepad.axis(Gamepad.Axis.LEFT_X);
        }
        invulnerable = Math.max(0, invulnerable - dt);
        if (world.topDown) {
            float iy = (held("Up") || held("W") || Gamepad.held(Gamepad.Button.UP) ? 1 : 0)
                    - (held("Down") || held("S") || Gamepad.held(Gamepad.Button.DOWN) ? 1 : 0);
            if (iy == 0) {
                iy = -Gamepad.axis(Gamepad.Axis.LEFT_Y);
            }
            float length = (float) Math.min(1, Math.sqrt(ix * ix + iy * iy));
            float norm = (float) Math.sqrt(ix * ix + iy * iy);
            float targetX = norm == 0 ? 0 : ix / norm * length * speed;
            float targetY = norm == 0 ? 0 : iy / norm * length * speed;
            velocityX = approach(velocityX, targetX, speed * 12 * dt);
            velocityY = approach(velocityY, targetY, speed * 12 * dt);
            if (norm > 0) {
                lookX = ix / norm;
                lookY = iy / norm;
                if (ix != 0) {
                    facing = ix < 0 ? -1 : 1;
                }
            }
        } else {
            float acceleration = onGround ? speed * 10 : speed * 6;
            velocityX = approach(velocityX, ix * speed, acceleration * dt);
            if (ix != 0) {
                facing = ix < 0 ? -1 : 1;
                lookX = facing;
                lookY = 0;
            }
            boolean jumpHeld = held("Space") || held("Up") || held("W") || Gamepad.held(Gamepad.Button.A);
            boolean jumpPressed = pressed("Space") || pressed("Up") || pressed("W") || Gamepad.pressed(Gamepad.Button.A);
            coyote = onGround ? COYOTE : coyote - dt;
            buffer = jumpPressed && !jumpWasPressed ? BUFFER : buffer - dt;
            jumpWasPressed = jumpPressed;
            if (buffer > 0 && coyote > 0) {
                velocityY = (float) Math.sqrt(2 * GRAVITY * jumpHeight(3.5f));
                buffer = 0;
                coyote = 0;
                jumpCut = false;
                onGround = false;
                Sound.effect(getClass(), Assets.snake(getClass().getSimpleName()) + "_jump", "jump");
            }
            if (!jumpHeld && velocityY > 0 && !jumpCut) {
                velocityY *= 0.45f; // let go early: lower jump
                jumpCut = true;
            }
        }
        boolean shootPressed = pressed("X") || pressed("Ctrl") || Gamepad.pressed(Gamepad.Button.X) || Gamepad.pressed(Gamepad.Button.B);
        if (shootPressed && !shootWasPressed) {
            shoot(world);
        }
        shootWasPressed = shootPressed;
    }

    private void shoot(World world) {
        Shoots shoots = getClass().getAnnotation(Shoots.class);
        if (shoots == null) {
            return;
        }
        Shot shot = world.create(shoots.value());
        world.prepare(shot);
        float dx = world.topDown ? lookX : facing;
        float dy = world.topDown ? lookY : 0;
        float speed = shot.speed(14);
        shot.velocityX = dx * speed;
        shot.velocityY = dy * speed;
        shot.facing = dx < 0 ? -1 : 1;
        shot.x = centerX() - shot.width / 2 + dx * (width / 2);
        shot.y = centerY() - shot.height / 2 + dy * (height / 2);
        shot.shooter = this;
        world.spawn(shot);
        Sound.effect(getClass(), Assets.snake(shoots.value().getSimpleName()), "shoot");
    }

    /** Something hurt it: the user's {@code hurt()}, or start over. */
    void damage() {
        if (invulnerable > 0 || removed) {
            return;
        }
        invulnerable = INVULNERABLE;
        Sound.effect(getClass(), Assets.snake(getClass().getSimpleName()) + "_hurt", "hurt");
        if (Hooks.of(getClass()).call(this, "hurt")) {
            return;
        }
        lives = getLives() - 1;
        if (lives <= 0) {
            lives = -1; // full again after the restart
            restart();
        }
    }

    /** Bounces off an enemy it jumped on. */
    void bounce() {
        velocityY = (float) Math.sqrt(2 * GRAVITY * 1.6f);
        jumpCut = !(held("Space") || held("Up") || held("W") || Gamepad.held(Gamepad.Button.A));
    }

    private float jumpHeight(float fallback) {
        Jump jump = getClass().getAnnotation(Jump.class);
        return jump != null ? jump.value() : fallback;
    }

    /** Lives are full again when its level starts over. */
    @Override
    void reset() {
        super.reset();
        lives = -1;
        invulnerable = 0;
    }

    private static boolean pressed(String key) {
        return com.dervarex.unweird.Keyboard.pressed(key);
    }

    static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
}
