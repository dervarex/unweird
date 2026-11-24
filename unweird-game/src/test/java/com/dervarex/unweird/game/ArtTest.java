package com.dervarex.unweird.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArtTest {

    static class Walker extends Player {}
    static class BigWalker extends Walker {}
    static class Brick extends Wall {}
    static class Goblin extends Enemy {}
    static class Arrow extends Shot {}

    @Test
    void numberedFramesAnimateAndStatesFallBack() {
        Art art = Art.of(Walker.class, 16);
        assertFalse(art.placeholder);
        assertEquals(16, art.pixelWidth());
        assertEquals(32, art.pixelHeight());
        assertEquals(0xffff0000, art.frame(Art.State.IDLE, 0).getRGB(0, 0));
        assertEquals(0xff00ff00, art.frame(Art.State.IDLE, 1 / Art.FRAMES_PER_SECOND).getRGB(0, 0), "second frame");
        assertEquals(0xff0000ff, art.frame(Art.State.WALK, 0).getRGB(0, 0));
        assertEquals(0xffffff00, art.frame(Art.State.FALL, 0).getRGB(0, 0), "no fall picture: the jump one");
    }

    @Test
    void subclassesUseTheirParentsPicturesAndSizesFollowThem() {
        assertFalse(Art.of(BigWalker.class, 16).placeholder);
        World world = new World(false, 16, null);
        assertArrayEquals(new float[]{1, 2}, world.naturalSize(BigWalker.class));
        assertTrue(Art.of(Brick.class, 16).placeholder, "no brick.png: drawn");
    }

    @Test
    void topDownEnemiesComeAfterThePlayer() {
        Walker player = new Walker().at(0, 0);
        Goblin goblin = new Goblin().at(5, 0);
        World world = new World(true, 16, null);
        world.load(List.of(player, goblin));
        float before = goblin.getX();
        for (int i = 0; i < 30; i++) {
            world.update(1 / 60f);
        }
        assertTrue(goblin.getX() < before, "moved towards the player");
    }

    @Test
    void shotsDefeatEnemiesAndStopAtWalls() {
        Goblin goblin = new Goblin().at(4, 0);
        Arrow arrow = new Arrow().at(1, 0.25f).velocity(14, 0);
        Brick brick = new Brick().at(2, 5);
        Arrow stray = new Arrow().at(0, 5.25f).velocity(14, 0);
        World world = new World(true, 16, null);
        world.load(List.of(goblin, arrow, brick, stray));
        for (int i = 0; i < 30; i++) {
            world.update(1 / 60f);
        }
        assertTrue(goblin.isRemoved());
        assertTrue(arrow.isRemoved());
        assertTrue(stray.isRemoved(), "hit the brick");
        assertFalse(brick.isRemoved());
    }
}
