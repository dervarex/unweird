package com.dervarex.unweird;

/**
 * One full window of content. Declare the widgets as fields; they are added automatically
 * in declaration order and laid out for you.
 *
 * <p>Subclasses need a constructor without parameters. Annotate with
 * {@link com.dervarex.unweird.annotation.Title @Title} to set the window title.
 */
public abstract class Screen extends Container {

    /** The card the content sits on, if the layout decided to use one. Set by {@link Flow}. */
    boolean card;
    float cardX;
    float cardY;
    float cardWidth;
    float cardHeight;

    /** Re-evaluates, lays out and draws the whole screen. */
    final void render(Canvas canvas, float width, float height) {
        update();
        place(canvas, 0, 0, width, height);
        draw(canvas);
    }

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        return new Size(maxWidth, getHeight()); // a screen always fills the window
    }

    @Override
    protected void arrange(Canvas canvas) {
        Flow.arrange(this, canvas);
    }

    @Override
    protected void draw(Canvas canvas) {
        if (card) {
            float radius = canvas.getTheme().getRadius() * 2;
            canvas.shadow(cardX, cardY, cardWidth, cardHeight, radius, 24, canvas.getTheme().getShadow());
            canvas.fillRect(cardX, cardY, cardWidth, cardHeight, radius, canvas.getTheme().getSurface());
            canvas.strokeRect(cardX, cardY, cardWidth, cardHeight, radius, 1, canvas.getTheme().getBorder());
        }
        super.draw(canvas);
    }
}
