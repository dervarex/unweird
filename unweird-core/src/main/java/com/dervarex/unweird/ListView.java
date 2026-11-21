package com.dervarex.unweird;

import com.dervarex.unweird.style.Theme;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.lwjgl.glfw.GLFW.*;

/**
 * A list of items to pick from. Give it a list, {@code new ListView<>(List.of(...))}, or a live one,
 * {@code new ListView<>(() -> tasks)}, which is re-read after every event.
 * Click a row or use the arrow keys to select; {@link #onSelect} hears about it.
 */
public class ListView<T> extends Widget {

    static final float ROW = 40;
    private static final float PADDING_X = 14;
    private static final float FONT_SIZE = 15;
    private static final float EMPTY_HEIGHT = 64;

    private final Supplier<List<T>> source;
    private final List<Consumer<T>> handlers = new CopyOnWriteArrayList<>();
    private volatile List<T> items = List.of();
    private Function<T, String> labels = String::valueOf;
    private String emptyText = "Nothing here yet";

    private volatile T selected;
    /** Row under the mouse, -1 if none. */
    private int hoverRow = -1;
    /** Row the mouse went down on. */
    private int pressedRow = -1;

    public ListView(List<T> items) {
        this.source = null;
        this.items = List.copyOf(items);
    }

    public ListView(Supplier<List<T>> items) {
        this.source = Objects.requireNonNull(items, "items");
    }

    public List<T> items() {
        return items;
    }

    /** The selected item, or null. */
    public T selected() {
        return selected;
    }

    /** Selects an item without calling the handlers. {@code null} clears the selection. */
    public ListView<T> select(T item) {
        selected = item;
        return this;
    }

    /** How items are shown. Default: {@code String.valueOf}. */
    public ListView<T> labels(Function<T, String> labels) {
        this.labels = Objects.requireNonNull(labels, "labels");
        return this;
    }

    /** What to show while the list is empty. */
    public ListView<T> empty(String text) {
        this.emptyText = Objects.requireNonNull(text, "text");
        return this;
    }

    /** Runs {@code handler} whenever the user selects a different item. */
    public ListView<T> onSelect(Consumer<T> handler) {
        handlers.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    @Override public ListView<T> below(Widget other) { super.below(other); return this; }
    @Override public ListView<T> above(Widget other) { super.above(other); return this; }
    @Override public ListView<T> rightOf(Widget other) { super.rightOf(other); return this; }
    @Override public ListView<T> leftOf(Widget other) { super.leftOf(other); return this; }
    @Override public ListView<T> alignLeft(Widget other) { super.alignLeft(other); return this; }
    @Override public ListView<T> alignRight(Widget other) { super.alignRight(other); return this; }
    @Override public ListView<T> gap(float pixels) { super.gap(pixels); return this; }
    @Override public ListView<T> visibleIf(java.util.function.BooleanSupplier condition) { super.visibleIf(condition); return this; }
    @Override public ListView<T> enabledIf(java.util.function.BooleanSupplier condition) { super.enabledIf(condition); return this; }
    @Override public ListView<T> tooltip(String text) { super.tooltip(text); return this; }

    String labelOf(T item) {
        return String.valueOf(labels.apply(item));
    }

    @Override
    protected void update() {
        if (source != null) {
            List<T> next = source.get();
            items = next == null ? List.of() : List.copyOf(next);
        }
        if (selected != null && !items.contains(selected)) {
            selected = null; // the selected item was removed
        }
    }

    // ---- input ----

    @Override
    protected boolean clickable() {
        return true;
    }

    @Override
    protected boolean focusable() {
        return true;
    }

    @Override
    protected Cursor cursor() {
        return items.isEmpty() ? Cursor.ARROW : Cursor.HAND;
    }

    private int rowAt(float py) {
        int row = (int) Math.floor((py - getY() - 1) / ROW);
        return row >= 0 && row < items.size() ? row : -1;
    }

    @Override
    boolean pointerMove(Pointer pointer) {
        int row = rowAt(pointer.y());
        if (row == hoverRow) {
            return false;
        }
        hoverRow = row;
        return true;
    }

    @Override
    void pointerDown(Pointer pointer) {
        pressedRow = pointer.button() == Pointer.Button.LEFT ? rowAt(pointer.y()) : -1;
    }

    @Override
    void activate(Host host) {
        if (pressedRow >= 0) {
            choose(pressedRow, host);
        }
    }

    @Override
    boolean keyPressed(int key, int mods, Host host) {
        if (items.isEmpty()) {
            return false;
        }
        int current = selected == null ? -1 : items.indexOf(selected);
        int next = switch (key) {
            case GLFW_KEY_DOWN -> Math.min(items.size() - 1, current + 1);
            case GLFW_KEY_UP -> current < 0 ? items.size() - 1 : Math.max(0, current - 1);
            case GLFW_KEY_HOME -> 0;
            case GLFW_KEY_END -> items.size() - 1;
            default -> -2;
        };
        if (next == -2) {
            return false;
        }
        choose(next, host);
        return true;
    }

    void choose(int index, Host host) {
        List<T> current = items;
        if (index < 0 || index >= current.size()) {
            return;
        }
        T item = current.get(index);
        if (Objects.equals(item, selected)) {
            return;
        }
        selected = item;
        for (Consumer<T> handler : handlers) {
            host.safely(this, () -> handler.accept(item));
        }
    }

    // ---- drawing ----

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        float height = items.isEmpty() ? EMPTY_HEIGHT : items.size() * ROW + 2;
        return new Size(maxWidth, height);
    }

    @Override
    protected void draw(Canvas canvas) {
        Theme theme = canvas.getTheme();
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        float radius = theme.getRadius();

        canvas.fillRect(x, y, w, h, radius, theme.getSurface());
        canvas.strokeRect(x, y, w, h, radius, isFocused() ? 2 : 1, isFocused() ? theme.getAccent() : theme.getBorder());

        List<T> current = items;
        if (current.isEmpty()) {
            canvas.label(emptyText, x + w / 2, y + h / 2, Canvas.Weight.REGULAR, FONT_SIZE, theme.getMutedText());
            return;
        }
        canvas.clip(x + 1, y + 1, w - 2, h - 2);
        T chosen = selected;
        for (int i = 0; i < current.size(); i++) {
            float rowY = y + 1 + i * ROW;
            boolean isSelected = Objects.equals(current.get(i), chosen);
            if (isSelected) {
                canvas.fillRect(x + 1, rowY, w - 2, ROW, 0, theme.getAccent().withAlpha(0.14f));
            } else if (isHovered() && i == hoverRow) {
                canvas.fillRect(x + 1, rowY, w - 2, ROW, 0, theme.getText().withAlpha(0.05f));
            }
            if (i > 0) {
                canvas.fillRect(x + PADDING_X, rowY, w - PADDING_X * 2, 1, 0, theme.getBorder());
            }
            canvas.textLine(labelOf(current.get(i)), x + PADDING_X, rowY + ROW / 2,
                    isSelected ? Canvas.Weight.SEMIBOLD : Canvas.Weight.REGULAR, FONT_SIZE,
                    isSelected ? theme.getAccent() : theme.getText());
        }
        canvas.unclip();
    }
}
