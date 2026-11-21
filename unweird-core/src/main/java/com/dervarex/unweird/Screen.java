package com.dervarex.unweird;

import java.util.ArrayList;
import java.util.List;

/**
 * One full window of content. Declare the widgets as fields; they are added automatically
 * in declaration order and laid out for you.
 *
 * <p>Subclasses need a constructor without parameters. Annotate with
 * {@link com.dervarex.unweird.annotation.Title @Title} to set the window title,
 * {@link com.dervarex.unweird.annotation.Without @Without} to hide default components,
 * and {@link com.dervarex.unweird.annotation.Fresh @Fresh} to get a new instance on every visit.
 */
public abstract class Screen extends Container {

    /** The default components shown around this screen's content. Set on every visit. */
    List<DockSlot> docks = List.of();

    /** The card the content sits on, if the layout decided to use one. Set by {@link Flow}. */
    boolean card;
    float cardX;
    float cardY;
    float cardWidth;
    float cardHeight;

    /** Called every time this screen becomes visible, including the first time. */
    protected void onShow() {
    }

    /** Called when another screen replaces this one. */
    protected void onHide() {
    }

    /** Re-evaluates, lays out and draws the whole screen. */
    final void render(Canvas canvas, float width, float height) {
        update();
        place(canvas, 0, 0, width, height);
        draw(canvas);
    }

    @Override
    protected void update() {
        super.update();
        for (DockSlot slot : docks) {
            slot.component.update();
        }
    }

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        return new Size(maxWidth, getHeight()); // a screen always fills the window
    }

    @Override
    protected void arrange(Canvas canvas) {
        float[] content = Docking.arrange(this, canvas);
        Flow.arrange(this, canvas, content);
    }

    @Override
    protected void draw(Canvas canvas) {
        Docking.drawPanels(this, canvas);
        for (DockSlot slot : docks) {
            slot.component.draw(canvas);
        }
        if (card) {
            float radius = canvas.getTheme().getRadius() * 2;
            canvas.shadow(cardX, cardY, cardWidth, cardHeight, radius, 24, canvas.getTheme().getShadow());
            canvas.fillRect(cardX, cardY, cardWidth, cardHeight, radius, canvas.getTheme().getSurface());
            canvas.strokeRect(cardX, cardY, cardWidth, cardHeight, radius, 1, canvas.getTheme().getBorder());
        }
        super.draw(canvas);
    }

    @Override
    Widget hitTest(float px, float py) {
        for (DockSlot slot : docks) {
            Widget hit = slot.component.hitTest(px, py);
            if (hit != null) {
                return hit;
            }
        }
        return super.hitTest(px, py);
    }

    /** Tab order for the whole window: top and left docks, the content, then right and bottom docks. */
    final List<Widget> tabOrder() {
        List<Widget> order = new ArrayList<>();
        addDocks(order, Side.TOP);
        addDocks(order, Side.LEFT);
        order.addAll(focusables());
        addDocks(order, Side.RIGHT);
        addDocks(order, Side.BOTTOM);
        return order;
    }

    private void addDocks(List<Widget> order, Side side) {
        for (DockSlot slot : docks) {
            if (slot.side == side) {
                order.addAll(slot.component.focusables());
            }
        }
    }
}
