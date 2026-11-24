package com.dervarex.unweird.game;

import com.dervarex.unweird.scene3d.Mesh;
import com.dervarex.unweird.scene3d.Scene;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VoxelTest {

    static class Grass extends Block {}
    static class Dirt extends Block {}
    static class Stone extends Block {}
    @Liquid static class Water extends Block {}
    @SeeThrough static class Glass extends Block {}
    static class Log extends Block {}
    @SeeThrough static class Leaves extends Block {}

    final Grass grass = new Grass();
    final Dirt dirt = new Dirt();
    final Stone stone = new Stone();
    final Water water = new Water();
    final Glass glass = new Glass();
    final Log log = new Log();
    final Leaves leaves = new Leaves();
    final List<Block> blocks = List.of(grass, dirt, stone, water, glass, log, leaves);

    class Land extends Terrain {
        Hills hills = new Hills();
        Surface surface = new Surface(grass, dirt, stone);
        Sea sea = new Sea(water);
        Trees trees = new Trees(log, leaves).dense();
    }

    class Floor extends Terrain {
        Flat flat = new Flat(10);
        Surface surface = new Surface(grass, dirt, stone);
    }

    private final List<Voxels> created = new ArrayList<>();

    private Voxels voxels(Terrain terrain, long seed) {
        Voxels voxels = new Voxels(blocks, terrain, seed, 2, new Scene());
        created.add(voxels);
        return voxels;
    }

    @AfterEach
    void stop() {
        created.forEach(Voxels::shutdown);
    }

    /** Loads the chunks around the origin synchronously, like the game would over a few frames. */
    private static void load(Voxels voxels, int radius) {
        for (int cx = -radius; cx <= radius; cx++) {
            for (int cz = -radius; cz <= radius; cz++) {
                Chunk chunk = voxels.generate(cx, cz);
                voxels.chunks.put(chunk.key(), chunk);
            }
        }
    }

    @Test
    void theSameSeedMakesTheSameWorld() {
        Voxels a = voxels(new Land(), 42);
        Voxels b = voxels(new Land(), 42);
        Voxels c = voxels(new Land(), 43);
        assertArrayEquals(a.generate(3, -2).blocks, b.generate(3, -2).blocks);
        assertFalse(java.util.Arrays.equals(a.generate(3, -2).blocks, c.generate(3, -2).blocks));
    }

    @Test
    void surfaceLayersAndSea() {
        Voxels voxels = voxels(new Floor(), 1);
        load(voxels, 0);
        assertSame(grass, voxels.block(3, 10, 3));
        assertSame(dirt, voxels.block(3, 9, 3));
        assertSame(dirt, voxels.block(3, 7, 3));
        assertSame(stone, voxels.block(3, 6, 3));
        assertNull(voxels.block(3, 11, 3));
        assertEquals(10, voxels.ground(3, 3));

        Voxels sea = voxels(new Land(), 7);
        load(sea, 0);
        int lowest = Integer.MAX_VALUE;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                lowest = Math.min(lowest, sea.terrain.height(x, z));
            }
        }
        if (lowest < 33) {
            assertTrue(sea.liquid(0, 34, 0) || sea.block(0, 34, 0) != null, "below sea level is water or ground");
        }
    }

    @Test
    void treesGrowOnDryLand() {
        Voxels voxels = voxels(new Land(), 5);
        load(voxels, 1);
        int logs = 0;
        for (Chunk chunk : voxels.chunks.values()) {
            for (short id : chunk.blocks) {
                if (id == log.id) {
                    logs++;
                }
            }
        }
        assertTrue(logs > 0, "a dense forest has trees");
    }

    @Test
    void changesSurviveAChunkBeingMadeAgain() {
        Voxels voxels = voxels(new Floor(), 1);
        load(voxels, 0);
        assertTrue(voxels.set(2, 11, 2, glass));
        assertTrue(voxels.set(2, 10, 2, null));
        Chunk again = voxels.generate(0, 0);
        assertEquals(glass.id, again.get(2, 11, 2));
        assertEquals(0, again.get(2, 10, 2));
    }

    @Test
    void onlyVisibleFacesAreMeshed() {
        Voxels voxels = voxels(null, 1);
        load(voxels, 1);
        voxels.set(4, 20, 4, stone);
        Mesh[] one = Mesher.build(voxels.chunks.get(Chunk.key(0, 0)), voxels::id, voxels.kinds);
        assertEquals(12, one[0].triangleCount(), "a lone block: 6 faces");
        voxels.set(5, 20, 4, stone);
        Mesh[] two = Mesher.build(voxels.chunks.get(Chunk.key(0, 0)), voxels::id, voxels.kinds);
        assertEquals(20, two[0].triangleCount(), "the faces between two blocks are hidden");
        voxels.set(6, 20, 4, glass);
        Mesh[] three = Mesher.build(voxels.chunks.get(Chunk.key(0, 0)), voxels::id, voxels.kinds);
        assertEquals(20, three[0].triangleCount(), "stone still shows its face to glass");
        assertEquals(10, three[1].triangleCount(), "glass hides its face towards stone");
    }

    @Test
    void raysFindTheFirstBlockAndThePlaceInFront() {
        Voxels voxels = voxels(new Floor(), 1);
        load(voxels, 0);
        Raycast.Hit hit = Raycast.cast(voxels, 3.5, 14, 3.5, 0, -1, 0, 6);
        assertNotNull(hit);
        assertEquals(10, hit.y());
        assertEquals(11, hit.frontY());
        assertNull(Raycast.cast(voxels, 3.5, 30, 3.5, 0, -1, 0, 6), "out of reach");
    }

    @Test
    void bodiesFallAndStopAtBlocks() {
        Voxels voxels = voxels(new Floor(), 1);
        load(voxels, 1);
        Body body = new Body();
        body.x = 3.5;
        body.y = 20;
        body.z = 3.5;
        for (int i = 0; i < 180; i++) {
            body.move(voxels, 1 / 60.0, true);
        }
        assertEquals(11, body.y, 1e-3);
        assertTrue(body.onGround);
        voxels.set(5, 11, 3, stone);
        body.vx = 5;
        boolean bumped = false;
        for (int i = 0; i < 30; i++) {
            body.move(voxels, 1 / 60.0, true);
            bumped |= body.bumped;
        }
        assertEquals(5 - body.width / 2, body.x, 1e-3, "stopped at the wall");
        assertTrue(bumped);
    }

    @Test
    void savedWorldsComeBack(@TempDir Path folder) {
        Path file = folder.resolve("world.dat");
        Voxels voxels = voxels(new Floor(), 1);
        load(voxels, 0);
        voxels.set(1, 11, 1, glass);
        Explorer explorer = new Explorer();
        explorer.placed = true;
        explorer.body.x = 4;
        explorer.body.y = 11;
        explorer.body.z = 5;
        explorer.yaw = 90;
        Save save = new Save(file, blocks);
        assertEquals(99, save.seed(99));
        save.write(voxels, explorer);

        Save loaded = new Save(file, blocks);
        assertEquals(99, loaded.seed(5), "the saved seed wins");
        Voxels again = voxels(new Floor(), 99);
        loaded.applyEdits(again);
        load(again, 0);
        assertSame(glass, again.block(1, 11, 1));
        Explorer back = new Explorer();
        loaded.restore(back);
        assertEquals(4, back.startX);
        assertEquals(90, back.yaw);
    }
}
