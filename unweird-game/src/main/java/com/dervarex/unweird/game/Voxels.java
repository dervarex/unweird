package com.dervarex.unweird.game;

import com.dervarex.unweird.scene3d.Material;
import com.dervarex.unweird.scene3d.Mesh;
import com.dervarex.unweird.scene3d.Node;
import com.dervarex.unweird.scene3d.Scene;
import com.dervarex.unweird.scene3d.Texture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The block world: chunks made around the player on background threads, meshed, shown and dropped again far away.
 * Changes (placing, breaking) are kept separately so they survive a chunk being dropped and made again.
 * Everything except the background work runs on the UI thread.
 */
final class Voxels {

    final List<Block> blocks;
    final Terrain terrain;
    final Mesher.Kinds kinds;
    final BlockArt art;
    final int radius;

    final Map<Long, Chunk> chunks = new ConcurrentHashMap<>();
    /** Changed blocks per chunk: block index → id. */
    final Map<Long, Map<Integer, Short>> edits = new ConcurrentHashMap<>();

    private final Set<Long> generating = ConcurrentHashMap.newKeySet();
    private final Queue<Chunk> generated = new ConcurrentLinkedQueue<>();
    private final Queue<Object[]> meshed = new ConcurrentLinkedQueue<>();
    private final AtomicInteger busy = new AtomicInteger();
    private final ExecutorService pool;
    private final int threads;

    private final Scene scene;
    private final Material solid;
    private final Material cutout;
    private final Material liquid;

    Voxels(List<Block> blocks, Terrain terrain, long seed, int radius, Scene scene) {
        this.blocks = blocks;
        this.terrain = terrain;
        this.radius = radius;
        this.scene = scene;
        for (int i = 0; i < blocks.size(); i++) {
            blocks.get(i).id = i + 1;
        }
        art = new BlockArt(blocks);
        kinds = Mesher.Kinds.of(blocks, art);
        if (terrain != null) {
            terrain.prepare(seed, blocks.isEmpty() ? null : blocks.getFirst());
        }
        Texture atlas = Texture.of(art.atlas).nearest();
        solid = Material.texture(atlas).unlit();
        cutout = Material.texture(atlas).unlit().cutout().doubleSided();
        liquid = Material.texture(atlas).unlit().transparent().doubleSided();
        threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
        pool = Executors.newFixedThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "unweird-chunks");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
    }

    void shutdown() {
        pool.shutdownNow();
    }

    // ---- reading and changing blocks ----

    /** The block id at a place; {@link Mesher#SOLID} where nothing is loaded or below the world, 0 above it. */
    short id(int x, int y, int z) {
        if (y < 0) {
            return Mesher.SOLID;
        }
        if (y >= Chunk.HEIGHT) {
            return 0;
        }
        Chunk chunk = chunks.get(Chunk.key(Math.floorDiv(x, Chunk.SIZE), Math.floorDiv(z, Chunk.SIZE)));
        return chunk == null ? Mesher.SOLID : chunk.get(Math.floorMod(x, Chunk.SIZE), y, Math.floorMod(z, Chunk.SIZE));
    }

    /** The block at a place, or null for air and places that aren't loaded. */
    Block block(int x, int y, int z) {
        short id = id(x, y, z);
        return id <= 0 || id == Mesher.SOLID ? null : blocks.get(id - 1);
    }

    /** Whether bodies can't move through it (unloaded places count as solid, so nothing falls out of the world). */
    boolean solid(int x, int y, int z) {
        short id = id(x, y, z);
        return id == Mesher.SOLID || id > 0 && blocks.get(id - 1).solid();
    }

    boolean liquid(int x, int y, int z) {
        short id = id(x, y, z);
        return id > 0 && id != Mesher.SOLID && blocks.get(id - 1).liquid;
    }

    boolean loaded(int x, int z) {
        return chunks.containsKey(Chunk.key(Math.floorDiv(x, Chunk.SIZE), Math.floorDiv(z, Chunk.SIZE)));
    }

    /** The highest block you can stand on in a column, or -1 if it isn't loaded. */
    int ground(int x, int z) {
        if (!loaded(x, z)) {
            return -1;
        }
        for (int y = Chunk.HEIGHT - 1; y >= 0; y--) {
            short id = id(x, y, z);
            if (id > 0 && (blocks.get(id - 1).solid() || blocks.get(id - 1).liquid)) {
                return y;
            }
        }
        return 0;
    }

    /** Puts a block (null = air) somewhere loaded. Returns whether it changed. */
    boolean set(int x, int y, int z, Block block) {
        if (y < 0 || y >= Chunk.HEIGHT) {
            return false;
        }
        int cx = Math.floorDiv(x, Chunk.SIZE);
        int cz = Math.floorDiv(z, Chunk.SIZE);
        Chunk chunk = chunks.get(Chunk.key(cx, cz));
        if (chunk == null) {
            return false;
        }
        int lx = Math.floorMod(x, Chunk.SIZE);
        int lz = Math.floorMod(z, Chunk.SIZE);
        short id = (short) (block == null ? 0 : block.id);
        if (chunk.get(lx, y, lz) == id) {
            return false;
        }
        chunk.set(lx, y, lz, id);
        edits.computeIfAbsent(chunk.key(), k -> new ConcurrentHashMap<>()).put(Chunk.index(lx, y, lz), id);
        chunk.dirty = true;
        for (int dx = -1; dx <= 1; dx++) { // faces and shadows on the neighbors' side change too
            for (int dz = -1; dz <= 1; dz++) {
                boolean nearX = dx == 0 || dx < 0 && lx == 0 || dx > 0 && lx == Chunk.SIZE - 1;
                boolean nearZ = dz == 0 || dz < 0 && lz == 0 || dz > 0 && lz == Chunk.SIZE - 1;
                Chunk neighbor = chunks.get(Chunk.key(cx + dx, cz + dz));
                if (nearX && nearZ && neighbor != null) {
                    neighbor.dirty = true;
                }
            }
        }
        return true;
    }

    // ---- streaming ----

    /** Loads, meshes and drops chunks around (x, z). Call once per frame. */
    void update(double x, double z) {
        int centerX = Math.floorDiv((int) Math.floor(x), Chunk.SIZE);
        int centerZ = Math.floorDiv((int) Math.floor(z), Chunk.SIZE);

        for (Chunk chunk; (chunk = generated.poll()) != null; ) {
            chunks.put(chunk.key(), chunk);
            generating.remove(chunk.key());
        }
        for (Object[] result; (result = meshed.poll()) != null; ) {
            show((Chunk) result[0], (Mesh[]) result[1]);
        }

        // what's missing, nearest first
        List<int[]> wanted = new ArrayList<>();
        int reach = radius + 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx * dx + dz * dz <= reach * reach + 1) {
                    wanted.add(new int[]{centerX + dx, centerZ + dz, dx * dx + dz * dz});
                }
            }
        }
        wanted.sort(Comparator.comparingInt(c -> c[2]));
        for (int[] c : wanted) {
            if (busy.get() >= threads * 2) {
                break;
            }
            long key = Chunk.key(c[0], c[1]);
            Chunk chunk = chunks.get(key);
            if (chunk == null) {
                if (generating.add(key)) {
                    busy.incrementAndGet();
                    pool.execute(() -> {
                        try {
                            generated.add(generate(c[0], c[1]));
                        } finally {
                            busy.decrementAndGet();
                        }
                    });
                }
            } else if (!chunk.meshed && !chunk.meshing && c[2] <= radius * radius + 1 && neighborsReady(c[0], c[1])) {
                chunk.meshing = true;
                busy.incrementAndGet();
                pool.execute(() -> {
                    try {
                        meshed.add(new Object[]{chunk, Mesher.build(chunk, this::id, kinds)});
                    } finally {
                        busy.decrementAndGet();
                    }
                });
            }
        }

        // changed chunks: right away, so a placed block shows in the same frame
        for (Chunk chunk : chunks.values()) {
            if (chunk.dirty && chunk.meshed) {
                chunk.dirty = false;
                show(chunk, Mesher.build(chunk, this::id, kinds));
            }
        }

        // far away: drop
        int drop = radius + 3;
        chunks.values().removeIf(chunk -> {
            int dx = chunk.cx - centerX;
            int dz = chunk.cz - centerZ;
            if (dx * dx + dz * dz > drop * drop && !chunk.meshing) {
                hide(chunk);
                return true;
            }
            return false;
        });
    }

    /** Whether every chunk around has been made (their edge blocks decide which faces show). */
    private boolean neighborsReady(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!chunks.containsKey(Chunk.key(cx + dx, cz + dz))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Darkens (below 1) or restores (1) every block, for the time of day. */
    void brightness(float value) {
        com.dervarex.unweird.style.Color tint = new com.dervarex.unweird.style.Color(value, value, value, 1);
        solid.tint(tint);
        cutout.tint(tint);
        liquid.tint(tint);
    }

    /** Whether the chunk at (x, z) can be seen (generated and meshed). */
    boolean shown(int x, int z) {
        Chunk chunk = chunks.get(Chunk.key(Math.floorDiv(x, Chunk.SIZE), Math.floorDiv(z, Chunk.SIZE)));
        return chunk != null && chunk.meshed;
    }

    private void show(Chunk chunk, Mesh[] meshes) {
        chunk.meshing = false;
        if (chunks.get(chunk.key()) != chunk) {
            return; // dropped meanwhile
        }
        chunk.meshed = true;
        chunk.solid = update(chunk, chunk.solid, meshes[0], solid);
        chunk.cutout = update(chunk, chunk.cutout, meshes[1], cutout);
        chunk.liquid = update(chunk, chunk.liquid, meshes[2], liquid);
    }

    private Node update(Chunk chunk, Node node, Mesh mesh, Material material) {
        if (mesh == null) {
            if (node != null) {
                scene.remove(node);
            }
            return null;
        }
        if (node == null) {
            node = new Node(mesh, material).position(chunk.cx * Chunk.SIZE, 0, chunk.cz * Chunk.SIZE);
            scene.add(node);
        } else {
            node.mesh(mesh);
        }
        return node;
    }

    private void hide(Chunk chunk) {
        for (Node node : new Node[]{chunk.solid, chunk.cutout, chunk.liquid}) {
            if (node != null) {
                scene.remove(node);
            }
        }
        chunk.solid = chunk.cutout = chunk.liquid = null;
        chunk.meshed = false;
    }

    // ---- making chunks ----

    /** Fills a chunk from the terrain, then puts back what was changed there. Runs on a background thread. */
    Chunk generate(int cx, int cz) {
        Chunk chunk = new Chunk(cx, cz);
        if (terrain != null) {
            int baseX = cx * Chunk.SIZE;
            int baseZ = cz * Chunk.SIZE;
            for (int z = 0; z < Chunk.SIZE; z++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    int top = terrain.height(baseX + x, baseZ + z);
                    int highest = Math.min(Chunk.HEIGHT - 1, Math.max(top, terrain.seaLevel));
                    for (int y = 0; y <= highest; y++) {
                        Block block = terrain.block(baseX + x, y, baseZ + z, top);
                        if (block != null) {
                            chunk.set(x, y, z, (short) block.id);
                        }
                    }
                }
            }
            decorate(chunk, baseX, baseZ);
        }
        Map<Integer, Short> changes = edits.get(chunk.key());
        if (changes != null) {
            changes.forEach((index, id) -> chunk.blocks[index] = id);
        }
        return chunk;
    }

    /** Trees and plants: columns a little outside the chunk too, since a tree's crown reaches over. */
    private void decorate(Chunk chunk, int baseX, int baseZ) {
        int margin = 3;
        Part.Area area = new Part.Area() {
            @Override
            public Block get(int x, int y, int z) {
                if (y < 0 || y >= Chunk.HEIGHT) {
                    return null;
                }
                int lx = x - baseX;
                int lz = z - baseZ;
                if (lx >= 0 && lx < Chunk.SIZE && lz >= 0 && lz < Chunk.SIZE) {
                    short id = chunk.get(lx, y, lz);
                    return id == 0 ? null : blocks.get(id - 1);
                }
                return terrain.block(x, y, z, terrain.height(x, z));
            }

            @Override
            public void set(int x, int y, int z, Block block) {
                int lx = x - baseX;
                int lz = z - baseZ;
                if (y >= 0 && y < Chunk.HEIGHT && lx >= 0 && lx < Chunk.SIZE && lz >= 0 && lz < Chunk.SIZE) {
                    chunk.set(lx, y, lz, (short) (block == null ? 0 : block.id));
                }
            }
        };
        for (int z = baseZ - margin; z < baseZ + Chunk.SIZE + margin; z++) {
            for (int x = baseX - margin; x < baseX + Chunk.SIZE + margin; x++) {
                int top = terrain.height(x, z);
                for (Part part : terrain.parts) {
                    part.decorate(x, z, top, area);
                }
            }
        }
    }
}
