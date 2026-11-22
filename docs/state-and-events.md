# State and events

## State is just fields

```java
public class CounterScreen extends Screen {
    @State int clicks = 0;
    Text label  = new Text(() -> "Clicked " + clicks + " times");
    Button plus = new Button("+1").onClick(() -> clicks++);
}
```

After every event (click, key, focus change, navigation) unweird runs all the `() -> ...` lambdas again and
redraws what changed. So you change a field, the UI follows. `@State` does nothing, it's just a label for readers.

Changed something from another thread? Call `refresh()` (or `Unweird.refresh()`).

## Background work

```java
async(() -> api.loadWorlds())                 // runs on a virtual thread
        .then(worlds -> this.worlds = worlds)   // back on the UI thread
        .failed(e -> status = e.getMessage());  // optional
```

Without `failed` an error shows up as a red toast, never silently. Set up `then`/`failed` right away, in the same
statement.

Other helpers:

```java
Unweird.later(() -> ...);                              // UI thread, after the current event
Unweird.after(Duration.ofSeconds(2), () -> ...);       // UI thread, later
```

## Shortcuts

```java
@Shortcut("Ctrl+N")
void newTask() { task.focus(); }

@Shortcut({"F5", "Ctrl+R"})
void reload() { ... }

{ shortcut("Ctrl+F", this::find); }           // dynamic, same keys again replace it

Unweird.shortcut("Ctrl+Q", Unweird::quit);    // app-wide, before start
```

- Works on screen and component methods (also docked ones).
- If the focused widget uses the key itself (Ctrl+A in a field), the shortcut doesn't fire.
- Nearest wins: the component around the focused widget, then the screen, then docks, then app-wide.
- Letters follow the keyboard layout: Ctrl+Z is the key with Z on it, on QWERTZ too.
- On macOS, `Ctrl` also means Cmd.

## Focus

```java
pass.focus();
```

Tab and Shift+Tab go through everything in declaration order. The first field on a screen gets focus when the
screen opens.

## Files dropped on the window

Override `onDrop` on a screen, component or widget:

```java
@Override
protected boolean onDrop(Pointer at, List<Path> files) {
    load(files.getFirst());
    return true;   // taken; false passes it to the parent
}
```

Next: [Dialogs and toasts](dialogs-and-toasts.md)
