package com.dervarex.unweird.game;

/**
 * A {@link Creature} that comes after the {@link Explorer} when it's close (16 blocks) and hurts it on contact
 * (2 health, if the explorer has {@link Health @Health}). Clicking it hits it; after 3 hits it's gone and its
 * {@code defeated()} method runs. Looks like {@code zombie.obj} for a {@code Zombie}, otherwise like a placeholder
 * figure. {@link Speed @Speed} default 2.2.
 */
public class Monster extends Creature {

    private static final double SIGHT = 16;
    private double cooldown;
    int hits = 3;

    @Override
    void behave(double dt) {
        Explorer target = game == null ? null : game.explorerOrNull();
        cooldown = Math.max(0, cooldown - dt);
        if (stunned > 0) {
            stunned -= dt;
            return;
        }
        if (target == null || !target.placed || distanceTo(target) > SIGHT) {
            super.behave(dt);
            return;
        }
        double dx = target.body.x - body.x;
        double dz = target.body.z - body.z;
        double heading = Math.atan2(dx, dz);
        double speed = speed(2.2f);
        double flat = Math.hypot(dx, dz);
        body.vx = flat > 0.8 ? Math.sin(heading) * speed : 0;
        body.vz = flat > 0.8 ? Math.cos(heading) * speed : 0;
        yaw = (float) Math.toDegrees(heading);
        if (body.bumped && body.onGround) {
            body.vy = 7.2;
        }
        if (body.inLiquid) {
            body.vy = Math.max(body.vy, 1.5);
        }
        if (flat < 1.1 && Math.abs(target.body.y - body.y) < 1.5 && cooldown == 0) {
            cooldown = 1;
            target.hurt(2);
        }
    }

    /** The explorer hit it. */
    void hit(Explorer by) {
        hits--;
        double dx = body.x - by.body.x, dz = body.z - by.body.z;
        double length = Math.max(0.01, Math.hypot(dx, dz));
        body.vx = dx / length * 6;
        body.vz = dz / length * 6;
        body.vy = 5;
        stunned = 0.4;
        if (hits <= 0) {
            remove();
            Sound.effect(getClass(), Assets.snake(getClass().getSimpleName()) + "_defeated", "stomp");
            Hooks.of(getClass()).call(this, "defeated");
        } else {
            Sound.effect(getClass(), Assets.snake(getClass().getSimpleName()) + "_hurt", "hurt");
        }
    }
}
