package com.dervarex.unweird;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A piece of text. Either fixed, {@code new Text("Welcome")}, or live,
 * {@code new Text(() -> "Hi " + user.value())}, which is re-evaluated after every event.
 *
 * <p>Its look is inferred from its position: the first text on a screen becomes a heading,
 * a text right below a heading becomes a subtitle. {@link #heading()} and {@link #body()} override that.
 */
public class Text extends Widget {

    enum Role { AUTO, HEADING, SUBTITLE, BODY }

    private volatile Supplier<String> source;
    private volatile String text;

    /** What the developer asked for. */
    Role role = Role.AUTO;
    /** What the layout decided. */
    Role resolved = Role.BODY;
    Canvas.Align align = Canvas.Align.LEFT;

    public Text(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    public Text(Supplier<String> source) {
        this.source = Objects.requireNonNull(source, "source");
        this.text = "";
    }

    /** The text currently shown. */
    public String text() {
        return text;
    }

    /** Replaces the text (and any lambda it was created with). */
    public Text text(String text) {
        this.source = null;
        this.text = Objects.requireNonNull(text, "text");
        return this;
    }

    /** Always style this as a heading. */
    public Text heading() {
        role = Role.HEADING;
        return this;
    }

    /** Always style this as normal body text. */
    public Text body() {
        role = Role.BODY;
        return this;
    }

    @Override
    protected void update() {
        Supplier<String> current = source;
        if (current != null) {
            text = String.valueOf(current.get());
        }
    }

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        return canvas.measureText(text, weight(), fontSize(), maxWidth);
    }

    @Override
    protected void draw(Canvas canvas) {
        var color = resolved == Role.SUBTITLE ? canvas.getTheme().getMutedText() : canvas.getTheme().getText();
        canvas.text(text, getX(), getY(), getWidth(), weight(), fontSize(), color, align);
    }

    private Canvas.Weight weight() {
        return resolved == Role.HEADING ? Canvas.Weight.SEMIBOLD : Canvas.Weight.REGULAR;
    }

    private float fontSize() {
        return resolved == Role.HEADING ? 26 : 15;
    }
}
