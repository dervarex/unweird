package com.dervarex.unweird.install;

import java.util.List;

/**
 * Where installable things come from: Modrinth, CurseForge, a company server, a folder of zips, ...
 * Implement these three methods and {@link Installer} and {@link ContentBrowser} do the rest.
 *
 * <p>{@link #search} and {@link #release} are called on background threads and may block (network, disk).
 * Throw any exception with a readable message when something goes wrong; it's shown to the user.
 */
public interface Source {

    /** The categories to browse, e.g. mods, resource packs. At least one. */
    List<Kind> kinds();

    /** Finds items of one kind. An empty query should list something sensible (popular, newest). */
    Results search(String query, Kind kind, int offset);

    /** What installing {@code item} means right now: the newest fitting version's files, dependencies, archives. */
    Release release(Item item);

    /** Sent as User-Agent when the installer downloads files. Many APIs want one that identifies your app. */
    default String userAgent() {
        return "unweird";
    }
}
