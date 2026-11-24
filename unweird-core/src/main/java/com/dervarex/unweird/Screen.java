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

    /** The area left for content after docking, and how far it is scrolled. Set by {@link Flow}. */
    float contentX;
    float contentY;
    float contentWidth;
    float contentHeight;
    float scrollY;
    float scrollMax;

    private static final float SCROLL_STEP = 48;

    /** The window title when there's no {@link com.dervarex.unweird.annotation.Title @Title}; null = just the app name. */
    protected String title() {
        return null;
    }

    /** Called every time this screen becomes visible, including the first time. */
    protected void onShow() {
    }

    /** Called when another screen replaces this one, and when the window closes. */
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
            slot.component.evaluateConditions();
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
        canvas.clip(contentX, contentY, contentWidth, contentHeight);
        if (card) {
            float radius = canvas.getTheme().getRadius() * 2;
            canvas.shadow(cardX, cardY, cardWidth, cardHeight, radius, 24, canvas.getTheme().getShadow());
            canvas.fillRect(cardX, cardY, cardWidth, cardHeight, radius, canvas.getTheme().getSurface());
            canvas.strokeRect(cardX, cardY, cardWidth, cardHeight, radius, 1, canvas.getTheme().getBorder());
        }
        super.draw(canvas);
        if (scrollMax > 0) {
            float thumb = Math.max(32, contentHeight * contentHeight / (contentHeight + scrollMax));
            float thumbY = contentY + (contentHeight - thumb) * (scrollY / scrollMax);
            canvas.fillRect(contentX + contentWidth - 7, thumbY + 2, 4, thumb - 4, 2, canvas.getTheme().getMutedText().withAlpha(0.35f));
        }
        canvas.unclip();
    }

    /** Scrolls the content by mouse-wheel steps if the point is over it. Returns whether anything moved. */
    boolean scrollBy(float px, float py, double steps) {
        if (scrollMax <= 0 || !inContent(px, py)) {
            return false;
        }
        float next = (float) Math.max(0, Math.min(scrollMax, scrollY - steps * SCROLL_STEP));
        if (next == scrollY) {
            return false;
        }
        scrollY = next;
        return true;
    }

    /** Scrolls just enough to show {@code widget} (as last laid out), if it is part of the content. */
    void reveal(Widget widget) {
        if (scrollMax <= 0 || !isContent(widget)) {
            return;
        }
        float margin = 16;
        if (widget.getY() < contentY) {
            scrollY -= contentY - widget.getY() + margin;
        } else if (widget.getY() + widget.getHeight() > contentY + contentHeight) {
            scrollY += widget.getY() + widget.getHeight() - (contentY + contentHeight) + margin;
        }
        scrollY = Math.max(0, Math.min(scrollY, scrollMax));
    }

    private boolean inContent(float px, float py) {
        return px >= contentX && py >= contentY && px < contentX + contentWidth && py < contentY + contentHeight;
    }

    private boolean isContent(Widget widget) {
        for (Widget w = widget; w != null; w = w.parent) {
            if (w == this) {
                return true;
            }
        }
        return false;
    }

    @Override
    Widget hitTest(float px, float py) {
        for (DockSlot slot : docks) {
            Widget hit = slot.component.hitTest(px, py);
            if (hit != null) {
                return hit;
            }
        }
        return inContent(px, py) ? super.hitTest(px, py) : null;
    }

    @Override
    Widget widgetAt(float px, float py) {
        for (DockSlot slot : docks) {
            Widget hit = slot.component.widgetAt(px, py);
            if (hit != null) {
                return hit;
            }
        }
        if (!inContent(px, py)) {
            return this;
        }
        Widget hit = super.widgetAt(px, py);
        return hit != null ? hit : this;
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
