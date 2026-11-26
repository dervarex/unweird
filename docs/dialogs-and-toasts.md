# Dialogs and toasts

## Confirmations

```java
show(Dialog.confirm("Delete “My world”?", "This can't be undone.")
        .action("Delete", this::delete).danger()
        .cancel("Keep it"));
```

- Everything behind it is blocked. Tab stays inside, Escape cancels.
- The first action is the default: Enter runs it.
- Focus starts on the first field, else on Cancel, so a stray Enter doesn't delete anything.
- Actions close the dialog when they return.

| | |
|---|---|
| `Dialog(title)` / `Dialog(title, message)` | |
| `Dialog.confirm(title, message)` | The same, reads better |
| `action(label, Runnable)` | A button. Several allowed, the first is the default |
| `danger()` | Makes the last added action red |
| `cancel(label)` | A button that just closes |
| `onCancel(Runnable)` | Escape or the cancel button (the back gesture on Android) |
| `onClose(Runnable)` | However it closes |
| `close()` / `isOpen()` | |
| `stay()` | Inside an action: keep it open (protected, for subclasses) |

`show(...)` inside screens and components, `Unweird.show(...)` anywhere (any thread).

### Three choices

```java
show(new Dialog("Unsaved changes", "Save before closing?")
        .action("Save", this::saveAndClose)
        .action("Don't save", this::close).danger()
        .cancel("Cancel"));
```

## Dialogs with a form

Subclass it and declare fields, like a screen:

```java
class RenameDialog extends Dialog {
    @State String error = "";

    Field name   = new Field("New name");
    Text problem = new Text(() -> error).muted();

    RenameDialog(World world) {
        super("Rename world");
        name.value(world.name());
        action("Rename", () -> {
            if (name.value().isBlank()) {
                error = "A name, please.";
                stay();               // keep it open
                return;
            }
            world.rename(name.value());
        });
        cancel("Cancel");
    }
}

show(new RenameDialog(world));
```

### Asking for a value

Pass a callback in:

```java
class AskDialog extends Dialog {
    Field answer = new Field("");

    AskDialog(String question, Consumer<String> then) {
        super(question);
        action("OK", () -> then.accept(answer.value()));
        cancel("Cancel");
    }
}

show(new AskDialog("Name of the new folder?", name -> folders.add(name)));
```

Full example: [Dialogs and toasts](/examples/dialogs).

## Toasts

```java
toast("Saved");
toast(Toast.success("Installed Sodium"));
toast(Toast.error("Couldn't connect"));
toast(Toast.info("Hi").lasting(Duration.ofSeconds(10)));
```

Bottom right, gone after a few seconds, click to dismiss. `Unweird.toast(...)` works anywhere, on any thread:

```java
executor.submit(() -> {
    export();
    Unweird.toast(Toast.success("Exported"));
});
```
