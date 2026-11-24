package com.dervarex.unweird.game;

import com.dervarex.unweird.Canvas;
import com.dervarex.unweird.Component;
import com.dervarex.unweird.Cursor;
import com.dervarex.unweird.Pointer;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Size;
import com.dervarex.unweird.Widget;
import com.dervarex.unweird.annotation.Skip;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A {@link Game} or {@link Game3D} as a widget inside another screen, with the rest of your UI around it:
 *
 * <pre>{@code
 * Text title    = new Text("Today's level");
 * GameView play = new GameView(CoinJump.class).height(360);
 * }</pre>
 *
 * The game's own widgets (its score {@code Text}, ...) are drawn on top of it. Without {@link #height}, the view is as
 * tall as the game's {@link Resolution @Resolution} needs for the width it gets (16:9 for 3D).
 */
public class GameView extends Component {

    @Skip private final Screen game;
    private float preferredHeight = Float.NaN;

    /** Creates the game (its class needs a constructor without parameters) and shows it here. */
    public GameView(Class<? extends Screen> type) {
        if (!Game.class.isAssignableFrom(type) && !Game3D.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("GameView shows a Game or Game3D, but " + type.getSimpleName()
                    + " is a plain Screen: go(" + type.getSimpleName() + ".class) shows it instead");
        }
        game = Members.create(type);
        List<Widget> widgets = Members.valuesOf(game, Widget.class);
        if (!widgets.isEmpty()) {
            add(widgets.toArray(Widget[]::new));
        }
        if (game instanceof Game3D world) {
            world.host(new Game3D.Host() {
                @Override
                public boolean locked() {
                    return pointerLocked();
                }

                @Override
                public void lock() {
                    lockPointer();
                }

                @Override
                public void unlock() {
                    unlockPointer();
                }
            });
            whileShown(world::shown, world::hidden);
        } else {
            Game flat = (Game) game;
            whileShown(flat::shown, flat::hidden);
        }
    }

    /** The game being shown, e.g. to read its score. */
    @SuppressWarnings("unchecked")
    public <G extends Screen> G game() {
        return (G) game;
    }

    /** Height in logical pixels (the width follows the layout). */
    public GameView height(float height) {
        preferredHeight = height;
        return this;
    }

    @Override public GameView below(Widget other) { super.below(other); return this; }
    @Override public GameView above(Widget other) { super.above(other); return this; }
    @Override public GameView rightOf(Widget other) { super.rightOf(other); return this; }
    @Override public GameView leftOf(Widget other) { super.leftOf(other); return this; }
    @Override public GameView alignLeft(Widget other) { super.alignLeft(other); return this; }
    @Override public GameView alignRight(Widget other) { super.alignRight(other); return this; }
    @Override public GameView gap(float pixels) { super.gap(pixels); return this; }
    @Override public GameView visibleIf(BooleanSupplier condition) { super.visibleIf(condition); return this; }
    @Override public GameView enabledIf(BooleanSupplier condition) { super.enabledIf(condition); return this; }
    @Override public GameView tooltip(String text) { super.tooltip(text); return this; }

    @Override
    protected Size measure(Canvas canvas, float maxWidth) {
        if (!Float.isNaN(preferredHeight)) {
            return new Size(maxWidth, preferredHeight);
        }
        float aspect = game instanceof Game flat ? flat.aspect() : 9f / 16;
        return new Size(maxWidth, Math.round(maxWidth * aspect));
    }

    @Override
    protected void draw(Canvas canvas) {
        if (game instanceof Game flat) {
            flat.render(canvas, getX(), getY(), getWidth(), getHeight());
        } else {
            ((Game3D) game).render(canvas, getX(), getY(), getWidth(), getHeight());
        }
        super.draw(canvas);
        requestFrame();
    }

    @Override
    protected boolean clickable() {
        return true;
    }

    @Override
    protected Cursor cursor() {
        return game instanceof Game3D ? Cursor.CROSSHAIR : Cursor.ARROW;
    }

    @Override
    protected void onPress(Pointer pointer) {
        if (game instanceof Game flat) {
            flat.press(pointer);
        } else {
            ((Game3D) game).press(pointer);
        }
    }

    @Override
    protected boolean onMove(Pointer pointer) {
        return game instanceof Game3D world && world.move(pointer);
    }

    @Override
    protected boolean onScroll(Pointer at, double dx, double dy) {
        return game instanceof Game3D world && world.scroll(dy);
    }
}
