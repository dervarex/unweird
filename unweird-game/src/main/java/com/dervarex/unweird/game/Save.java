package com.dervarex.unweird.game;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The file a {@link Saved @Saved} world lives in: seed, the explorer, and every changed block (by block class name,
 * so adding or reordering block fields doesn't mix them up).
 */
final class Save {

    private static final int VERSION = 1;

    private final Path file;
    private final List<Block> blocks;
    private long seed;
    private boolean loaded;
    private double[] explorer;
    private boolean flying;
    private final Map<Long, Map<Integer, String>> edits = new HashMap<>();

    Save(Class<?> game, List<Block> blocks) {
        this(folder(game).resolve("world.dat"), blocks);
    }

    Save(Path file, List<Block> blocks) {
        this.file = file;
        this.blocks = blocks;
        read();
    }

    static Path folder(Class<?> game) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        Path base;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            base = Path.of(appData != null ? appData : home, "unweird-game");
        } else if (os.contains("mac")) {
            base = Path.of(home, "Library", "Application Support", "unweird-game");
        } else {
            String data = System.getenv("XDG_DATA_HOME");
            base = data != null && !data.isBlank() ? Path.of(data, "unweird-game") : Path.of(home, ".local", "share", "unweird-game");
        }
        return base.resolve(game.getName().replace('$', '.'));
    }

    /** The saved seed, or {@code fresh} for a new world. */
    long seed(long fresh) {
        if (!loaded) {
            seed = fresh;
        }
        return seed;
    }

    void applyEdits(Voxels voxels) {
        Map<String, Short> ids = new HashMap<>();
        for (Block block : blocks) {
            ids.put(block.getClass().getName(), (short) block.id);
        }
        edits.forEach((chunk, changes) -> {
            Map<Integer, Short> target = voxels.edits.computeIfAbsent(chunk, k -> new java.util.concurrent.ConcurrentHashMap<>());
            changes.forEach((index, name) -> {
                Short id = name.isEmpty() ? Short.valueOf((short) 0) : ids.get(name);
                if (id != null) {
                    target.put(index, id); // blocks that no longer exist are dropped
                }
            });
        });
    }

    void restore(Explorer explorer) {
        if (this.explorer != null) {
            explorer.at(this.explorer[0], this.explorer[1], this.explorer[2]);
            explorer.yaw = (float) this.explorer[3];
            explorer.pitch = (float) this.explorer[4];
            explorer.flying = flying;
        }
    }

    void write(Voxels voxels, Explorer explorer) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(temporary)))) {
                out.writeInt(VERSION);
                out.writeLong(seed);
                out.writeBoolean(explorer != null && explorer.placed);
                if (explorer != null && explorer.placed) {
                    out.writeDouble(explorer.body.x);
                    out.writeDouble(explorer.body.y);
                    out.writeDouble(explorer.body.z);
                    out.writeDouble(explorer.yaw);
                    out.writeDouble(explorer.pitch);
                    out.writeBoolean(explorer.flying);
                }
                List<String> names = new ArrayList<>();
                names.add("");
                for (Block block : voxels.blocks) {
                    names.add(block.getClass().getName());
                }
                out.writeInt(voxels.edits.size());
                for (var chunk : voxels.edits.entrySet()) {
                    out.writeLong(chunk.getKey());
                    out.writeInt(chunk.getValue().size());
                    for (var change : chunk.getValue().entrySet()) {
                        out.writeInt(change.getKey());
                        out.writeUTF(names.get(change.getValue()));
                    }
                }
            }
            Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("unweird-game: couldn't save the world to " + file + ": " + e.getMessage());
        }
    }

    private void read() {
        if (!Files.exists(file)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != VERSION) {
                System.err.println("unweird-game: " + file + " is from another version, starting a new world");
                return;
            }
            seed = in.readLong();
            if (in.readBoolean()) {
                explorer = new double[]{in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble(), in.readDouble()};
                flying = in.readBoolean();
            }
            int chunks = in.readInt();
            for (int i = 0; i < chunks; i++) {
                long key = in.readLong();
                int count = in.readInt();
                Map<Integer, String> changes = new HashMap<>();
                for (int j = 0; j < count; j++) {
                    changes.put(in.readInt(), in.readUTF());
                }
                edits.put(key, changes);
            }
            loaded = true;
        } catch (IOException e) {
            System.err.println("unweird-game: couldn't read the saved world " + file + " (" + e.getMessage() + "), starting a new one");
            edits.clear();
            explorer = null;
        }
    }
}
