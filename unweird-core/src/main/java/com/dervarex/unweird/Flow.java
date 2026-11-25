package com.dervarex.unweird;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The inferred content layout (see DESIGN.md, "Layout").
 *
 * <p>A screen's content becomes either
 * <ul>
 *   <li>a <b>card</b>: centered, ~360px wide, texts centered, buttons full width. Used when the content is
 *       form-like (only texts, fields and buttons, also inside components) and fits the window; or</li>
 *   <li>a <b>page</b>: a top-aligned column up to 640px wide, texts left-aligned, buttons at their natural width.</li>
 * </ul>
 *
 * Inside both, content is a column of rows:
 * <ul>
 *   <li>The first text (after any leading images) is a heading, a title inside components; a text right after it is a muted subtitle.</li>
 *   <li>Consecutive buttons share a row; the first is primary, the others secondary.</li>
 *   <li>Empty texts take no space.</li>
 *   <li>Overrides: {@code below/above} move a widget, {@code rightOf/leftOf} join another widget's row,
 *       {@code alignLeft/alignRight} line up edges, {@code gap} replaces the inferred spacing.</li>
 * </ul>
 * Components inside the content are laid out with the same rules, so a login form looks the same whether
 * its widgets sit directly on the screen or in a {@link Component}.
 */
final class Flow {

    enum Mode { CARD, PAGE }

    private static final float CARD_CONTENT_WIDTH = 360;
    private static final float CARD_PADDING = 32;
    private static final float OUTER_MARGIN = 24;
    private static final float PAGE_WIDTH = 640;
    private static final float PAGE_TOP = 40;
    private static final float PAGE_BOTTOM = 40;
    private static final float ROW_ITEM_GAP = 8;
    /** Space between the lines of a row that had to wrap. */
    private static final float WRAP_GAP = 10;
    /** The least a field (or other stretching widget) gets in a row before the row wraps. */
    private static final float MIN_FLEXIBLE = 160;

    private Flow() {
    }

    /** Lays out the screen's content inside {@code area} = {x, y, width, height}. */
    static void arrange(Screen screen, Canvas canvas, float[] area) {
        float areaX = area[0];
        float areaY = area[1];
        float areaWidth = area[2];
        float areaHeight = area[3];

        screen.card = false;
        screen.headingRole = Text.Role.HEADING;
        screen.contentX = areaX;
        screen.contentY = areaY;
        screen.contentWidth = areaWidth;
        screen.contentHeight = areaHeight;

        if (formLike(screen)) {
            float cardWidth = Math.min(CARD_CONTENT_WIDTH + CARD_PADDING * 2, areaWidth - OUTER_MARGIN * 2);
            float contentWidth = Math.max(0, cardWidth - CARD_PADDING * 2);
            screen.flowMode = Mode.CARD;
            float contentHeight = column(screen, canvas, contentWidth, false, 0, 0);
            float cardHeight = contentHeight + CARD_PADDING * 2;
            if (contentHeight > 0 && cardHeight <= areaHeight - OUTER_MARGIN * 2) {
                screen.card = true;
                screen.cardWidth = cardWidth;
                screen.cardHeight = cardHeight;
                screen.cardX = areaX + (areaWidth - cardWidth) / 2;
                screen.cardY = areaY + (areaHeight - cardHeight) / 2;
                column(screen, canvas, contentWidth, true, screen.cardX + CARD_PADDING, screen.cardY + CARD_PADDING);
                screen.scrollMax = 0;
                screen.scrollY = 0;
                return;
            }
        }

        screen.flowMode = Mode.PAGE;
        float width = Math.max(0, Math.min(PAGE_WIDTH, areaWidth - OUTER_MARGIN * 2));
        float top = Math.min(PAGE_TOP, Math.max(OUTER_MARGIN, areaHeight * 0.06f));
        float height = column(screen, canvas, width, false, 0, 0);
        screen.scrollMax = Math.max(0, top + height + PAGE_BOTTOM - areaHeight);
        screen.scrollY = Math.max(0, Math.min(screen.scrollY, screen.scrollMax));
        column(screen, canvas, width, true, areaX + (areaWidth - width) / 2, areaY + top - screen.scrollY);
    }

    /** Only texts, fields and buttons, possibly grouped in components: the kind of content that sits on a card. */
    static boolean formLike(Container container) {
        for (Widget child : container.children()) {
            if (child instanceof Container nested) {
                if (nested instanceof Each<?> || nested.card || !formLike(nested)) { // lists and cards make a page
                    return false;
                }
            } else if (!(child instanceof Text || child instanceof Field || child instanceof Dropdown<?>
                    || child instanceof Button || child instanceof Choice<?> || child instanceof Image || child instanceof Slider)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Measures ({@code place = false}) or places the children of {@code container} as a column of rows,
     * {@code width} wide, starting at (x, y). Returns the column's height.
     */
    static float column(Container container, Canvas canvas, float width, boolean place, float x, float y) {
        Mode mode = container.flowMode;
        List<Widget> visible = new ArrayList<>();
        for (Widget child : container.children()) {
            if (!child.isShown()) {
                if (place) {
                    child.place(canvas, x, y, 0, 0);
                }
            } else {
                visible.add(child);
            }
        }
        if (visible.isEmpty()) {
            return 0;
        }

        List<Widget> order = reorder(container, visible);
        inferRoles(container, order);
        List<List<Widget>> rows = rows(container, order);
        Set<List<Widget>> continued = Collections.newSetFromMap(new IdentityHashMap<>());
        rows = wrap(rows, canvas, width, mode, continued);

        float height = 0;
        for (int i = 0; i < rows.size(); i++) {
            List<Widget> row = rows.get(i);
            if (i > 0) {
                height += continued.contains(row) ? WRAP_GAP : gap(rows.get(i - 1), row, mode);
            }
            float[] widths = widths(row, canvas, width, mode);
            float rowHeight = 0;
            for (int j = 0; j < row.size(); j++) {
                rowHeight = Math.max(rowHeight, row.get(j).measure(canvas, widths[j]).height());
            }
            if (place) {
                placeRow(row, widths, canvas, x, y + height, width, rowHeight, mode);
            }
            height += rowHeight;
        }

        if (place) {
            for (Widget widget : order) {
                applyAlignment(widget, canvas);
            }
        }
        return height;
    }

    // ---- order and rows ----

    /** Applies {@code below/above}: moves widgets next to their target, keeping several moves to one target in declaration order. */
    static List<Widget> reorder(Container container, List<Widget> visible) {
        List<Widget> order = new ArrayList<>(visible);
        Map<Widget, Widget> lastBelow = new HashMap<>();
        for (Widget widget : visible) {
            if (widget.relation != Widget.Relation.BELOW && widget.relation != Widget.Relation.ABOVE) {
                continue;
            }
            Widget target = target(container, widget, widget.relationTarget, visible);
            if (target == null) {
                continue; // target is hidden right now (an empty text): stay where declared
            }
            order.remove(widget);
            if (widget.relation == Widget.Relation.BELOW) {
                Widget after = lastBelow.getOrDefault(target, target);
                order.add(order.indexOf(after) + 1, widget);
                lastBelow.put(target, widget);
            } else {
                order.add(order.indexOf(target), widget);
            }
        }
        return order;
    }

    static List<List<Widget>> rows(Container container, List<Widget> order) {
        List<List<Widget>> rows = new ArrayList<>();
        List<Widget> joining = new ArrayList<>();
        Set<Widget> joined = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Widget widget : order) {
            if (widget.relation == Widget.Relation.RIGHT_OF || widget.relation == Widget.Relation.LEFT_OF) {
                joined.add(widget.relationTarget);
            }
        }
        Predicate<Widget> grouped = w -> autoGroupedButton(w) && !joined.contains(w);
        for (Widget widget : order) {
            if (widget.relation == Widget.Relation.RIGHT_OF || widget.relation == Widget.Relation.LEFT_OF) {
                joining.add(widget);
                continue;
            }
            List<Widget> last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (grouped.test(widget) && last != null && last.stream().allMatch(grouped)) {
                last.add(widget);
            } else {
                rows.add(new ArrayList<>(List.of(widget)));
            }
        }
        for (Widget widget : joining) {
            Widget target = target(container, widget, widget.relationTarget, order);
            List<Widget> row = target == null ? null : rows.stream().filter(r -> r.contains(target)).findFirst().orElse(null);
            if (row == null) {
                rows.add(new ArrayList<>(List.of(widget))); // target hidden or itself joining: own row
            } else {
                int index = row.indexOf(target);
                row.add(widget.relation == Widget.Relation.RIGHT_OF ? index + 1 : index, widget);
            }
        }
        return rows;
    }

    /** Buttons group into rows automatically, unless the developer positioned them explicitly. */
    private static boolean autoGroupedButton(Widget widget) {
        return widget instanceof Button && widget.relation == null && widget.alignment == null;
    }

    /** Resolves a relation target among the visible children, or explains why it can't be. */
    private static Widget target(Container container, Widget widget, Widget target, List<Widget> visible) {
        if (visible.contains(target)) {
            return target;
        }
        if (container.children().contains(target)) {
            return null; // hidden
        }
        throw new IllegalStateException("A " + widget.getClass().getSimpleName() + " in " + container.getClass().getName()
                + " is positioned relative to a " + target.getClass().getSimpleName()
                + " that isn't in the same screen or component");
    }

    // ---- inference ----

    static void inferRoles(Container container, List<Widget> order) {
        boolean card = container.flowMode == Mode.CARD;
        Widget previous = null;
        boolean onlyImagesSoFar = true; // a logo above the first text doesn't stop it from being the heading
        for (int i = 0; i < order.size(); i++) {
            Widget widget = order.get(i);
            boolean leading = onlyImagesSoFar;
            onlyImagesSoFar &= widget instanceof Image;
            if (widget instanceof Text text) {
                if (text.role != Text.Role.AUTO) {
                    text.resolved = text.role;
                } else if (leading) {
                    text.resolved = container.headingRole;
                } else if (previous instanceof Text above && (above.resolved == Text.Role.HEADING || above.resolved == Text.Role.TITLE)) {
                    text.resolved = Text.Role.SUBTITLE;
                } else {
                    text.resolved = Text.Role.BODY;
                }
                text.align = card ? Canvas.Align.CENTER : Canvas.Align.LEFT;
            } else if (widget instanceof Container nested) {
                nested.flowMode = container.flowMode;
                // a component that opens a screen without its own heading carries the heading
                nested.headingRole = leading && container.headingRole == Text.Role.HEADING ? Text.Role.HEADING : Text.Role.TITLE;
            }
            previous = widget;
        }
    }

    private static float gap(List<Widget> above, List<Widget> below, Mode mode) {
        Widget a = above.get(above.size() - 1);
        Widget b = below.get(0);
        if (!Float.isNaN(b.gapBefore)) {
            return b.gapBefore;
        }
        if (a instanceof Container ca && b instanceof Container cb && ca.card && cb.card) {
            return 12; // a list of cards
        }
        if (a instanceof Container || b instanceof Container) {
            return 24;
        }
        if (a instanceof Text ta && b instanceof Text tb) {
            boolean headingBlock = (ta.resolved == Text.Role.HEADING || ta.resolved == Text.Role.TITLE) && tb.resolved == Text.Role.SUBTITLE;
            return headingBlock ? 6 : 12;
        }
        if (isInput(a) && isInput(b)) {
            return 10;
        }
        if (a instanceof Choice<?> && b instanceof Choice<?>) {
            return 8;
        }
        if (isInput(a) && b instanceof Choice<?>) {
            return 12;
        }
        if (a instanceof Text && (isInput(b) || b instanceof Button)) {
            return mode == Mode.CARD ? 28 : 20;
        }
        if ((isInput(a) || a instanceof Choice<?>) && b instanceof Button) {
            return 20;
        }
        return 16;
    }

    // ---- sizing and placing rows ----

    private static float[] widths(List<Widget> row, Canvas canvas, float width, Mode mode) {
        resolveButtonVariants(row);
        float[] widths = new float[row.size()];
        if (row.size() == 1) {
            Widget only = row.get(0);
            boolean natural = only.alignment != null || only instanceof Image || (only instanceof Button && mode == Mode.PAGE);
            widths[0] = natural ? only.measure(canvas, width).width() : width;
            return widths;
        }
        float gaps = 0;
        for (int i = 1; i < row.size(); i++) {
            gaps += itemGap(row.get(i));
        }
        if (mode == Mode.CARD && row.stream().allMatch(w -> w instanceof Button)) {
            Arrays.fill(widths, Math.max(0, (width - gaps) / row.size()));
            return widths;
        }
        // natural widths; fields (and components) share whatever is left
        float used = gaps;
        int flexible = 0;
        for (int i = 0; i < row.size(); i++) {
            Widget widget = row.get(i);
            if (isFlexible(widget)) {
                flexible++;
            } else {
                widths[i] = widget.measure(canvas, Math.max(0, width - used)).width();
                used += widths[i];
            }
        }
        if (flexible > 0) {
            float share = Math.max(0, (width - used) / flexible);
            for (int i = 0; i < row.size(); i++) {
                if (isFlexible(row.get(i))) {
                    widths[i] = share;
                }
            }
        }
        return widths;
    }

    /**
     * Rows whose widgets don't fit side by side continue on the next line instead of squeezing the last ones
     * (which would leave them drawn but too narrow to click). Card button rows split the width instead, so they never wrap.
     * Lines after the first are added to {@code continued}.
     */
    static List<List<Widget>> wrap(List<List<Widget>> rows, Canvas canvas, float width, Mode mode, Set<List<Widget>> continued) {
        List<List<Widget>> out = new ArrayList<>();
        for (List<Widget> row : rows) {
            boolean splitsEvenly = mode == Mode.CARD && row.stream().allMatch(w -> w instanceof Button);
            if (row.size() < 2 || splitsEvenly) {
                out.add(row);
                continue;
            }
            List<Widget> line = new ArrayList<>();
            float used = 0;
            for (Widget widget : row) {
                float need = isFlexible(widget) ? MIN_FLEXIBLE : widget.measure(canvas, width).width();
                float gap = line.isEmpty() ? 0 : itemGap(widget);
                if (!line.isEmpty() && used + gap + need > width) {
                    out.add(line);
                    line = new ArrayList<>();
                    used = 0;
                    gap = 0;
                    continued.add(line);
                }
                line.add(widget);
                used += gap + need;
            }
            out.add(line);
        }
        return out;
    }

    /** First automatic button in a row is primary, the rest secondary. Explicit variants always win. */
    static void resolveButtonVariants(List<Widget> row) {
        boolean primaryTaken = false;
        for (Widget widget : row) {
            if (widget instanceof Button button) {
                if (button.variant != null) {
                    button.resolved = button.variant;
                } else {
                    button.resolved = primaryTaken ? Button.Variant.SECONDARY : Button.Variant.PRIMARY;
                }
                button.alignStart = false;
                primaryTaken |= button.resolved == Button.Variant.PRIMARY;
            }
        }
    }

    private static void placeRow(List<Widget> row, float[] widths, Canvas canvas, float x, float y, float width, float rowHeight, Mode mode) {
        float total = 0;
        for (int i = 0; i < row.size(); i++) {
            total += widths[i] + (i > 0 ? itemGap(row.get(i)) : 0);
        }
        float cx = mode == Mode.CARD ? x + Math.max(0, (width - total) / 2) : x;
        for (int i = 0; i < row.size(); i++) {
            Widget widget = row.get(i);
            if (i > 0) {
                cx += itemGap(widget);
            }
            float h = row.size() == 1 ? rowHeight : widget.measure(canvas, widths[i]).height();
            widget.place(canvas, cx, y + (rowHeight - h) / 2, widths[i], h);
            cx += widths[i];
        }
    }

    /** Fields, dropdowns and lists: boxes you fill in or pick from, which stretch to the available width. */
    private static boolean isInput(Widget widget) {
        return widget instanceof Field || widget instanceof Dropdown<?> || widget instanceof ListView<?>;
    }

    /** Takes the space left in a mixed row: fields, lists and components. Dropdowns keep their natural width. */
    private static boolean isFlexible(Widget widget) {
        return widget instanceof Field || widget instanceof ListView<?> || widget instanceof Container;
    }

    private static float itemGap(Widget widget) {
        return Float.isNaN(widget.gapBefore) ? ROW_ITEM_GAP : widget.gapBefore;
    }

    private static void applyAlignment(Widget widget, Canvas canvas) {
        if (widget.alignment == null) {
            return;
        }
        Widget target = widget.alignmentTarget;
        float x = widget.alignment == Widget.Alignment.LEFT
                ? target.getX()
                : target.getX() + target.getWidth() - widget.getWidth();
        widget.place(canvas, x, widget.getY(), widget.getWidth(), widget.getHeight());
    }
}
