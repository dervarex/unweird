package com.dervarex.unweird;

import com.dervarex.unweird.style.Theme;

import java.util.List;

/** Shows a {@link Dialog}: a dimmed backdrop over everything and the dialog centered on a card. */
final class ModalLayer extends Overlay {

    private static final float WIDTH = 440;
    private static final float PADDING = 24;
    private static final float MARGIN = 24;

    final Dialog dialog;
    private float boxX;
    private float boxY;
    private float boxWidth;
    private float boxHeight;

    ModalLayer(Dialog dialog) {
        this.dialog = dialog;
        dialog.flowMode = Flow.Mode.PAGE;
        dialog.headingRole = Text.Role.TITLE;
    }

    @Override
    Widget owner() {
        return null;
    }

    @Override
    boolean modal() {
        return true;
    }

    @Override
    void escape() {
        dialog.cancel();
    }

    @Override
    boolean keyPressed(int key, int mods, Host host) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            boolean[] ran = {false};
            host.safely(dialog, () -> ran[0] = dialog.runDefault());
            return ran[0];
        }
        return false;
    }

    @Override
    List<Widget> focusables() {
        return dialog.focusables();
    }

    @Override
    Widget initialFocus() {
        return dialog.initialFocus();
    }

    @Override
    boolean owns(Widget widget) {
        for (Widget w = widget; w != null; w = w.parent) {
            if (w == dialog) {
                return true;
            }
        }
        return false;
    }

    @Override
    void closed() {
        dialog.closed();
    }

    @Override
    protected void update() {
        dialog.update();
    }

    @Override
    void position(Canvas canvas, float windowWidth, float windowHeight) {
        place(canvas, 0, 0, windowWidth, windowHeight);
        boxWidth = Math.max(0, Math.min(WIDTH, windowWidth - MARGIN * 2));
        float contentWidth = Math.max(0, boxWidth - PADDING * 2);
        float contentHeight = dialog.measure(canvas, contentWidth).height();
        boxHeight = Math.min(contentHeight + PADDING * 2, windowHeight - MARGIN * 2);
        boxX = (windowWidth - boxWidth) / 2;
        boxY = Math.max(MARGIN, (windowHeight - boxHeight) / 2);
        dialog.place(canvas, boxX + PADDING, boxY + PADDING, contentWidth, contentHeight);
    }

    @Override
    Widget hitTest(float px, float py) {
        return dialog.hitTest(px, py); // the backdrop itself takes no clicks
    }

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        return new Size(getWidth(), getHeight());
    }

    @Override
    protected void draw(Canvas canvas) {
        Theme theme = canvas.getTheme();
        canvas.fillRect(getX(), getY(), getWidth(), getHeight(), 0, com.dervarex.unweird.style.Color.BLACK.withAlpha(theme.isDark() ? 0.55f : 0.35f));
        float radius = theme.getRadius() * 2;
        canvas.shadow(boxX, boxY, boxWidth, boxHeight, radius, 32, theme.getShadow().withAlpha(Math.min(1, theme.getShadow().a() * 3)));
        canvas.fillRect(boxX, boxY, boxWidth, boxHeight, radius, theme.getSurface());
        canvas.clip(boxX, boxY, boxWidth, boxHeight);
        dialog.draw(canvas);
        canvas.unclip();
    }
}
