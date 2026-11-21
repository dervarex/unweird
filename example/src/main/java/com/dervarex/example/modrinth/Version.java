package com.dervarex.example.modrinth;

import java.util.List;
import java.util.Map;

/** One release of a project, with its files and dependencies. */
public record Version(
        String id,
        String projectId,
        String name,
        String versionNumber,
        String datePublished,
        List<String> gameVersions,
        List<String> loaders,
        List<File> files,
        List<Dependency> dependencies) {

    public record File(String url, String filename, boolean primary, long size, Map<String, String> hashes) {

        public String sha1() {
            return hashes == null ? null : hashes.get("sha1");
        }

        public String sha512() {
            return hashes == null ? null : hashes.get("sha512");
        }
    }

    /**
     * @param dependencyType {@code required}, {@code optional}, {@code incompatible} or {@code embedded}
     */
    public record Dependency(String versionId, String projectId, String fileName, String dependencyType) {

        public boolean required() {
            return "required".equals(dependencyType);
        }
    }

    /** The file to install: the one marked primary, else the first. */
    public File primaryFile() {
        if (files == null || files.isEmpty()) {
            throw new ModrinthException("Version " + versionNumber + " has no files");
        }
        return files.stream().filter(File::primary).findFirst().orElse(files.getFirst());
    }

    public List<Dependency> dependencies() {
        return dependencies == null ? List.of() : dependencies;
    }
}
