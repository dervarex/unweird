package com.dervarex.unweird.game;

/**
 * Walks back and forth, turning at walls and edges. Touching it from the side hurts the {@link Player}; jumping on it
 * (or a {@link Shot}) defeats it. In a {@link TopDown @TopDown} game it comes after the player when close and wanders
 * otherwise. {@link Speed @Speed} sets how fast (default 2 tiles per second). Add {@code defeated()} to react.
 */
public class Enemy extends Sprite {

    private float wanderTime;

    @Override
    boolean falls() {
        return world != null && !world.topDown;
    }

    @Override
    int layer() {
        return 3;
    }

    @Override
    Placeholder.Kind placeholder() {
        return Placeholder.Kind.ENEMY;
    }

    @Override
    void behave(World world, float dt) {
        float speed = speed(2);
        if (world.topDown) {
            Player player = world.player();
            if (player != null && distanceTo(player) < 7) {
                float dx = player.centerX() - centerX();
                float dy = player.centerY() - centerY();
                float length = (float) Math.max(0.001, Math.sqrt(dx * dx + dy * dy));
                velocityX = dx / length * speed;
                velocityY = dy / length * speed;
            } else {
                wanderTime -= dt;
                if (wanderTime <= 0) {
                    wanderTime = 1 + world.random.nextFloat() * 2;
                    double angle = world.random.nextDouble() * Math.PI * 2;
                    boolean rest = world.random.nextFloat() < 0.3f;
                    velocityX = rest ? 0 : (float) Math.cos(angle) * speed * 0.6f;
                    velocityY = rest ? 0 : (float) Math.sin(angle) * speed * 0.6f;
                }
            }
            if (velocityX != 0) {
                facing = velocityX < 0 ? -1 : 1;
            }
            return;
        }
        velocityX = facing * speed;
        if (onGround) {
            float frontX = facing > 0 ? x + width + 0.05f : x - 0.05f;
            if (!world.solidAt(frontX, y - 0.1f)) {
                facing = -facing; // don't walk off the edge
                velocityX = facing * speed;
            }
        }
    }

    @Override
    void bumped(Sprite wall, boolean horizontal) {
        if (horizontal && (world == null || !world.topDown)) {
            facing = -facing;
        } else if (world != null && world.topDown) {
            wanderTime = 0;
        }
    }

    @Override
    void touched(Sprite other) {
        if (!(other instanceof Player player) || removed) {
            return;
        }
        boolean fromAbove = world != null && !world.topDown && player.velocityY < 0 && player.y > y + height * 0.5f;
        if (fromAbove) {
            defeat();
            player.bounce();
        } else {
            player.damage();
        }
    }

    void defeat() {
        if (removed) {
            return;
        }
        remove();
        Sound.effect(getClass(), Assets.snake(getClass().getSimpleName()) + "_defeated", "stomp");
        Hooks.of(getClass()).call(this, "defeated");
    }
}
