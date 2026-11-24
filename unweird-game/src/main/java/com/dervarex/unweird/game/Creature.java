package com.dervarex.unweird.game;

/**
 * Walks around on its own: wanders, stops, looks around, hops up single blocks and swims. {@link Speed @Speed} sets
 * how fast (default 1.5 blocks per second).
 */
public class Creature extends Thing {

    private double timer;
    private double heading;
    private boolean walking;

    @Override
    boolean falls() {
        return true;
    }

    @Override
    void behave(double dt) {
        timer -= dt;
        if (timer <= 0) {
            java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
            walking = random.nextDouble() < 0.6;
            heading = random.nextDouble() * Math.PI * 2;
            timer = 1.5 + random.nextDouble() * 3;
        }
        double speed = walking ? speed(1.5f) : 0;
        body.vx = Math.sin(heading) * speed;
        body.vz = Math.cos(heading) * speed;
        if (walking) {
            float target = (float) Math.toDegrees(heading);
            float diff = ((target - yaw) % 360 + 540) % 360 - 180;
            yaw += diff * Math.min(1, dt * 6);
        }
        if (body.bumped && body.onGround) {
            body.vy = 7.2; // hop up one block
        }
        if (body.inLiquid) {
            body.vy = Math.max(body.vy, 1.5);
        }
    }
}
