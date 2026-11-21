package com.dervarex.example.world;

import com.dervarex.unweird.scene3d.Mesh;
import com.dervarex.unweird.style.Color;

/** A small block world: a grid of block types and a mesh of only the faces you can see. */
public final class VoxelWorld {

    public enum Block {
        AIR("Air", null, null),
        GRASS("Grass", Color.hex("#5fae3e"), Color.hex("#8a6a43")),
        DIRT("Dirt", Color.hex("#8a6a43"), null),
        STONE("Stone", Color.hex("#8d8d91"), null),
        PLANKS("Planks", Color.hex("#b68a4f"), null),
        LOG("Log", Color.hex("#a07c4a"), Color.hex("#6b4f2d")),
        LEAVES("Leaves", Color.hex("#3f8f3a"), null),
        SAND("Sand", Color.hex("#e3d39a"), null),
        BRICK("Bricks", Color.hex("#a5473a"), null);

        final String label;
        final Color top;
        final Color sides;

        Block(String label, Color top, Color sides) {
            this.label = label;
            this.top = top;
            this.sides = sides == null ? top : sides;
        }

        public String label() {
            return label;
        }
    }

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final Block[] blocks;

    public VoxelWorld(int sizeX, int sizeY, int sizeZ) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = new Block[sizeX * sizeY * sizeZ];
        java.util.Arrays.fill(blocks, Block.AIR);
    }

    /** Rolling grass, dirt and stone, a sandy corner and a tree. */
    public static VoxelWorld sample() {
        VoxelWorld world = new VoxelWorld(16, 10, 16);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int height = 2 + (int) Math.round(1.2 * Math.sin(x * 0.45) + Math.cos(z * 0.38));
                for (int y = 0; y <= height; y++) {
                    Block block = y == height ? (x + z < 6 ? Block.SAND : Block.GRASS) : y >= height - 1 ? Block.DIRT : Block.STONE;
                    world.set(x, y, z, block);
                }
            }
        }
        int ground = world.top(11, 10);
        for (int y = 1; y <= 4; y++) {
            world.set(11, ground + y, 10, Block.LOG);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 3; dy <= 5; dy++) {
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    if (!corner && world.get(11 + dx, ground + dy, 10 + dz) == Block.AIR) {
                        world.set(11 + dx, ground + dy, 10 + dz, Block.LEAVES);
                    }
                }
            }
        }
        return world;
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    public Block get(int x, int y, int z) {
        return inside(x, y, z) ? blocks[(y * sizeZ + z) * sizeX + x] : Block.AIR;
    }

    /** Returns whether anything changed (false outside the world). */
    public boolean set(int x, int y, int z, Block block) {
        if (!inside(x, y, z) || get(x, y, z) == block) {
            return false;
        }
        blocks[(y * sizeZ + z) * sizeX + x] = block;
        return true;
    }

    public int count() {
        int count = 0;
        for (Block block : blocks) {
            if (block != Block.AIR) {
                count++;
            }
        }
        return count;
    }

    private int top(int x, int z) {
        for (int y = sizeY - 1; y >= 0; y--) {
            if (get(x, y, z) != Block.AIR) {
                return y;
            }
        }
        return 0;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeZ() {
        return sizeZ;
    }

    /** One mesh for the whole world, with only the faces that touch air. */
    public Mesh mesh() {
        Mesh.Builder builder = Mesh.builder();
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    Block block = get(x, y, z);
                    if (block == Block.AIR) {
                        continue;
                    }
                    for (Mesh.BlockFace face : Mesh.BlockFace.values()) {
                        if (get(x + face.dx, y + face.dy, z + face.dz) == Block.AIR) {
                            Color color = face == Mesh.BlockFace.UP ? block.top : face == Mesh.BlockFace.DOWN ? block.sides.mix(Color.BLACK, 0.2f) : block.sides;
                            builder.blockFace(x, y, z, face, color);
                        }
                    }
                }
            }
        }
        return builder.build();
    }
}
