package com.dervarex.examples.chart;

import com.dervarex.unweird.Canvas;
import com.dervarex.unweird.Size;
import com.dervarex.unweird.Widget;
import com.dervarex.unweird.style.Theme;

import java.util.List;
import java.util.function.Supplier;

/** Draws bars from a live list and animates them whenever the data changes. */
public class BarChart extends Widget {

    public record Bar(String label, double value) {
    }

    private static final float HEIGHT = 260;
    private static final float LABELS = 24;
    private static final double GROW_SECONDS = 0.5;

    private final Supplier<List<Bar>> data;
    private List<Bar> from = List.of();
    private List<Bar> to = List.of();
    private double changedAt;

    public BarChart(Supplier<List<Bar>> data) {
        this.data = data;
    }

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        return new Size(maxWidth, HEIGHT);
    }

    @Override
    protected void draw(Canvas canvas) {
        Theme theme = canvas.getTheme();
        List<Bar> bars = data.get();
        if (!bars.equals(to)) {
            from = to;
            to = List.copyOf(bars);
            changedAt = canvas.time();
        }
        double t = Math.min(1, (canvas.time() - changedAt) / GROW_SECONDS);
        double eased = 1 - Math.pow(1 - t, 3);
        if (t < 1) {
            requestFrame(); // keep drawing until the animation is done, then the window sleeps again
        }

        double max = to.stream().mapToDouble(Bar::value).max().orElse(1);
        float plotHeight = getHeight() - LABELS;
        float slot = getWidth() / Math.max(1, to.size());
        float barWidth = slot * 0.6f;

        canvas.fillRect(getX(), getY() + plotHeight, getWidth(), 1, 0, theme.getBorder());
        for (int i = 0; i < to.size(); i++) {
            double start = i < from.size() ? from.get(i).value() : 0;
            double value = start + (to.get(i).value() - start) * eased;
            float h = (float) (value / max * (plotHeight - 24));
            float x = getX() + i * slot + (slot - barWidth) / 2;
            float y = getY() + plotHeight - h;
            canvas.fillRect(x, y, barWidth, h, Math.min(6, barWidth / 4), theme.getAccent());
            canvas.label(String.valueOf(Math.round(value)), x + barWidth / 2, y - 10, Canvas.Weight.SEMIBOLD, 13, theme.getText());
            canvas.label(to.get(i).label(), x + barWidth / 2, getY() + plotHeight + LABELS / 2 + 2, Canvas.Weight.REGULAR, 13, theme.getMutedText());
        }
    }
}
