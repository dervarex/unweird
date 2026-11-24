package com.dervarex.unweird.game;

import com.dervarex.unweird.scene3d.Scene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Moving platforms, lives, falling and flowing blocks, health, inventories. */
class RoadmapTest {

    static class Hero extends Player {}
    @Lives(3) static class Tough extends Player {}
    static class Ground extends Wall {}
    static class Spike extends Hazard {}
    static class Lift extends Mover {}

    static class Grass extends Block {}
    static class Stone extends Block {}
    @Falls static class Sand extends Block {}
    @Liquid static class Water extends Block {}
    @Health(10) @Inventory static class Me extends Explorer {}

    private static World world(Sprite... sprites) {
        World world = new World(false, 16, null);
        world.load(List.of(sprites));
        return world;
    }

    private static void run(World world, double seconds) {
        for (double t = 0; t < seconds; t += 1 / 60.0) {
            world.update(1 / 60f);
        }
    }

    @Test
    void moversCarryWhatStandsOnThem() {
        Lift lift = new Lift().at(2, 2).travel(6, 0);
        Hero hero = new Hero().at(2, 3);
        World world = world(lift, hero);
        run(world, 0.2);
        assertEquals(3, hero.getY(), 0.05, "standing on the lift");
        run(world, 1.3);
        assertTrue(lift.getX() > 3, "the lift moved");
        assertEquals(lift.getX(), hero.getX(), 0.05, "and took the hero along");
        assertEquals(World.Pending.NONE, world.pending, "nobody fell");
    }

    @Test
    void moversGoBackAndForth() {
        Lift lift = new Lift().at(0, 0).travel(0, 4);
        World world = world(lift);
        run(world, 2); // 4 tiles at 2 per second: the top
        assertEquals(4, lift.getY(), 0.2);
        run(world, 2);
        assertEquals(0, lift.getY(), 0.2);
    }

    @Test
    void livesAbsorbHitsBeforeTheRestart() {
        Tough hero = new Tough().at(2, 1);
        World world = world(new Ground().from(0, 0).to(9, 0), hero, new Spike().at(2, 1));
        run(world, 0.05);
        assertEquals(2, hero.getLives());
        assertEquals(World.Pending.NONE, world.pending);
        hero.invulnerable = 0;
        hero.damage();
        hero.invulnerable = 0;
        hero.damage();
        assertEquals(World.Pending.RESTART, world.pending);
    }

    // ---- 3D ----

    final Grass grass = new Grass();
    final Stone stone = new Stone();
    final Sand sand = new Sand();
    final Water water = new Water();
    private final List<Voxels> created = new ArrayList<>();

    class Shore extends Terrain {
        Flat flat = new Flat(10);
        Surface surface = new Surface(stone);
        Sea sea = new Sea(water).level(12);
    }

    private Voxels voxels(Terrain terrain) {
        Voxels voxels = new Voxels(List.of(grass, stone, sand, water), terrain, 1, 2, new Scene());
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                Chunk chunk = voxels.generate(cx, cz);
                voxels.chunks.put(chunk.key(), chunk);
            }
        }
        created.add(voxels);
        return voxels;
    }

    @AfterEach
    void stop() {
        created.forEach(Voxels::shutdown);
    }

    private static void settle(Voxels voxels, double seconds) {
        for (double t = 0; t < seconds; t += 1 / 60.0) {
            voxels.settle(1 / 60.0);
        }
    }

    @Test
    void sandFallsUntilItLands() {
        Voxels voxels = voxels(new Shore());
        voxels.set(3, 20, 3, sand);
        settle(voxels, 2);
        assertNull(voxels.block(3, 20, 3));
        assertSame(sand, voxels.block(3, 11, 3), "through the water, onto the ground");
    }

    @Test
    void aHoleInTheSeaFloorFloodsButDryLandStaysDry() {
        Voxels voxels = voxels(new Shore());
        voxels.set(3, 10, 3, null);
        voxels.set(3, 9, 3, null);
        settle(voxels, 1);
        assertSame(water, voxels.block(3, 10, 3));
        assertSame(water, voxels.block(3, 9, 3));
        assertNull(voxels.block(3, 14, 3), "nothing above the sea");
    }

    @Test
    void fallsHurtAndHealthRunsOut() {
        Voxels voxels = voxels(new Flat2());
        Me me = new Me();
        me.body.x = 3.5;
        me.body.y = 20;
        me.body.z = 3.5;
        for (int i = 0; i < 180; i++) {
            me.body.move(voxels, 1 / 60.0, true);
            me.landed();
        }
        assertEquals(11, me.body.y, 1e-3);
        assertTrue(me.getHealth() < 10, "a 9-block fall hurts, health " + me.getHealth());
        me.spawnX = 1;
        me.spawnY = 11;
        me.spawnZ = 1;
        me.invulnerable = 0;
        me.hurt(100);
        assertEquals(10, me.getHealth(), "back to full");
        assertEquals(1, me.body.x, "at the start again");
    }

    class Flat2 extends Terrain {
        Flat flat = new Flat(10);
        Surface surface = new Surface(grass, stone, stone);
    }

    @Test
    void inventoriesCountAndComeBack(@TempDir Path folder) {
        Me me = new Me();
        me.give(stone, 3);
        me.give(stone, -1);
        me.give(grass, 1);
        me.give(grass, -5);
        assertEquals(2, me.count(stone));
        assertEquals(0, me.count(grass));
        me.invulnerable = 0;
        me.hurt(4);

        Voxels voxels = voxels(new Flat2());
        me.placed = true;
        Save save = new Save(folder.resolve("world.dat"), voxels.blocks);
        save.seed(1);
        save.write(voxels, me);
        Me back = new Me();
        new Save(folder.resolve("world.dat"), voxels.blocks).restore(back);
        assertEquals(2, back.count(stone));
        assertEquals(6, back.getHealth());
    }
}
