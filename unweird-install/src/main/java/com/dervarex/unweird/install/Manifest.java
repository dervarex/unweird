package com.dervarex.unweird.install;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the installer put into its root directory: {@value #FILE} at its root. Together with the files on disk,
 * this is the only source of truth for "installed". All methods are synchronized; callers that need several
 * steps to be atomic synchronize on the manifest too.
 */
final class Manifest {

    static final String FILE = ".unweird-installed.json";
    private static final int FORMAT = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * @param path     relative to the game directory, with '/' separators
     * @param optional not checked when deciding "installed" (a modpack's override files, which users may edit or delete)
     */
    record FileRef(String path, String sha1, boolean optional) {
    }

    record Entry(String id, String title, String kind, String kindLabel, String iconUrl, String version,
                 List<FileRef> files, boolean explicit, Set<String> requiredBy, Map<String, String> info) {

        Entry {
            files = files == null ? List.of() : List.copyOf(files);
            requiredBy = requiredBy == null ? Set.of() : Set.copyOf(requiredBy);
            info = info == null ? Map.of() : Map.copyOf(info);
        }

        Entry withRequiredBy(Set<String> next) {
            return new Entry(id, title, kind, kindLabel, iconUrl, version, files, explicit, next, info);
        }

        Entry withExplicit(boolean next) {
            return new Entry(id, title, kind, kindLabel, iconUrl, version, files, next, requiredBy, info);
        }

        Item item() {
            return new Item(id, new Kind(kind, kindLabel), title, null, null, iconUrl, null);
        }
    }

    private record Data(int format, Map<String, Entry> projects) {
    }

    private final Path root;
    private final Path file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    private Manifest(Path root) {
        this.root = root;
        this.file = root.resolve(FILE);
    }

    static Manifest load(Path root) {
        Manifest manifest = new Manifest(root);
        if (!Files.isRegularFile(manifest.file)) {
            return manifest;
        }
        try {
            Data data = GSON.fromJson(Files.readString(manifest.file, StandardCharsets.UTF_8), Data.class);
            if (data != null && data.projects() != null) {
                manifest.entries.putAll(data.projects());
            }
        } catch (IOException | JsonParseException e) {
            // Don't lose it, don't crash: keep a copy and start over. Files on disk can be adopted again.
            Path broken = manifest.file.resolveSibling(FILE + ".broken-" + System.currentTimeMillis());
            try {
                Files.move(manifest.file, broken);
            } catch (IOException ignored) {
                // nothing more we can do
            }
            System.err.println("unweird-install: " + manifest.file + " was unreadable (" + e.getMessage() + "), moved it to " + broken);
        }
        return manifest;
    }

    synchronized Entry get(String id) {
        return entries.get(id);
    }

    synchronized void put(Entry entry) {
        entries.put(entry.id(), entry);
    }

    synchronized Entry remove(String id) {
        return entries.remove(id);
    }

    synchronized List<Entry> all() {
        return List.copyOf(entries.values());
    }

    /** Whether every non-optional file of the entry is on disk. */
    boolean present(Entry entry) {
        if (entry.files().isEmpty()) {
            return false;
        }
        for (FileRef ref : entry.files()) {
            if (!ref.optional() && !Files.isRegularFile(resolve(ref.path()))) {
                return false;
            }
        }
        return true;
    }

    /** Paths (relative) that some entry other than {@code except} owns. */
    synchronized Set<String> pathsOwnedByOthers(String except) {
        Set<String> paths = new HashSet<>();
        for (Entry entry : entries.values()) {
            if (!entry.id().equals(except)) {
                entry.files().forEach(ref -> paths.add(ref.path()));
            }
        }
        return paths;
    }

    synchronized Set<String> allPaths() {
        Set<String> paths = new LinkedHashSet<>();
        entries.values().forEach(entry -> entry.files().forEach(ref -> paths.add(ref.path())));
        return paths;
    }

    Path resolve(String relative) {
        return root.resolve(relative);
    }

    String relativize(Path path) {
        return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    /** Writes the manifest atomically (temp file + move). */
    synchronized void save() throws IOException {
        Files.createDirectories(root);
        Path temp = file.resolveSibling(FILE + ".tmp");
        Files.writeString(temp, GSON.toJson(new Data(FORMAT, new LinkedHashMap<>(entries))), StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
