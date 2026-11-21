package com.dervarex.unweird;

import lombok.AccessLevel;
import lombok.Getter;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Anything that can be shown: {@link Text}, {@link Button}, {@link Field}, and containers such as
 * {@link Screen} and {@link Component}.
 *
 * <p>All coordinates are logical pixels; the window maps them to physical pixels for HiDPI displays.
 */
public abstract class Widget {

    @Getter(AccessLevel.PROTECTED) private float x;
    @Getter(AccessLevel.PROTECTED) private float y;
    @Getter(AccessLevel.PROTECTED) private float width;
    @Getter(AccessLevel.PROTECTED) private float height;

    /** The mouse is over this widget (only tracked for interactive widgets). */
    @Getter(AccessLevel.PROTECTED) boolean hovered;
    /** The mouse button went down on this widget and hasn't been released yet. */
    @Getter(AccessLevel.PROTECTED) boolean pressed;
    /** This widget receives keyboard input. */
    @Getter(AccessLevel.PROTECTED) boolean focused;
    /** Focus arrived via the keyboard (Tab), so a focus ring should be visible. */
    @Getter(AccessLevel.PROTECTED) boolean focusVisible;
    /** Blink phase of the text caret, driven by the window. */
    boolean caretOn = true;

    Container parent;

    // ---- visibility and enabled state, re-evaluated after every event ----

    private BooleanSupplier visibleIf;
    private BooleanSupplier enabledIf;
    private boolean visible = true;
    private boolean enabled = true;

    private volatile String tooltip;

    /** A hint shown when the mouse rests on this widget for a moment. {@code null} removes it. */
    public Widget tooltip(String text) {
        tooltip = text == null || text.isBlank() ? null : text;
        return this;
    }

    /** The tooltip text, or null. */
    public String tooltipText() {
        return tooltip;
    }

    /** Hovered for its tooltip only (it doesn't take clicks or focus). */
    final boolean hoverOnly() {
        return visible && tooltip != null && !interactive();
    }

    /** Shows this widget only while {@code condition} is true; hidden widgets take no space. */
    public Widget visibleIf(BooleanSupplier condition) {
        visibleIf = Objects.requireNonNull(condition, "condition");
        return this;
    }

    /** Lets the user interact with this widget only while {@code condition} is true; otherwise it's dimmed. */
    public Widget enabledIf(BooleanSupplier condition) {
        enabledIf = Objects.requireNonNull(condition, "condition");
        return this;
    }

    /** Re-evaluates {@link #visibleIf} and {@link #enabledIf}. Called by the parent before {@link #update()}. */
    final void evaluateConditions() {
        if (visibleIf != null) {
            visible = visibleIf.getAsBoolean();
        }
        if (enabledIf != null) {
            enabled = enabledIf.getAsBoolean();
        }
    }

    /** Whether the layout should give this widget space. */
    boolean isShown() {
        return visible;
    }

    /** Whether this widget currently reacts to input. */
    final boolean isEnabled() {
        return enabled;
    }

    // ---- relational overrides (see DESIGN.md, "Layout") ----

    enum Relation { BELOW, ABOVE, RIGHT_OF, LEFT_OF }

    enum Alignment { LEFT, RIGHT }

    Relation relation;
    Widget relationTarget;
    Alignment alignment;
    Widget alignmentTarget;
    /** Space before this widget, overriding the inferred gap. NaN = inferred. */
    float gapBefore = Float.NaN;

    /** Places this widget directly below {@code other}, wherever it was declared. */
    public Widget below(Widget other) {
        return relate(Relation.BELOW, other);
    }

    /** Places this widget directly above {@code other}, wherever it was declared. */
    public Widget above(Widget other) {
        return relate(Relation.ABOVE, other);
    }

    /** Puts this widget in the same row as {@code other}, to its right. */
    public Widget rightOf(Widget other) {
        return relate(Relation.RIGHT_OF, other);
    }

    /** Puts this widget in the same row as {@code other}, to its left. */
    public Widget leftOf(Widget other) {
        return relate(Relation.LEFT_OF, other);
    }

    /** Uses its natural width and lines up its left edge with {@code other}'s. */
    public Widget alignLeft(Widget other) {
        return align(Alignment.LEFT, other);
    }

    /** Uses its natural width and lines up its right edge with {@code other}'s. */
    public Widget alignRight(Widget other) {
        return align(Alignment.RIGHT, other);
    }

    /** Space before this widget in logical pixels, instead of the inferred spacing. */
    public Widget gap(float pixels) {
        if (pixels < 0 || Float.isNaN(pixels)) {
            throw new IllegalArgumentException("gap must be zero or positive, got " + pixels);
        }
        gapBefore = pixels;
        return this;
    }

    private Widget relate(Relation kind, Widget other) {
        relation = kind;
        relationTarget = checkTarget(other);
        return this;
    }

    private Widget align(Alignment kind, Widget other) {
        alignment = kind;
        alignmentTarget = checkTarget(other);
        return this;
    }

    private Widget checkTarget(Widget other) {
        if (other == null) {
            throw new IllegalArgumentException("Can't position a " + getClass().getSimpleName() + " relative to null "
                    + "(is the other widget declared further down? Declare it first)");
        }
        if (other == this) {
            throw new IllegalArgumentException("A widget can't be positioned relative to itself");
        }
        return other;
    }

    /** Re-evaluates lambdas such as {@code new Text(() -> ...)}. Runs before every layout. */
    protected void update() {
    }

    /** The size this widget would like, given at most {@code maxWidth} horizontally. */
    protected abstract Size measure(Canvas canvas, float maxWidth);

    /** Positions children after this widget's own bounds were set. Only containers need this. */
    protected void arrange(Canvas canvas) {
    }

    protected abstract void draw(Canvas canvas);

    /** Whether a mouse click (press + release on the widget) calls {@link #click()}. */
    protected boolean clickable() {
        return false;
    }

    /** Called when the widget was clicked, or activated with the keyboard. */
    protected void click() {
    }

    /** A mouse click as the window delivers it. Widgets that need the window (e.g. to open a popup) override this. */
    void activate(Host host) {
        click();
    }

    // ---- input for custom widgets ----
    //
    // A widget gets pointer events when it's clickable() or focusable(), and key events while it has focus.
    // After every event the UI re-evaluates and redraws, so handlers just change state.

    /** Whether this widget can take keyboard focus (Tab, click). */
    protected boolean focusable() {
        return false;
    }

    /** The mouse cursor over this widget. */
    protected Cursor cursor() {
        return clickable() ? Cursor.HAND : Cursor.ARROW;
    }

    /** A mouse button went down on this widget (any button). It keeps getting {@link #onDrag} until release. */
    protected void onPress(Pointer pointer) {
    }

    /** The mouse moved while a button is held after {@link #onPress}, also outside the widget. */
    protected void onDrag(Pointer pointer) {
    }

    /** The button from {@link #onPress} was released (anywhere). */
    protected void onRelease(Pointer pointer) {
    }

    /** The mouse moved over this widget without a button held. Return true if that changes what's drawn. */
    protected boolean onMove(Pointer pointer) {
        return false;
    }

    /** Mouse wheel / touchpad scroll over this widget. Return true to use it (otherwise the page scrolls). */
    protected boolean onScroll(Pointer at, double dx, double dy) {
        return false;
    }

    /** A key was pressed (or repeats) while this widget has focus. Return true if it was used. */
    protected boolean onKey(KeyPress key) {
        return false;
    }

    /** Text was typed while this widget has focus (already translated by the keyboard layout). */
    protected void onType(String text) {
    }

    /**
     * Asks for another frame right after this one, for animations: call it from {@link #draw} while something moves
     * and read {@link Canvas#time()} for the clock. When nobody asks, the window goes back to sleeping.
     */
    protected final void requestFrame() {
        Unweird.requestFrame();
    }

    /**
     * Moves keyboard focus here (if this widget can take it and is on screen). Safe from any thread;
     * e.g. in {@code onShow()} to put the cursor into a particular field.
     */
    public void focus() {
        Unweird.focus(this);
    }

    // ---- internal input paths (built-in widgets override these; the defaults call the public hooks) ----

    boolean keyPressed(int key, int mods, Host host) {
        return onKey(new KeyPress(key, mods, false));
    }

    void typed(int codePoint, Host host) {
        onType(Character.toString(codePoint));
    }

    void pointerDown(Pointer pointer) {
        onPress(pointer);
    }

    boolean pointerMove(Pointer pointer) {
        return onMove(pointer);
    }

    void pointerDrag(Pointer pointer) {
        onDrag(pointer);
    }

    void pointerUp(Pointer pointer) {
        onRelease(pointer);
    }

    boolean scrolled(Pointer at, double dx, double dy) {
        return onScroll(at, dx, dy);
    }

    /** Whether the window should blink a caret while this widget is focused. */
    boolean blinks() {
        return false;
    }

    final boolean interactive() {
        return enabled && visible && (clickable() || focusable());
    }

    final void place(Canvas canvas, float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        arrange(canvas);
    }

    /** The innermost interactive widget at the given point, or {@code null}. */
    Widget hitTest(float px, float py) {
        return (interactive() || hoverOnly()) && contains(px, py) ? this : null;
    }

    final boolean contains(float px, float py) {
        return px >= x && py >= y && px < x + width && py < y + height;
    }
}
