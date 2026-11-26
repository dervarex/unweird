# State and events

## State is just fields

```java
public class CounterScreen extends Screen {
    @State int clicks = 0;
    Text label  = new Text(() -> "Clicked " + clicks + " times");
    Button plus = new Button("+1").onClick(() -> clicks++);
}
```

After every event (click, key, focus change, navigation, a timer, an `async` result) unweird runs every
`() -> ...` again, lays out and redraws. Change a field and the UI follows. No observables, no binding.

`@State` does nothing at runtime; it tells readers "the UI depends on this".

### What's live

Anything that takes a lambda is re-evaluated after every event:

```java
new Text(() -> ...)                       // the text
new Button(() -> ...)                     // the label
new ListView<>(() -> items)               // the items
new Each<>(() -> items, Row::new)         // the rows
widget.visibleIf(() -> ...)
widget.enabledIf(() -> ...)
```

Keep them cheap: they run on every event. Compute expensive things once (in `then`, an `onChange`) and store
the result in a field.

### Collections

Mutate them in place or replace them, both work:

```java
@State final List<Task> tasks = new ArrayList<>();
tasks.add(task);                    // fine

@State List<Book> results = List.of();
results = found;                    // also fine
```

## Changes from other threads

The UI thread re-reads your fields after its own events. A change made on another thread needs a nudge:

```java
new Thread(() -> {
    progress = download();
    Unweird.refresh();            // or refresh() inside screens and components
}).start();
```

Better: use `async`, which does that for you.

### Listeners from other libraries

Read their state straight in your lambdas, and let the listener only say "redraw":

```java
private final LoginStateListener redraw = state -> refresh();

{
    whileShown(() -> Auth.addListener(redraw), () -> Auth.removeListener(redraw));
}

Text status = new Text(() -> Auth.state().message());
```

## Background work

```java
async(() -> api.loadWorlds())                     // a background (virtual) thread
        .then(worlds -> this.worlds = worlds)     // back on the UI thread
        .failed(e -> status = e.getMessage());    // optional, also UI thread
```

- Without `failed`, an error shows up as a red toast. Never silently.
- Chain `then`/`failed` in the same statement.
- `.future()` gives the `CompletableFuture` if you need to combine it.

### Loading states

```java
@State boolean loading;
Text status = new Text(() -> loading ? "Loading…" : "");

private void reload() {
    loading = true;
    async(api::fetch)
            .then(data -> { this.data = data; loading = false; })
            .failed(e -> { loading = false; toast(Toast.error(e.getMessage())); });
}
```

### Search as you type

Later answers can overtake earlier ones. Number the requests and drop old answers:

```java
private int latest;

Field query = new Field("Search").onChange(q -> search());

private void search() {
    int mine = ++latest;
    async(() -> api.search(query.value())).then(found -> {
        if (mine == latest) results = found;
    });
}
```

Full example: [Async search](/examples/search).

## Timers

```java
Unweird.later(() -> ...);                                  // UI thread, after the current event
Runnable cancel = Unweird.after(Duration.ofSeconds(2), () -> ...);   // UI thread, later
cancel.run();                                              // changed your mind
```

A repeating timer schedules itself again:

```java
private Runnable cancelTick = () -> { };

private void tick() {
    secondsLeft--;
    if (secondsLeft > 0) cancelTick = Unweird.after(Duration.ofSeconds(1), this::tick);
}

@Override protected void onHide() { cancelTick.run(); }
```

Each run counts as an event, so live texts update. For smooth per-frame animation inside a custom widget, use
[`requestFrame()`](custom-widgets.md#animation) instead. Full example: [Stopwatch](/examples/stopwatch).

## Shortcuts

```java
@Shortcut("Ctrl+N")
void newTask() { task.focus(); }

@Shortcut({"F5", "Ctrl+R"})
void reload() { ... }

{ shortcut("Ctrl+F", this::find); }          // at runtime; the same keys again replace it

Unweird.shortcut("Ctrl+Q", Unweird::quit);   // app-wide, before start
```

- On instance methods without parameters (any visibility) of screens and components, docked ones too.
- If the focused widget uses the key itself (Ctrl+A in a field, Space on a button), the shortcut doesn't fire.
- Nearest wins: the component around the focused widget, then the screen, then docked components, then app-wide.
- Letters follow the keyboard layout: Ctrl+Z is the key with Z on it, on QWERTZ too.
- On macOS, `Ctrl` also matches Cmd.

### Key names

| | |
|---|---|
| Modifiers | `Ctrl`, `Shift`, `Alt` (`Option`), `Meta` (`Cmd`, `Super`, `Win`) |
| Letters, digits | `A`…`Z`, `0`…`9` |
| Function keys | `F1`…`F12` |
| Others | `Enter`, `Escape` (`Esc`), `Tab`, `Space`, `Backspace`, `Delete` (`Del`), `Insert`, `Home`, `End`, `PageUp`, `PageDown`, `Left`, `Right`, `Up`, `Down`, `Plus`, `Minus` |

Case doesn't matter. `"Ctrl++"` means Ctrl and Plus. `Enter` is also `Return`.

::: warning Typos
`"Ctl+S"` isn't an error: the last word is the key, so it's read as plain S. The [IntelliJ plugin](intellij.md)
flags misspelled shortcuts while you type.
:::

## Focus

```java
pass.focus();
```

Tab and Shift+Tab go through everything focusable in declaration order. The first field on a screen gets focus
when the screen opens. Clicking elsewhere drops focus.

## Links and files

```java
Unweird.open("https://example.com");     // default browser
Unweird.open("mailto:me@example.com");
Unweird.open(Path.of("logs"));            // folders and files in their default app
```

Returns false if nothing could be started (no `xdg-open`, say); show the link yourself then. Don't use
`java.awt.Desktop`: unweird runs AWT headless.

## Files dropped on the window

Override `onDrop` on a screen, component or widget:

```java
@Override
protected boolean onDrop(Pointer at, List<Path> files) {
    files.forEach(this::load);
    return true;   // taken; false passes it to the parent
}
```

The innermost widget under the pointer gets it first. Full example: [Dropping files](/examples/files).
