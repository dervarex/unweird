package com.dervarex.unweird;

import java.util.ArrayList;
import java.util.List;

/**
 * The M1 screen layout: everything flows top to bottom on a centered card.
 *
 * <ul>
 *   <li>The first {@link Text} becomes a heading, a text right after it a subtitle.</li>
 *   <li>Consecutive {@link Button}s share one row; consecutive {@link Field}s form a tight block.</li>
 *   <li>The card is vertically centered when it fits, otherwise it starts at the top.</li>
 *   <li>Empty texts take no space, so a status line appears only once it says something.</li>
 * </ul>
 *
 * The full inference rules and relational overrides replace this in M4 (see DESIGN.md).
 */
final class Flow {

    private static final float CARD_CONTENT_WIDTH = 360;
    private static final float CARD_PADDING = 32;
    private static final float OUTER_MARGIN = 24;
    private static final float BUTTON_GAP = 8;

    private Flow() {
    }

    /** Lays out the screen's content inside {@code area} = {x, y, width, height}. */
    static void arrange(Screen screen, Canvas canvas, float[] area) {
        float areaX = area[0];
        float areaY = area[1];
        float areaWidth = area[2];
        float areaHeight = area[3];
        // An empty text (e.g. a status message nobody has set yet) takes no space.
        List<Widget> children = new ArrayList<>();
        for (Widget child : screen.children()) {
            if (child instanceof Text text && text.text().isEmpty()) {
                child.place(canvas, areaX, areaY, 0, 0);
            } else {
                children.add(child);
            }
        }
        screen.card = !children.isEmpty();
        if (!screen.card) {
            return;
        }

        inferTextRoles(children);
        List<List<Widget>> rows = rows(children);

        float cardWidth = Math.min(CARD_CONTENT_WIDTH + CARD_PADDING * 2, areaWidth - OUTER_MARGIN * 2);
        float contentWidth = Math.max(0, cardWidth - CARD_PADDING * 2);

        float[] heights = new float[rows.size()];
        float total = 0;
        for (int i = 0; i < rows.size(); i++) {
            heights[i] = rowHeight(rows.get(i), canvas, contentWidth);
            total += heights[i];
            if (i > 0) {
                total += gap(rows.get(i - 1), rows.get(i));
            }
        }

        float cardHeight = total + CARD_PADDING * 2;
        float freeHeight = areaHeight - OUTER_MARGIN * 2;
        screen.cardWidth = cardWidth;
        screen.cardHeight = cardHeight;
        screen.cardX = areaX + (areaWidth - cardWidth) / 2;
        screen.cardY = areaY + (cardHeight <= freeHeight ? (areaHeight - cardHeight) / 2 : OUTER_MARGIN);

        float x = screen.cardX + CARD_PADDING;
        float y = screen.cardY + CARD_PADDING;
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                y += gap(rows.get(i - 1), rows.get(i));
            }
            placeRow(rows.get(i), canvas, x, y, contentWidth, heights[i]);
            y += heights[i];
        }
    }

    private static void inferTextRoles(List<Widget> children) {
        Widget previous = null;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i) instanceof Text text) {
                if (text.role != Text.Role.AUTO) {
                    text.resolved = text.role;
                } else if (i == 0) {
                    text.resolved = Text.Role.HEADING;
                } else if (previous instanceof Text above && above.resolved == Text.Role.HEADING) {
                    text.resolved = Text.Role.SUBTITLE;
                } else {
                    text.resolved = Text.Role.BODY;
                }
                text.align = Canvas.Align.CENTER;
            }
            previous = children.get(i);
        }
    }

    /** Groups consecutive buttons into one row; everything else gets its own row. */
    private static List<List<Widget>> rows(List<Widget> children) {
        List<List<Widget>> rows = new ArrayList<>();
        for (Widget child : children) {
            List<Widget> last = rows.isEmpty() ? null : rows.getLast();
            if (child instanceof Button && last != null && last.getFirst() instanceof Button) {
                last.add(child);
            } else {
                rows.add(new ArrayList<>(List.of(child)));
            }
        }
        return rows;
    }

    private static float rowHeight(List<Widget> row, Canvas canvas, float width) {
        float cellWidth = cellWidth(row, width);
        float height = 0;
        for (Widget widget : row) {
            height = Math.max(height, widget.measure(canvas, cellWidth).height());
        }
        return height;
    }

    private static void placeRow(List<Widget> row, Canvas canvas, float x, float y, float width, float height) {
        float cellWidth = cellWidth(row, width);
        for (Widget widget : row) {
            widget.place(canvas, x, y, cellWidth, height);
            x += cellWidth + BUTTON_GAP;
        }
    }

    private static float cellWidth(List<Widget> row, float width) {
        return (width - BUTTON_GAP * (row.size() - 1)) / row.size();
    }

    /** Space between two rows: tight within a heading block, wider between different kinds of content. */
    private static float gap(List<Widget> above, List<Widget> below) {
        Widget a = above.getLast();
        Widget b = below.getFirst();
        if (a instanceof Text ta && b instanceof Text tb) {
            return ta.resolved == Text.Role.HEADING && tb.resolved == Text.Role.SUBTITLE ? 6 : 12;
        }
        if (a instanceof Field && b instanceof Field) {
            return 10; // a block of inputs
        }
        if (a instanceof Text && (b instanceof Field || b instanceof Button)) {
            return 28; // from the heading block to the form
        }
        if (a instanceof Field && b instanceof Button) {
            return 20;
        }
        return 16;
    }
}
