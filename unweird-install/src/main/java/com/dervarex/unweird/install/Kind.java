package com.dervarex.unweird.install;

import java.util.Objects;

/**
 * A category of things a {@link Source} offers, e.g. "Mods" or "Resource packs".
 *
 * @param id    stable id, stored with installed items
 * @param label shown to users
 */
public record Kind(String id, String label) {

    public Kind {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
    }

    @Override
    public String toString() {
        return label;
    }
}
