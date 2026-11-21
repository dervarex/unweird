package com.dervarex.unweird.install;

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Dropdown;
import com.dervarex.unweird.Each;
import com.dervarex.unweird.Field;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.Unweird;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Search a {@link Source}, pick a {@link Kind}, install and remove items. Drop it into any screen:
 *
 * <pre>{@code
 * public class ModsScreen extends Screen {
 *     Text heading = new Text("Mods");
 *     ContentBrowser browser = new ContentBrowser(installer);
 * }
 * }</pre>
 *
 * Searching happens in the background; only the newest search's results are shown. The first search
 * (most popular items of the first kind) starts when the browser is first shown.
 */
public class ContentBrowser extends Component {

    @Getter private final Installer installer;
    private final Source source;
    private final AtomicInteger request = new AtomicInteger();

    private volatile List<Item> items = List.of();
    private volatile int total;
    private volatile boolean loading;
    private volatile String problem = "";
    private boolean started;

    @Getter private final Field search;
    @Getter private final Dropdown<Kind> kind;
    @Getter private final Text status;
    @Getter private final Each<Item> results;
    @Getter private final Button more;

    public ContentBrowser(Installer installer) {
        this.installer = Objects.requireNonNull(installer, "installer");
        this.source = installer.source();
        List<Kind> kinds = source.kinds();
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("The source offers no kinds to browse");
        }
        this.search = new Field("Search").onSubmit(() -> search(false));
        this.kind = new Dropdown<>("Kind", kinds).select(kinds.getFirst()).onChange(k -> search(false));
        kind.rightOf(search).visibleIf(() -> kinds.size() > 1);
        this.status = new Text(this::statusLine).muted();
        this.results = new Each<>(() -> items, item -> new ItemCard(item, installer));
        this.more = new Button(() -> "Load more (" + (total - items.size()) + " left)").secondary()
                .visibleIf(() -> !loading && problem.isEmpty() && items.size() < total);
        more.onClick(() -> search(true));
    }

    /** The items shown right now. */
    public List<Item> items() {
        return items;
    }

    private String statusLine() {
        if (loading) {
            return items.isEmpty() ? "Searching…" : "Loading…";
        }
        if (!problem.isEmpty()) {
            return problem;
        }
        return started && items.isEmpty() ? "Nothing found." : "";
    }

    @Override
    protected void update() {
        if (!started) {
            started = true;
            search(false);
        }
        super.update();
    }

    /** Runs the search for the current query and kind; {@code append} loads the next page. */
    public void search(boolean append) {
        int id = request.incrementAndGet();
        String query = search.value();
        Kind which = kind.selected() != null ? kind.selected() : source.kinds().getFirst();
        int offset = append ? items.size() : 0;
        loading = true;
        problem = "";
        if (!append) {
            items = List.of();
            total = 0;
        }
        Thread.ofVirtual().name("unweird-search").start(() -> {
            try {
                Results found = source.search(query, which, offset);
                if (id != request.get()) {
                    return; // a newer search started meanwhile
                }
                items = append ? merge(items, found.items()) : distinct(found.items());
                total = Math.max(found.total(), items.size());
            } catch (RuntimeException e) {
                if (id == request.get()) {
                    problem = "Couldn't search: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
            } finally {
                if (id == request.get()) {
                    loading = false;
                }
                Unweird.refresh();
            }
        });
    }

    /** Results can repeat across pages when the source's ranking shifts; keep the first of each id. */
    private static List<Item> merge(List<Item> before, List<Item> page) {
        List<Item> all = new ArrayList<>(before);
        all.addAll(page);
        return distinct(all);
    }

    private static List<Item> distinct(List<Item> list) {
        Set<String> seen = new LinkedHashSet<>();
        List<Item> out = new ArrayList<>();
        for (Item item : list) {
            if (seen.add(item.id())) {
                out.add(item);
            }
        }
        return List.copyOf(out);
    }
}
