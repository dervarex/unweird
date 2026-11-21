package com.dervarex.unweird;

import java.util.ArrayList;
import java.util.List;

/**
 * Places {@link com.dervarex.unweird.annotation.Default @Default} components on the edges of a screen
 * and lays out what's inside them.
 *
 * <ul>
 *   <li>Left and right docks take the full window height; top and bottom docks span the width that is left.</li>
 *   <li>Several docks on one side stack outwards-in by class name.</li>
 *   <li>Side docks are vertical: the first text is a title, buttons become full-width navigation items.</li>
 *   <li>Top/bottom docks are horizontal: texts on the left (the first one a title), buttons on the right.</li>
 * </ul>
 */
final class Docking {

    enum Axis { VERTICAL, HORIZONTAL }

    static final float PADDING = 12;
    private static final float SIDE_MIN_WIDTH = 200;
    private static final float SIDE_MAX_WIDTH = 300;
    private static final float ITEM_GAP = 4;
    private static final float TITLE_GAP = 16;
    private static final float ROW_GAP = 8;

    private Docking() {
    }

    /** Places every dock of {@code screen} and returns the rectangle left for the content: {x, y, width, height}. */
    static float[] arrange(Screen screen, Canvas canvas) {
        float left = screen.getX();
        float top = screen.getY();
        float right = left + screen.getWidth();
        float bottom = top + screen.getHeight();

        for (DockSlot slot : screen.docks) {
            if (slot.side == Side.LEFT || slot.side == Side.RIGHT) {
                slot.component.dockAxis = Axis.VERTICAL;
                float preferred = slot.component.measure(canvas, SIDE_MAX_WIDTH - PADDING * 2).width() + PADDING * 2;
                float width = Math.min(clamp(preferred, SIDE_MIN_WIDTH, SIDE_MAX_WIDTH), (right - left) / 2);
                slot.y = top;
                slot.width = width;
                slot.height = bottom - top;
                if (slot.side == Side.LEFT) {
                    slot.x = left;
                    left += width;
                } else {
                    right -= width;
                    slot.x = right;
                }
            }
        }
        for (DockSlot slot : screen.docks) {
            if (slot.side == Side.TOP || slot.side == Side.BOTTOM) {
                slot.component.dockAxis = Axis.HORIZONTAL;
                float height = slot.component.measure(canvas, right - left - PADDING * 2).height() + PADDING * 2;
                height = Math.min(height, (bottom - top) / 2);
                slot.x = left;
                slot.width = right - left;
                slot.height = height;
                if (slot.side == Side.TOP) {
                    slot.y = top;
                    top += height;
                } else {
                    bottom -= height;
                    slot.y = bottom;
                }
            }
        }
        for (DockSlot slot : screen.docks) {
            slot.component.place(canvas, slot.x + PADDING, slot.y + PADDING,
                    Math.max(0, slot.width - PADDING * 2), Math.max(0, slot.height - PADDING * 2));
        }
        return new float[]{left, top, Math.max(0, right - left), Math.max(0, bottom - top)};
    }

    /** Draws the panels behind the docks: surface color with a hairline towards the content. */
    static void drawPanels(Screen screen, Canvas canvas) {
        var theme = canvas.getTheme();
        for (DockSlot slot : screen.docks) {
            canvas.fillRect(slot.x, slot.y, slot.width, slot.height, 0, theme.getSurface());
            switch (slot.side) {
                case LEFT -> canvas.fillRect(slot.x + slot.width - 1, slot.y, 1, slot.height, 0, theme.getBorder());
                case RIGHT -> canvas.fillRect(slot.x, slot.y, 1, slot.height, 0, theme.getBorder());
                case TOP -> canvas.fillRect(slot.x, slot.y + slot.height - 1, slot.width, 1, 0, theme.getBorder());
                case BOTTOM -> canvas.fillRect(slot.x, slot.y, slot.width, 1, 0, theme.getBorder());
            }
        }
    }

    // ---- inside a docked component ----

    static Size measure(Container dock, Canvas canvas, float maxWidth) {
        List<Widget> children = dock.children();
        inferRoles(children, dock.dockAxis);
        if (dock.dockAxis == Axis.VERTICAL) {
            float width = 0;
            float height = 0;
            for (int i = 0; i < children.size(); i++) {
                Size size = children.get(i).measure(canvas, maxWidth);
                width = Math.max(width, size.width());
                height += size.height() + (i < children.size() - 1 ? gapAfter(children.get(i)) : 0);
            }
            return new Size(width, height);
        }
        float width = 0;
        float height = 0;
        for (Widget child : children) {
            Size size = child.measure(canvas, maxWidth);
            width += size.width();
            height = Math.max(height, size.height());
        }
        width += ROW_GAP * Math.max(0, children.size() - 1);
        return new Size(width, height);
    }

    static void arrange(Container dock, Canvas canvas) {
        List<Widget> children = dock.children();
        inferRoles(children, dock.dockAxis);
        if (dock.dockAxis == Axis.VERTICAL) {
            float y = dock.getY();
            for (Widget child : children) {
                float height = child.measure(canvas, dock.getWidth()).height();
                child.place(canvas, dock.getX(), y, dock.getWidth(), height);
                y += height + gapAfter(child);
            }
            return;
        }

        List<Widget> start = new ArrayList<>();
        List<Widget> end = new ArrayList<>();
        for (Widget child : children) {
            (child instanceof Button ? end : start).add(child);
        }
        float centerY = dock.getY() + dock.getHeight() / 2;
        float right = dock.getX() + dock.getWidth();
        for (int i = end.size() - 1; i >= 0; i--) {
            Widget child = end.get(i);
            Size size = child.measure(canvas, Math.max(0, right - dock.getX()));
            right -= size.width();
            child.place(canvas, right, centerY - size.height() / 2, size.width(), size.height());
            right -= ROW_GAP;
        }
        float x = dock.getX();
        for (Widget child : start) {
            Size size = child.measure(canvas, Math.max(0, right - x));
            child.place(canvas, x, centerY - size.height() / 2, size.width(), size.height());
            x += size.width() + TITLE_GAP;
        }
    }

    private static void inferRoles(List<Widget> children, Axis axis) {
        boolean titled = false;
        for (Widget child : children) {
            if (child instanceof Text text) {
                if (text.role != Text.Role.AUTO) {
                    text.resolved = text.role;
                } else {
                    text.resolved = titled ? Text.Role.SUBTITLE : Text.Role.TITLE;
                }
                titled = true;
                text.align = Canvas.Align.LEFT;
            } else if (child instanceof Button button) {
                button.resolved = button.variant != null ? button.variant : Button.Variant.GHOST;
                button.alignStart = axis == Axis.VERTICAL;
            }
        }
    }

    private static float gapAfter(Widget child) {
        return child instanceof Text text && text.resolved == Text.Role.TITLE ? TITLE_GAP : ITEM_GAP;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
