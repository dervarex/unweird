package com.dervarex.unweird.install;

import java.util.Objects;

/**
 * Something that can be installed, as a {@link Source} lists it.
 *
 * @param id          unique within the source (installed state is tracked by it)
 * @param detail      a short extra line, e.g. "1.2M downloads"; may be null
 * @param iconUrl     http(s) URL of an icon; may be null
 */
public record Item(String id, Kind kind, String title, String description, String author, String iconUrl, String detail) {

    public Item {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        title = title == null || title.isBlank() ? id : title;
        iconUrl = iconUrl == null || iconUrl.isBlank() ? null : iconUrl; // catalogs often send "" for "no icon"
    }

    /** An item known only by id, e.g. a dependency; the source fills in the rest when it builds the release. */
    public static Item of(String id, Kind kind) {
        return new Item(id, kind, null, null, null, null, null);
    }
}
