package com.dervarex.example.modrinth;

/**
 * A project as Modrinth's search returns it.
 *
 * @param projectId   stable id, e.g. {@code "P7dR8mSH"}
 * @param slug        URL name, e.g. {@code "fabric-api"}
 * @param projectType Modrinth's type ({@code "mod"}, {@code "resourcepack"}, ...)
 */
public record Project(
        String projectId,
        String slug,
        String title,
        String description,
        String author,
        long downloads,
        String iconUrl,
        String projectType) {

    /** Short download count: "1.2M", "34K", "980". */
    public String downloadsShort() {
        if (downloads >= 1_000_000) {
            return trim(downloads / 1_000_000.0) + "M";
        }
        if (downloads >= 1_000) {
            return trim(downloads / 1_000.0) + "K";
        }
        return Long.toString(downloads);
    }

    private static String trim(double value) {
        return value >= 100 ? Long.toString(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value).replace(".0", "");
    }
}
