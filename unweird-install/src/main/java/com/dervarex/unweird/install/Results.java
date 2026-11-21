package com.dervarex.unweird.install;

import java.util.List;

/**
 * One page of search results.
 *
 * @param total how many results there are in all (for "Load more")
 */
public record Results(List<Item> items, int total) {

    public Results {
        items = List.copyOf(items);
    }
}
