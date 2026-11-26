# Widgets

Every widget has the [layout calls](layout.md#nudging) (`below`, `rightOf`, `alignRight`, `gap`...),
`visibleIf`, `enabledIf` and `tooltip("...")`. Setters return the widget, so everything goes where you declare it.

## Text

```java
new Text("Hello");
new Text(() -> "Hi " + user.value());      // live: re-read after every event
new Text(longStory).maxLines(2);            // cut with "…"
new Text("v1.2").muted();
new Text("Error!").color(Color.hex("#f87171"));
```

| | |
|---|---|
| `Text(String)` / `Text(Supplier<String>)` | Fixed or live |
| `text()` / `text("new")` | Read / replace (a fixed text) |
| `heading()` `title()` `body()` | Force a role instead of the inferred one |
| `muted()` | Grey, like a subtitle |
| `color(Color)` | Any color |
| `maxLines(n)` | Wrap at most `n` lines, then "…" |

The role comes from the position: first text is the heading, the one after it the subtitle, first text in a
component a section title. Texts wrap to the available width. `""` takes no space.

## Button

```java
new Button("Save").onClick(this::save);
new Button(() -> playing ? "Pause" : "Play").onClick(this::toggle);   // live label
new Button("Delete").danger();
new Button("Forgot password?").ghost();
```

| | |
|---|---|
| `onClick(Runnable)` | Click, Enter or Space when focused |
| `label()` / `label("new")` | Read / replace |
| `primary()` | Filled with the accent color |
| `secondary()` | Outlined |
| `ghost()` | Just text |
| `danger()` | Red |

Without a style call it's picked for you: the first button of a row is primary, the rest secondary; buttons in a
sidebar look like navigation items.

## Field

```java
Field name = new Field("Name");                     // the text is the placeholder
Field pass = new Field("Password").masked();
name.value();                                       // read
name.value("Alex");                                 // set (clears undo)
name.onChange(v -> search(v)).onSubmit(this::save); // every edit / Enter
name.focus();
```

| | |
|---|---|
| `value()` / `value(String)` | Read / set |
| `masked()` | Dots, no copy or cut; a password keyboard on Android |
| `onChange(Consumer<String>)` | After every edit |
| `onSubmit(Runnable)` | Enter |
| `placeholder()` | The grey text |

Keys: arrows, Ctrl+arrows by word, Shift to select, Home/End, Ctrl+A/C/X/V, Ctrl+Z / Ctrl+Y (and Ctrl+Shift+Z).
Double click selects a word, triple click everything.

## Checkbox and Toggle

```java
Checkbox remember = new Checkbox("Remember me");
Toggle dark = new Toggle("Dark mode").checked(true).onChange(on -> Unweird.theme(on ? Theme.dark() : Theme.light()));

remember.checked();       // read
remember.checked(false);  // set
```

Same API. Checkbox: box left of the label, for forms. Toggle: label left, switch right, for settings.

## Slider

```java
Slider volume = new Slider("Volume", 0, 100)
        .step(5)
        .value(40)
        .format(v -> Math.round(v) + "%")
        .onChange(v -> player.volume(v / 100));
```

| | |
|---|---|
| `Slider(label, min, max)` | |
| `value()` / `value(double)` | Read / set |
| `step(double)` | Snap to multiples |
| `format(DoubleFunction<String>)` | The value shown right of the label |
| `onChange(DoubleConsumer)` | While dragging and on keys |

Drag, click on the track, arrow keys, Page Up/Down, Home/End, mouse wheel while focused.

## Dropdown

```java
enum Language { EN, DE, FR }

Dropdown<Language> lang = new Dropdown<>("Language", List.of(Language.values()))
        .labels(l -> switch (l) { case EN -> "English"; case DE -> "Deutsch"; case FR -> "Français"; })
        .select(Language.EN)
        .onChange(l -> i18n.use(l));

lang.selected();
lang.options(List.of(Language.EN, Language.DE));   // replace the options
```

| | |
|---|---|
| `Dropdown(placeholder, options)` | The placeholder shows while nothing is selected |
| `labels(Function<T, String>)` | Default: `toString()` |
| `select(T)` / `selected()` | Set / read |
| `options()` / `options(List<T>)` | Read / replace |
| `onChange(Consumer<T>)` | |

Keyboard: arrows, Enter, Escape.

## ListView

```java
ListView<String> list = new ListView<>(() -> tasks)    // live list
        .empty("Nothing to do")
        .onSelect(task -> details = task);

list.selected();
```

| | |
|---|---|
| `ListView(List<T>)` / `ListView(Supplier<List<T>>)` | Fixed or live |
| `labels(Function<T, String>)` | Default: `toString()` |
| `empty(String)` | Shown when the list is empty |
| `select(T)` / `selected()` | |
| `onSelect(Consumer<T>)` | Click or arrow keys |

For rows with more than text, use [`Each`](layout.md#lists-of-things) with a component.

## Image

```java
new Image("/images/logo.png").size(56, 56);   // classpath (src/main/resources)
Image.file(Path.of("cover.jpg"));
Image.url("https://example.com/icon.png").size(48, 48).radius(8);   // downloaded in the background
```

| | |
|---|---|
| `size(w, h)` | Display size in logical pixels; otherwise its natural size, fit to the width |
| `radius(r)` | Rounded corners |

PNG, JPEG, GIF, BMP, TGA. WebP works when the TwelveMonkeys ImageIO plugin is on the classpath (`unweird-install`
brings it). URL images are downloaded once per run; a placeholder shows meanwhile.

## Each

One widget per list item, see [Lists of things](layout.md#lists-of-things).

```java
Each<Book> results = new Each<>(() -> books, BookCard::new);
```

## Tooltips

```java
new Button("Sync").tooltip("Uploads your changes. Ctrl+S");
```

Shows after hovering a moment.

## Not built in

Missing a widget? Most are 50 lines: see [Your own widgets](custom-widgets.md), and the
[rating](/examples/rating) and [chart](/examples/chart) examples.
