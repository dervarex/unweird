# Dialogs and toasts

## Dialogs

```java
show(Dialog.confirm("Delete “My world”?", "This can't be undone.")
        .action("Delete", this::delete).danger()
        .cancel("Keep it"));
```

- Everything behind it is blocked, Tab stays inside, Escape cancels.
- The first action is the default: Enter runs it.
- Focus starts on the first field, else on Cancel, so a stray Enter doesn't delete stuff.
- Actions close the dialog when they're done.

A dialog with a form: subclass it and declare fields, like a screen.

```java
class RenameDialog extends Dialog {
    Field name = new Field("New name");

    RenameDialog(World world) {
        super("Rename world");
        action("Rename", () -> {
            if (name.value().isBlank()) {
                stay();        // keep it open
                return;
            }
            world.rename(name.value());
        });
        cancel("Cancel");
    }
}

show(new RenameDialog(world));
```

Also there: `onCancel(...)`, `onClose(...)`, `close()`, `isOpen()`.

## Toasts

```java
toast("Saved");
toast(Toast.success("Installed Sodium"));
toast(Toast.error("Couldn't connect"));
toast(Toast.info("Hi").lasting(Duration.ofSeconds(10)));
```

Bottom right, gone after a few seconds, click to dismiss. Fine to call from any thread
(`Unweird.toast(...)` outside screens).

Next: [Styling](styling.md)
