package com.dervarex.unweird;

import com.dervarex.unweird.style.Theme;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.lwjgl.glfw.GLFW.*;

/**
 * A single-line text input. {@code new Field("Username")}; read it with {@link #value()}.
 *
 * <p>Supports cursor movement (with Ctrl/Alt for whole words), selection with Shift or the mouse,
 * double click to select a word, triple click for everything, and copy/cut/paste.
 * A {@link #masked()} field shows dots and never copies its content to the clipboard.
 */
public class Field extends Widget {

    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");
    /** Ctrl on Linux/Windows, Cmd on macOS. */
    private static final int SHORTCUT = MAC ? GLFW_MOD_SUPER : GLFW_MOD_CONTROL;
    /** Ctrl on Linux/Windows, Alt (Option) on macOS. */
    private static final int WORD = MAC ? GLFW_MOD_ALT : GLFW_MOD_CONTROL;

    private static final float HEIGHT = 40;
    private static final float PADDING_X = 12;
    private static final float FONT_SIZE = 15;
    private static final float CARET_WIDTH = 1.5f;
    private static final String BULLET = "•";

    private final String placeholder;
    private final List<Consumer<String>> changeHandlers = new CopyOnWriteArrayList<>();
    private final List<Runnable> submitHandlers = new CopyOnWriteArrayList<>();
    private boolean masked;

    private volatile String value = "";
    /** Caret position, a char index into {@link #value}. */
    private int caret;
    /** The other end of the selection; equals {@link #caret} when nothing is selected. */
    private int anchor;

    /** Horizontal scroll of the text inside the field, so the caret stays visible. */
    private float scroll;
    /** Caret positions of the last drawn display text, used to map mouse x to a char index. */
    private float[] positions = {0};
    private String positionsFor = "";

    public Field(String placeholder) {
        this.placeholder = Objects.requireNonNull(placeholder, "placeholder");
    }

    /** What the user typed. */
    public String value() {
        return value;
    }

    /** Replaces the content and puts the caret at the end. */
    public Field value(String value) {
        this.value = Objects.requireNonNull(value, "value");
        caret = anchor = value.length();
        return this;
    }

    public String placeholder() {
        return placeholder;
    }

    /** Shows dots instead of the text, for passwords. Copy and cut are disabled. */
    public Field masked() {
        masked = true;
        return this;
    }

    /** Runs {@code handler} with the new value after every edit. */
    public Field onChange(Consumer<String> handler) {
        changeHandlers.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    /** Runs {@code handler} when Enter is pressed in this field. */
    public Field onSubmit(Runnable handler) {
        submitHandlers.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    @Override public Field below(Widget other) { super.below(other); return this; }
    @Override public Field above(Widget other) { super.above(other); return this; }
    @Override public Field rightOf(Widget other) { super.rightOf(other); return this; }
    @Override public Field leftOf(Widget other) { super.leftOf(other); return this; }
    @Override public Field alignLeft(Widget other) { super.alignLeft(other); return this; }
    @Override public Field alignRight(Widget other) { super.alignRight(other); return this; }
    @Override public Field gap(float pixels) { super.gap(pixels); return this; }
    @Override public Field visibleIf(java.util.function.BooleanSupplier condition) { super.visibleIf(condition); return this; }
    @Override public Field enabledIf(java.util.function.BooleanSupplier condition) { super.enabledIf(condition); return this; }

    // ---- selection helpers (package-private for tests) ----

    int caret() {
        return caret;
    }

    String selectedText() {
        return value.substring(selectionStart(), selectionEnd());
    }

    private int selectionStart() {
        return Math.min(caret, anchor);
    }

    private int selectionEnd() {
        return Math.max(caret, anchor);
    }

    private boolean hasSelection() {
        return caret != anchor;
    }

    // ---- input ----

    @Override
    protected boolean focusable() {
        return true;
    }

    @Override
    boolean blinks() {
        return true;
    }

    @Override
    protected Cursor cursor() {
        return Cursor.TEXT;
    }

    @Override
    void typed(int codePoint, Host host) {
        if (Character.isISOControl(codePoint)) {
            return;
        }
        replaceSelection(Character.toString(codePoint), host);
    }

    @Override
    boolean keyPressed(int key, int mods, Host host) {
        boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
        boolean shortcut = (mods & SHORTCUT) != 0;
        boolean word = (mods & WORD) != 0;

        switch (key) {
            case GLFW_KEY_LEFT -> {
                if (hasSelection() && !shift) {
                    moveTo(selectionStart(), false);
                } else {
                    moveTo(word ? wordLeft(caret) : previous(caret), shift);
                }
            }
            case GLFW_KEY_RIGHT -> {
                if (hasSelection() && !shift) {
                    moveTo(selectionEnd(), false);
                } else {
                    moveTo(word ? wordRight(caret) : next(caret), shift);
                }
            }
            case GLFW_KEY_HOME -> moveTo(0, shift);
            case GLFW_KEY_END -> moveTo(value.length(), shift);
            case GLFW_KEY_BACKSPACE -> {
                if (!hasSelection()) {
                    anchor = word ? wordLeft(caret) : previous(caret);
                }
                replaceSelection("", host);
            }
            case GLFW_KEY_DELETE -> {
                if (!hasSelection()) {
                    anchor = word ? wordRight(caret) : next(caret);
                }
                replaceSelection("", host);
            }
            case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> {
                for (Runnable handler : submitHandlers) {
                    host.safely(this, handler);
                }
            }
            default -> {
                if (!shortcut) {
                    return false;
                }
                switch (key) {
                    case GLFW_KEY_A -> {
                        anchor = 0;
                        caret = value.length();
                    }
                    case GLFW_KEY_C -> {
                        if (!masked && hasSelection()) {
                            host.clipboard(selectedText());
                        }
                    }
                    case GLFW_KEY_X -> {
                        if (!masked && hasSelection()) {
                            host.clipboard(selectedText());
                            replaceSelection("", host);
                        }
                    }
                    case GLFW_KEY_V -> {
                        String pasted = host.clipboard();
                        if (pasted != null && !pasted.isEmpty()) {
                            // single line: line breaks become spaces, other control characters are dropped
                            String clean = pasted.replaceAll("\\R", " ").replaceAll("\\p{Cntrl}", "");
                            replaceSelection(clean, host);
                        }
                    }
                    default -> {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    void pointerDown(Pointer pointer) {
        if (pointer.button() != Pointer.Button.LEFT) {
            return;
        }
        int clicks = pointer.clicks();
        boolean shift = pointer.shift();
        int index = indexAt(pointer.x());
        if (clicks >= 3) {
            anchor = 0;
            caret = value.length();
        } else if (clicks == 2) {
            anchor = masked ? 0 : wordStart(index);
            caret = masked ? value.length() : wordEnd(index);
        } else {
            moveTo(index, shift);
        }
    }

    @Override
    void pointerDrag(Pointer pointer) {
        if (pointer.button() == Pointer.Button.LEFT) {
            moveTo(indexAt(pointer.x()), true);
        }
    }

    /** Tabbing into a field selects its content, so typing replaces it. */
    void selectAllForTab() {
        anchor = 0;
        caret = value.length();
    }

    private void moveTo(int index, boolean extendSelection) {
        caret = index;
        if (!extendSelection) {
            anchor = index;
        }
    }

    private void replaceSelection(String insert, Host host) {
        int start = selectionStart();
        int end = selectionEnd();
        if (start == end && insert.isEmpty()) {
            return;
        }
        value = value.substring(0, start) + insert + value.substring(end);
        caret = anchor = start + insert.length();
        String current = value;
        for (Consumer<String> handler : changeHandlers) {
            host.safely(this, () -> handler.accept(current));
        }
    }

    // ---- code point and word navigation ----

    private int previous(int index) {
        return index <= 0 ? 0 : value.offsetByCodePoints(index, -1);
    }

    private int next(int index) {
        return index >= value.length() ? value.length() : value.offsetByCodePoints(index, 1);
    }

    private int wordLeft(int index) {
        if (masked) {
            return 0;
        }
        int i = index;
        while (i > 0 && !isWordChar(value.codePointBefore(i))) {
            i = previous(i);
        }
        while (i > 0 && isWordChar(value.codePointBefore(i))) {
            i = previous(i);
        }
        return i;
    }

    private int wordRight(int index) {
        if (masked) {
            return value.length();
        }
        int i = index;
        while (i < value.length() && !isWordChar(value.codePointAt(i))) {
            i = next(i);
        }
        while (i < value.length() && isWordChar(value.codePointAt(i))) {
            i = next(i);
        }
        return i;
    }

    private int wordStart(int index) {
        int i = index;
        while (i > 0 && isWordChar(value.codePointBefore(i))) {
            i = previous(i);
        }
        return i;
    }

    private int wordEnd(int index) {
        int i = index;
        while (i < value.length() && isWordChar(value.codePointAt(i))) {
            i = next(i);
        }
        return i;
    }

    private static boolean isWordChar(int codePoint) {
        return Character.isLetterOrDigit(codePoint) || codePoint == '_';
    }

    // ---- display ----

    private String displayText() {
        return masked ? BULLET.repeat(value.codePointCount(0, value.length())) : value;
    }

    /** Value index → index into the display text (they differ when masked: one bullet per code point). */
    private int toDisplay(int index) {
        return masked ? value.codePointCount(0, index) : index;
    }

    private int fromDisplay(int displayIndex) {
        return masked ? value.offsetByCodePoints(0, Math.min(displayIndex, value.codePointCount(0, value.length()))) : displayIndex;
    }

    /** The char index closest to the given x (window logical pixels), snapped to code point boundaries. */
    private int indexAt(float px) {
        String display = displayText();
        if (!display.equals(positionsFor) || display.isEmpty()) {
            return display.isEmpty() ? 0 : caret; // not drawn yet with this text; keep the caret where it is
        }
        float local = px - (getX() + PADDING_X) + scroll;
        int best = 0;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0; i <= display.length(); i = i < display.length() ? display.offsetByCodePoints(i, 1) : i + 1) {
            float distance = Math.abs(positions[i] - local);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return fromDisplay(best);
    }

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        return new Size(Math.min(240, maxWidth), HEIGHT);
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
        if (isFocused()) {
            canvas.strokeRect(x, y, w, h, theme.getRadius(), 2, theme.getAccent());
        } else {
            canvas.strokeRect(x, y, w, h, theme.getRadius(), 1, isHovered() ? theme.getMutedText().withAlpha(0.5f) : theme.getBorder());
        }

        String display = displayText();
        positions = canvas.caretPositions(display, Canvas.Weight.REGULAR, FONT_SIZE);
        positionsFor = display;

        float innerX = x + PADDING_X;
        float innerWidth = Math.max(0, w - PADDING_X * 2);
        float caretX = positions[toDisplay(caret)];
        float textWidth = positions[display.length()];

        // keep the caret inside the visible part, and don't scroll past the end
        if (caretX - scroll > innerWidth - CARET_WIDTH) {
            scroll = caretX - innerWidth + CARET_WIDTH;
        } else if (caretX - scroll < 0) {
            scroll = caretX;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, textWidth - innerWidth + CARET_WIDTH)));

        canvas.clip(innerX - 1, y, innerWidth + 2, h);
        float lineHeight = canvas.lineHeight(FONT_SIZE);
        if (isFocused() && hasSelection()) {
            float from = positions[toDisplay(selectionStart())];
            float to = positions[toDisplay(selectionEnd())];
            canvas.fillRect(innerX - scroll + from, centerY - lineHeight / 2, to - from, lineHeight, 2, theme.getAccent().withAlpha(0.3f));
        }
        if (display.isEmpty()) {
            canvas.textLine(placeholder, innerX, centerY, Canvas.Weight.REGULAR, FONT_SIZE, theme.getMutedText());
        } else {
            canvas.textLine(display, innerX - scroll, centerY, Canvas.Weight.REGULAR, FONT_SIZE, theme.getText());
        }
        if (isFocused() && caretOn && !hasSelection()) {
            float caretHeight = FONT_SIZE * 1.25f;
            canvas.fillRect(innerX - scroll + caretX - CARET_WIDTH / 2, centerY - caretHeight / 2, CARET_WIDTH, caretHeight, 0, theme.getText());
        }
        canvas.unclip();
    }
}
