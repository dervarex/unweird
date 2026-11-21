package com.dervarex.unweird.install;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What installing an {@link Item} means: files to download (checked against their hash), other items it
 * needs, and archives to unpack. Built by {@link Source#release}.
 *
 * <pre>{@code
 * return Release.version("2.1.0")
 *         .download(file.url(), "mods/" + file.filename(), file.sha1(), file.size())
 *         .requires(Item.of("fabric-api", MODS))
 *         .build();
 * }</pre>
 *
 * All paths are relative to the installer's root directory; anything that would end up outside it is refused.
 */
public record Release(String version, List<Download> downloads, List<Item> requires, List<Unpack> unpack,
                      Map<String, String> info, List<Path> temporary) {

    /**
     * A file to download.
     *
     * @param path     where it goes, relative to the root, '/'-separated
     * @param sha1     expected SHA-1 (hex); null skips the check (not recommended)
     * @param size     expected size in bytes for progress, or 0 if unknown
     */
    public record Download(String url, String path, String sha1, long size) {
        public Download {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(path, "path");
        }
    }

    /**
     * Extracts the entries of {@code archive} under {@code from} (e.g. {@code "overrides/"}) into {@code to}
     * (relative to the root, "" for the root itself). Unpacked files may be edited or deleted by users later,
     * so they don't count when deciding whether the item is still installed.
     */
    public record Unpack(Path archive, String from, String to) {
        public Unpack {
            Objects.requireNonNull(archive, "archive");
            from = from == null ? "" : from;
            to = to == null ? "" : to;
        }
    }

    public Release {
        downloads = List.copyOf(downloads);
        requires = List.copyOf(requires);
        unpack = List.copyOf(unpack);
        info = Map.copyOf(info);
        temporary = List.copyOf(temporary);
    }

    public static Builder version(String version) {
        return new Builder(version);
    }

    public static final class Builder {
        private final String version;
        private final List<Download> downloads = new ArrayList<>();
        private final List<Item> requires = new ArrayList<>();
        private final List<Unpack> unpack = new ArrayList<>();
        private final Map<String, String> info = new LinkedHashMap<>();
        private final List<Path> temporary = new ArrayList<>();

        private Builder(String version) {
            this.version = version;
        }

        public Builder download(String url, String path, String sha1, long size) {
            downloads.add(new Download(url, path, sha1, size));
            return this;
        }

        /** Another item that must be installed too. Already installed ones are left alone. */
        public Builder requires(Item item) {
            requires.add(Objects.requireNonNull(item, "item"));
            return this;
        }

        public Builder unpack(Path archive, String from, String to) {
            unpack.add(new Unpack(archive, from, to));
            return this;
        }

        /** Extra facts kept with the installed item, e.g. {@code "minecraft" -> "1.21.1"} for a modpack. */
        public Builder info(String key, String value) {
            info.put(key, value);
            return this;
        }

        /** A file the source downloaded while building the release (e.g. a pack to unpack); deleted after installing. */
        public Builder temporary(Path file) {
            temporary.add(file);
            return this;
        }

        public Release build() {
            return new Release(version, downloads, requires, unpack, info, temporary);
        }
    }
}
