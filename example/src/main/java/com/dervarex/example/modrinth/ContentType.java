package com.dervarex.example.modrinth;

import java.util.List;

/** The kinds of Modrinth projects that can be installed, and where each goes inside the game directory. */
public enum ContentType {

    MOD("Mods", "mod", "mods"),
    RESOURCE_PACK("Resource packs", "resourcepack", "resourcepacks"),
    SHADER("Shaders", "shader", "shaderpacks"),
    DATAPACK("Data packs", "datapack", "datapacks"),
    /** Installs into the game directory itself: its files, plus the pack's overrides. */
    MODPACK("Modpacks", "modpack", "");

    private final String label;
    private final String projectType;
    private final String folder;

    ContentType(String label, String projectType, String folder) {
        this.label = label;
        this.projectType = projectType;
        this.folder = folder;
    }

    /** Human name, plural: "Resource packs". */
    public String label() {
        return label;
    }

    /** Modrinth's project type, as used in search facets. */
    public String projectType() {
        return projectType;
    }

    /** Folder inside the game directory ("" for modpacks, which go into the game directory itself). */
    public String folder() {
        return folder;
    }

    /** Loaders to ask Modrinth for when picking a version, or an empty list for "any". */
    List<String> versionLoaders(Target target) {
        return switch (this) {
            case MOD -> target.loader() == null ? List.of() : List.of(target.loader());
            case DATAPACK -> List.of("datapack");
            default -> List.of();
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
