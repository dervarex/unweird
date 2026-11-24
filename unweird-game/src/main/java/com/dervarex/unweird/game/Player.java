package com.dervarex.unweird.game;

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
    private boolean jumpCut;
    private boolean shootWasPressed;
    float invulnerable;

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
        int ix = (held("Right") || held("D") ? 1 : 0) - (held("Left") || held("A") ? 1 : 0);
        invulnerable = Math.max(0, invulnerable - dt);
        if (world.topDown) {
            int iy = (held("Up") || held("W") ? 1 : 0) - (held("Down") || held("S") ? 1 : 0);
            float length = (float) Math.sqrt(ix * ix + iy * iy);
            float targetX = length == 0 ? 0 : ix / length * speed;
            float targetY = length == 0 ? 0 : iy / length * speed;
            velocityX = approach(velocityX, targetX, speed * 12 * dt);
            velocityY = approach(velocityY, targetY, speed * 12 * dt);
            if (length > 0) {
                lookX = ix / length;
                lookY = iy / length;
                if (ix != 0) {
                    facing = ix;
                }
            }
        } else {
            float acceleration = onGround ? speed * 10 : speed * 6;
            velocityX = approach(velocityX, ix * speed, acceleration * dt);
            if (ix != 0) {
                facing = ix;
                lookX = ix;
                lookY = 0;
            }
            boolean jumpHeld = held("Space") || held("Up") || held("W");
            boolean jumpPressed = pressed("Space") || pressed("Up") || pressed("W");
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
        boolean shootPressed = pressed("X") || pressed("Ctrl");
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
        if (!Hooks.of(getClass()).call(this, "hurt")) {
            restart();
        }
    }

    /** Bounces off an enemy it jumped on. */
    void bounce() {
        velocityY = (float) Math.sqrt(2 * GRAVITY * 1.6f);
        jumpCut = !(held("Space") || held("Up") || held("W"));
    }

    private float jumpHeight(float fallback) {
        Jump jump = getClass().getAnnotation(Jump.class);
        return jump != null ? jump.value() : fallback;
    }

    private static boolean pressed(String key) {
        return com.dervarex.unweird.Keyboard.pressed(key);
    }

    static float approach(float value, float target, float step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }
}
