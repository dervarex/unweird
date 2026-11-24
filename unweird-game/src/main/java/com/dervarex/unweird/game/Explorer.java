package com.dervarex.unweird.game;

/**
 * You, in first person. Click into the game to look around with the mouse (Escape lets go of it).
 * <ul>
 *   <li>W A S D or arrows: walk, Ctrl: run, Space: jump (swim up in water)</li>
 *   <li>F: fly on/off; while flying Space goes up and Shift down</li>
 *   <li>Left click: break a block (or click a {@link Thing}), right click: place one</li>
 *   <li>1–9 or the mouse wheel: choose the block to place from the bar at the bottom (all your blocks)</li>
 * </ul>
 * {@link Speed @Speed} sets the walking speed (default 4.5), {@link Jump @Jump} the jump height (default 1.25).
 * Optional methods: {@code broke(Block block)}, {@code placed(Block block)}.
 */
public class Explorer extends Thing {

    static final double EYE = 1.62;
    static final double REACH = 6;

    boolean flying;
    float pitch;

    {
        body.width = 0.6;
        body.height = 1.8;
    }

    @Override
    boolean falls() {
        return !flying;
    }

    /** Whether it's flying right now. */
    public final boolean isFlying() {
        return flying;
    }

    @Override
    void behave(double dt) {
        if (pressed("F")) {
            flying = !flying;
            body.vy = 0;
        }
        double forward = (held("W") || held("Up") ? 1 : 0) - (held("S") || held("Down") ? 1 : 0);
        double strafe = (held("D") || held("Right") ? 1 : 0) - (held("A") || held("Left") ? 1 : 0);
        double speed = speed(4.5f) * (held("Ctrl") ? 1.4 : 1) * (flying ? 2.2 : 1) * (body.inLiquid && !flying ? 0.5 : 1);
        double yawRad = Math.toRadians(yaw);
        double fx = -Math.sin(yawRad), fz = -Math.cos(yawRad); // where the camera looks
        double rx = Math.cos(yawRad), rz = -Math.sin(yawRad);
        double mx = fx * forward + rx * strafe;
        double mz = fz * forward + rz * strafe;
        double length = Math.sqrt(mx * mx + mz * mz);
        if (length > 0) {
            mx /= length;
            mz /= length;
        }
        double response = body.onGround || flying ? 14 : 4; // less control in the air
        body.vx += (mx * speed - body.vx) * Math.min(1, response * dt);
        body.vz += (mz * speed - body.vz) * Math.min(1, response * dt);
        if (flying) {
            double vertical = (held("Space") ? 1 : 0) - (held("Shift") ? 1 : 0);
            body.vy += (vertical * speed - body.vy) * Math.min(1, 10 * dt);
        } else if (body.inLiquid) {
            if (held("Space")) {
                body.vy = Math.max(body.vy, 3);
            }
        } else if (held("Space") && body.onGround) {
            body.vy = Math.sqrt(2 * Body.GRAVITY * jumpHeight());
            Sound.effect(getClass(), Assets.snake(getClass().getSimpleName()) + "_jump");
        }
        if (body.onGround && length > 0) {
            walked += (float) (speed * dt);
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
