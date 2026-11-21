package com.dervarex.unweird;

import com.dervarex.unweird.style.Theme;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Pick one option from a list. {@code new Dropdown<>("Language", List.of("English", "Deutsch"))};
 * read it with {@link #selected()}. Options can be any type; {@link #labels} decides how they're shown.
 */
public class Dropdown<T> extends Widget {

    static final float HEIGHT = 40;
    static final float PADDING_X = 12;
    static final float FONT_SIZE = 15;

    private final String placeholder;
    private final List<Consumer<T>> handlers = new CopyOnWriteArrayList<>();
    private volatile List<T> options;
    private volatile T selected;
    private Function<T, String> labels = String::valueOf;

    /** Non-null while the list is open. */
    private Popup popup;

    public Dropdown(String placeholder, List<T> options) {
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
        this.options = List.copyOf(options);
    }

    /** The chosen option, or null if nothing is chosen yet. */
    public T selected() {
        return selected;
    }

    /** Chooses an option without calling the change handlers. {@code null} clears the choice. */
    public Dropdown<T> select(T option) {
        if (option != null && !options.contains(option)) {
            throw new IllegalArgumentException(option + " isn't one of this dropdown's options");
        }
        selected = option;
        return this;
    }

    public List<T> options() {
        return options;
    }

    /** Replaces the options. A chosen option that's no longer there is cleared. */
    public Dropdown<T> options(List<T> next) {
        options = List.copyOf(next);
        if (selected != null && !options.contains(selected)) {
            selected = null;
        }
        return this;
    }

    /** How options are shown, e.g. {@code .labels(Language::displayName)}. Default: {@code String.valueOf}. */
    public Dropdown<T> labels(Function<T, String> labels) {
        this.labels = Objects.requireNonNull(labels, "labels");
        return this;
    }

    /** Runs {@code handler} with the newly chosen option whenever the user picks one. */
    public Dropdown<T> onChange(Consumer<T> handler) {
        handlers.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    public String placeholder() {
        return placeholder;
    }

    @Override public Dropdown<T> below(Widget other) { super.below(other); return this; }
    @Override public Dropdown<T> above(Widget other) { super.above(other); return this; }
    @Override public Dropdown<T> rightOf(Widget other) { super.rightOf(other); return this; }
    @Override public Dropdown<T> leftOf(Widget other) { super.leftOf(other); return this; }
    @Override public Dropdown<T> alignLeft(Widget other) { super.alignLeft(other); return this; }
    @Override public Dropdown<T> alignRight(Widget other) { super.alignRight(other); return this; }
    @Override public Dropdown<T> gap(float pixels) { super.gap(pixels); return this; }
    @Override public Dropdown<T> visibleIf(java.util.function.BooleanSupplier condition) { super.visibleIf(condition); return this; }
    @Override public Dropdown<T> enabledIf(java.util.function.BooleanSupplier condition) { super.enabledIf(condition); return this; }

    String labelOf(T option) {
        return String.valueOf(labels.apply(option));
    }

    boolean isOpen() {
        return popup != null;
    }

    // ---- input ----

    @Override
    protected boolean clickable() {
        return true;
    }

    @Override
    boolean focusable() {
        return true;
    }

    @Override
    void activate(Host host) {
        if (popup != null) {
            host.closeOverlay();
        } else {
            open(host);
        }
    }

    @Override
    boolean keyPressed(int key, int mods, Host host) {
        if (popup == null) {
            if (key == GLFW_KEY_SPACE || key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER || key == GLFW_KEY_DOWN || key == GLFW_KEY_UP) {
                open(host);
                return true;
            }
            return false;
        }
        switch (key) {
            case GLFW_KEY_DOWN -> popup.move(1);
            case GLFW_KEY_UP -> popup.move(-1);
            case GLFW_KEY_HOME -> popup.moveTo(0);
            case GLFW_KEY_END -> popup.moveTo(options.size() - 1);
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER, GLFW_KEY_SPACE -> choose(popup.highlighted, host);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void open(Host host) {
        if (options.isEmpty()) {
            return;
        }
        Popup next = new Popup();
        T current = selected;
        next.highlighted = current == null ? 0 : Math.max(0, options.indexOf(current)); // immutable lists reject indexOf(null)
        popup = next;
        host.openOverlay(next);
    }

    /** Picks the option at {@code index}, closes the list and tells the handlers (if the choice changed). */
    void choose(int index, Host host) {
        List<T> current = options;
        host.closeOverlay();
        if (index < 0 || index >= current.size()) {
            return;
        }
        T option = current.get(index);
        if (Objects.equals(option, selected)) {
            return;
        }
        selected = option;
        for (Consumer<T> handler : handlers) {
            host.safely(this, () -> handler.accept(option));
        }
    }

    // ---- drawing ----

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        float widest = canvas.textWidth(placeholder, Canvas.Weight.REGULAR, FONT_SIZE);
        for (T option : options) {
            widest = Math.max(widest, canvas.textWidth(labelOf(option), Canvas.Weight.REGULAR, FONT_SIZE));
        }
        return new Size(Math.min(widest + PADDING_X * 2 + 24, maxWidth), HEIGHT);
    }

    @Override
    protected void draw(Canvas canvas) {
        Theme theme = canvas.getTheme();
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        float centerY = y + h / 2;

        canvas.fillRect(x, y, w, h, theme.getRadius(), theme.getBackground());
        if (isFocused() || isOpen()) {
            canvas.strokeRect(x, y, w, h, theme.getRadius(), 2, theme.getAccent());
        } else {
            canvas.strokeRect(x, y, w, h, theme.getRadius(), 1, isHovered() ? theme.getMutedText().withAlpha(0.5f) : theme.getBorder());
        }

        T current = selected;
        canvas.clip(x + PADDING_X, y, Math.max(0, w - PADDING_X * 2 - 20), h);
        if (current == null) {
            canvas.textLine(placeholder, x + PADDING_X, centerY, Canvas.Weight.REGULAR, FONT_SIZE, theme.getMutedText());
        } else {
            canvas.textLine(labelOf(current), x + PADDING_X, centerY, Canvas.Weight.REGULAR, FONT_SIZE, theme.getText());
        }
        canvas.unclip();

        float chevronX = x + w - PADDING_X - 10;
        float flip = isOpen() ? -1 : 1;
        canvas.polyline(new float[]{
                chevronX, centerY - 2.5f * flip,
                chevronX + 5, centerY + 2.5f * flip,
                chevronX + 10, centerY - 2.5f * flip}, 1.75f, theme.getMutedText());
    }

    /** The open list of options. */
    final class Popup extends Overlay {

        static final float ROW = 36;
        static final int VISIBLE_ROWS = 8;
        static final float INSET = 4;

        int highlighted;
        /** Index of the first visible row. */
        int first;

        @Override
        Widget owner() {
            return Dropdown.this;
        }

        @Override
        void closed() {
            if (popup == this) {
                popup = null;
            }
        }

        @Override
        void position(Canvas canvas, float windowWidth, float windowHeight) {
            int rows = Math.min(VISIBLE_ROWS, options.size());
            float height = rows * ROW + INSET * 2;
            float below = Dropdown.this.getY() + Dropdown.this.getHeight() + 4;
            float above = Dropdown.this.getY() - 4 - height;
            float y = below + height <= windowHeight - 8 || above < 8 ? below : above;
            place(canvas, Dropdown.this.getX(), y, Dropdown.this.getWidth(), height);
        }

        void move(int delta) {
            moveTo(highlighted + delta);
        }

        void moveTo(int index) {
            highlighted = Math.max(0, Math.min(options.size() - 1, index));
            if (highlighted < first) {
                first = highlighted;
            } else if (highlighted >= first + VISIBLE_ROWS) {
                first = highlighted - VISIBLE_ROWS + 1;
            }
        }

        private int rowAt(float py) {
            return first + (int) Math.floor((py - getY() - INSET) / ROW);
        }

        @Override
        boolean mouseMoved(float px, float py) {
            int row = rowAt(py);
            if (row >= 0 && row < options.size() && row != highlighted) {
                highlighted = row;
                return true;
            }
            return false;
        }

        @Override
        boolean scrolled(double steps) {
            int max = Math.max(0, options.size() - VISIBLE_ROWS);
            int next = (int) Math.max(0, Math.min(max, first - Math.round(steps)));
            if (next == first) {
                return false;
            }
            first = next;
            return true;
        }

        @Override
        void activate(Host host) {
            choose(highlighted, host);
        }

        @Override
        protected Size measure(Canvas canvas, float maxWidth) {
            return new Size(getWidth(), getHeight());
        }

        @Override
        protected void draw(Canvas canvas) {
            Theme theme = canvas.getTheme();
            float x = getX();
            float y = getY();
            float w = getWidth();
            float h = getHeight();
            float radius = theme.getRadius();

            canvas.shadow(x, y, w, h, radius, 16, theme.getShadow().withAlpha(Math.min(1, theme.getShadow().a() * 2)));
            canvas.fillRect(x, y, w, h, radius, theme.getSurface());
            canvas.strokeRect(x, y, w, h, radius, 1, theme.getBorder());

            canvas.clip(x, y + INSET, w, h - INSET * 2);
            T current = selected;
            int last = Math.min(options.size(), first + VISIBLE_ROWS);
            for (int i = first; i < last; i++) {
                T option = options.get(i);
                float rowY = y + INSET + (i - first) * ROW;
                if (i == highlighted) {
                    canvas.fillRect(x + INSET, rowY, w - INSET * 2, ROW, radius - 2, theme.getText().withAlpha(0.07f));
                }
                boolean chosen = Objects.equals(option, current);
                canvas.textLine(labelOf(option), x + PADDING_X, rowY + ROW / 2, chosen ? Canvas.Weight.SEMIBOLD : Canvas.Weight.REGULAR,
                        FONT_SIZE, chosen ? theme.getAccent() : theme.getText());
                if (chosen) {
                    float cx = x + w - PADDING_X - 12;
                    float cy = rowY + ROW / 2;
                    canvas.polyline(new float[]{cx, cy, cx + 3.5f, cy + 3.5f, cx + 10, cy - 4}, 2, theme.getAccent());
                }
            }
            canvas.unclip();

            if (options.size() > VISIBLE_ROWS) {
                float track = h - INSET * 4;
                float thumb = Math.max(24, track * VISIBLE_ROWS / options.size());
                float thumbY = y + INSET * 2 + (track - thumb) * first / (options.size() - VISIBLE_ROWS);
                canvas.fillRect(x + w - 6, thumbY, 3, thumb, 1.5f, theme.getMutedText().withAlpha(0.35f));
            }
        }
    }
}
