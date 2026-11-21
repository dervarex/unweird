package com.dervarex.unweird;

import com.dervarex.unweird.annotation.Skip;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A modal dialog: everything behind it is blocked until it closes.
 *
 * <pre>{@code
 * show(new Dialog("Delete “My world”?", "This can't be undone.")
 *         .action("Delete", this::delete).danger()
 *         .cancel("Keep it"));
 * }</pre>
 *
 * For forms, subclass it and declare fields like on a screen; they're added between the message and the buttons:
 *
 * <pre>{@code
 * class RenameDialog extends Dialog {
 *     Field name = new Field("New name");
 *     RenameDialog() {
 *         super("Rename world");
 *         action("Rename", () -> {
 *             if (name.value().isBlank()) { stay(); return; } // keep it open
 *             rename(name.value());
 *         });
 *         cancel("Cancel");
 *     }
 * }
 * }</pre>
 *
 * Actions close the dialog after running unless they call {@link #stay()}. The first action is the default:
 * Enter runs it (unless a focused widget uses Enter itself). Escape cancels.
 */
public class Dialog extends Component {

    @Getter private final Text title;
    @Getter private final Text message;

    @Skip private final ButtonBar buttons = new ButtonBar(); // added after everything else, see trailingChildren()
    private final List<Runnable> cancelHandlers = new CopyOnWriteArrayList<>();
    private final List<Runnable> closeHandlers = new CopyOnWriteArrayList<>();
    @Skip private Button defaultAction;
    @Skip private Button cancelButton;
    @Skip private Button lastAction;
    private boolean stayOpen;

    /** The layer showing this dialog while it's open. */
    @Skip ModalLayer layer;

    public Dialog(String title) {
        this(title, "");
    }

    public Dialog(String title, String message) {
        this.title = new Text(Objects.requireNonNull(title, "title")).title();
        this.message = new Text(Objects.requireNonNull(message, "message")).muted();
    }

    /** A plain dialog with a title and message; add {@link #action}s and a {@link #cancel}. */
    public static Dialog confirm(String title, String message) {
        return new Dialog(title, message);
    }

    /** Adds a button that runs {@code action} and then closes the dialog. The first one is the default (Enter). */
    public Dialog action(String label, Runnable action) {
        Objects.requireNonNull(action, "action");
        Button button = new Button(label);
        button.onClick(() -> {
            stayOpen = false;
            action.run();
            if (!stayOpen) {
                close();
            }
        });
        if (defaultAction == null) {
            defaultAction = button.primary();
        } else {
            button.secondary();
        }
        lastAction = button;
        buttons.actions.add(button);
        return this;
    }

    /** Styles the action added last as destructive (red). */
    public Dialog danger() {
        if (lastAction == null) {
            throw new IllegalStateException("danger() styles the last action; add one with action(...) first");
        }
        lastAction.danger();
        return this;
    }

    /** Adds a cancel button. Escape does the same, with or without one. */
    public Dialog cancel(String label) {
        cancelButton = new Button(label).secondary().onClick(this::cancel);
        return this;
    }

    /** Runs {@code handler} when the dialog is cancelled (button or Escape). */
    public Dialog onCancel(Runnable handler) {
        cancelHandlers.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    /** Runs {@code handler} whenever the dialog closes, however it closes. */
    public Dialog onClose(Runnable handler) {
        closeHandlers.add(Objects.requireNonNull(handler, "handler"));
        return this;
    }

    /** Call inside an action to keep the dialog open (e.g. when input is invalid). */
    protected final void stay() {
        stayOpen = true;
    }

    public boolean isOpen() {
        return layer != null;
    }

    /** Closes the dialog. */
    public void close() {
        ModalLayer open = layer;
        if (open != null) {
            Unweird.closeOverlay(open);
        }
    }

    void cancel() {
        for (Runnable handler : cancelHandlers) {
            handler.run();
        }
        close();
    }

    /** Enter pressed and nothing focused used it. */
    boolean runDefault() {
        if (defaultAction == null || !defaultAction.isEnabled()) {
            return false;
        }
        defaultAction.click();
        return true;
    }

    /** Where focus starts: the first input, else Cancel (so Enter never confirms something by accident), else the default. */
    Widget initialFocus() {
        for (Widget widget : focusables()) {
            if (!buttons.children().contains(widget)) {
                return widget;
            }
        }
        if (cancelButton != null) {
            return cancelButton;
        }
        return defaultAction;
    }

    void closed() {
        layer = null;
        for (Runnable handler : closeHandlers) {
            handler.run();
        }
    }

    @Override
    List<Widget> trailingChildren() {
        buttons.refresh(cancelButton);
        return List.of(buttons);
    }

    /** The row of buttons at the bottom, right-aligned: cancel, then actions with the default rightmost. */
    static final class ButtonBar extends Container {

        private static final float GAP = 8;
        final List<Button> actions = new ArrayList<>();

        ButtonBar() {
            add();
        }

        void refresh(Button cancel) {
            List<Widget> order = new ArrayList<>();
            if (cancel != null) {
                order.add(cancel);
            }
            for (int i = actions.size() - 1; i >= 0; i--) {
                order.add(actions.get(i));
            }
            replaceChildren(order);
        }

        @Override
        protected Size measure(Canvas canvas, float maxWidth) {
            float height = 0;
            for (Widget child : children()) {
                if (child instanceof Button button) {
                    button.resolved = button.variant != null ? button.variant : Button.Variant.SECONDARY;
                }
                height = Math.max(height, child.measure(canvas, maxWidth).height());
            }
            return new Size(maxWidth, height);
        }

        @Override
        protected void arrange(Canvas canvas) {
            float right = getX() + getWidth();
            List<Widget> kids = children();
            for (int i = kids.size() - 1; i >= 0; i--) {
                Widget child = kids.get(i);
                Size size = child.measure(canvas, getWidth());
                right -= size.width();
                child.place(canvas, right, getY() + (getHeight() - size.height()) / 2, size.width(), size.height());
                right -= GAP;
            }
        }
    }
}
