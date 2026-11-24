package com.dervarex.unweird.game;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorldTest {

    // the kinds of things a test level is made of, declared like a user would
    static class Hero extends Player {}
    static class Ground extends Wall {}
    static class Ledge extends Platform {}
    static class Spike extends Hazard {}
    static class Flag extends Goal {}
    static class Slime extends Enemy {}

    static class Coin extends Pickup {
        static int picked;

        void picked() {
            picked++;
        }
    }

    static class Bell extends Sprite {
        final List<Sprite> touched = new ArrayList<>();

        void touch(Hero hero) {
            touched.add(hero);
        }
    }

    private static World world(boolean topDown, Sprite... sprites) {
        World world = new World(topDown, 16, new World.Factory() {
            @Override
            public <T> T create(Class<T> type) {
                return Members.create(type);
            }
        });
        world.load(List.of(sprites));
        return world;
    }

    private static void run(World world, double seconds) {
        for (double t = 0; t < seconds; t += 1 / 60.0) {
            world.update(1 / 60f);
        }
    }

    @Test
    void playerFallsOntoTheGround() {
        Hero hero = new Hero().at(2, 5);
        World world = world(false, new Ground().from(0, 0).to(10, 0), hero);
        run(world, 2);
        assertEquals(1, hero.getY(), 1e-3);
        assertTrue(hero.isOnGround());
    }

    @Test
    void fromToMakesOneLongWallButSeparateCoins() {
        World world = world(false, new Ground().from(0, 0).to(9, 0), new Coin().from(2, 3).to(6, 3));
        assertEquals(1, world.sprites.stream().filter(s -> s instanceof Ground).count());
        Sprite ground = world.sprites.stream().filter(s -> s instanceof Ground).findFirst().orElseThrow();
        assertEquals(10, ground.getWidth(), 1e-6);
        assertEquals(5, world.sprites.stream().filter(s -> s instanceof Coin).count());
    }

    @Test
    void touchingAPickupRemovesItAndCallsPicked() {
        Coin.picked = 0;
        Hero hero = new Hero().at(2, 1);
        Coin coin = new Coin().at(2, 1);
        World world = world(false, new Ground().from(0, 0).to(5, 0), hero, coin);
        run(world, 0.1);
        assertTrue(coin.isRemoved());
        assertEquals(1, Coin.picked);
        assertFalse(world.sprites.contains(coin));
    }

    @Test
    void hazardsRestartAndGoalsMoveOn() {
        World hurt = world(false, new Ground().from(0, 0).to(5, 0), new Hero().at(2, 1), new Spike().at(2, 1));
        run(hurt, 0.05);
        assertEquals(World.Pending.RESTART, hurt.pending);

        World done = world(false, new Ground().from(0, 0).to(5, 0), new Hero().at(2, 1), new Flag().at(2, 1));
        run(done, 0.05);
        assertEquals(World.Pending.NEXT, done.pending);
    }

    @Test
    void fallingOutOfTheLevelRestarts() {
        World world = world(false, new Ground().from(0, 0).to(2, 0), new Hero().at(5, 3));
        run(world, 3);
        assertEquals(World.Pending.RESTART, world.pending);
    }

    @Test
    void platformsCanBeJumpedThroughFromBelowAndStoodOn() {
        Hero hero = new Hero().at(2, 1);
        Ledge ledge = new Ledge().from(0, 3).to(5, 3);
        World world = world(false, new Ground().from(0, 0).to(5, 0), ledge, hero);
        run(world, 0.2);
        hero.velocity(0, 18); // a jump from below
        run(world, 1.5);
        assertEquals(4, hero.getY(), 1e-3, "landed on top of the platform");
    }

    @Test
    void wallsStopSideways() {
        Bell box = new Bell().at(1, 1);
        World world = world(true, new Ground().from(5, 0).to(5, 3), box);
        box.velocity(8, 0);
        world.update(0.5f);
        assertEquals(5, box.getX() + box.getWidth(), 1e-3);
        assertEquals(0, box.getVelocityX());
    }

    @Test
    void enemiesTurnAroundAtEdges() {
        Slime slime = new Slime().at(4, 1);
        World world = world(false, new Ground().from(2, 0).to(6, 0), slime);
        run(world, 8);
        assertTrue(slime.getX() >= 2 - 0.2 && slime.getX() <= 6.2, "stayed on its ground, at " + slime.getX());
    }

    @Test
    void jumpingOnAnEnemyDefeatsIt() {
        Slime slime = new Slime().at(2, 1);
        Hero hero = new Hero().at(2, 1.6f);
        World world = world(false, new Ground().from(0, 0).to(5, 0), slime, hero);
        hero.velocity(0, -5);
        run(world, 0.1);
        assertTrue(slime.isRemoved());
        assertEquals(World.Pending.NONE, world.pending);
    }

    @Test
    void touchMethodsRunOncePerContact() {
        Hero hero = new Hero().at(2, 1);
        Bell bell = new Bell().at(2, 1);
        World world = world(false, new Ground().from(0, 0).to(5, 0), hero, bell);
        run(world, 0.5);
        assertEquals(List.of(hero), bell.touched);
    }

    @Test
    void spawnedSpritesJoinAfterTheStep() {
        World world = world(false, new Ground().from(0, 0).to(5, 0));
        Coin coin = new Coin().at(1, 1);
        world.spawn(coin);
        assertFalse(world.sprites.contains(coin));
        world.update(1 / 60f);
        assertTrue(world.sprites.contains(coin));
    }

    @Test
    void cameraStaysInsideTheLevel() {
        Hero hero = new Hero().at(1, 1);
        World world = world(false, new Ground().from(0, 0).to(99, 0), hero);
        world.viewWidth = 20;
        world.viewHeight = 11;
        world.update(1 / 60f);
        assertEquals(0, world.cameraX, 1e-3, "doesn't show what's left of the level");
        assertEquals(0, world.cameraY, 1e-3, "the ground is at the bottom");
    }

    @Test
    void picturesDecideTheSizeAndPlaceholdersAreOneTile() {
        World world = world(false);
        assertArrayEquals(new float[]{1, 1}, world.naturalSize(Hero.class));
        assertArrayEquals(new float[]{0.5f, 0.5f}, world.naturalSize(Shot.class));
    }
}
