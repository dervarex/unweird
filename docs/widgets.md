# Widgets

All widgets can do `below/rightOf/alignRight/gap` ([layout](layout.md)), `visibleIf`, `enabledIf` and
`tooltip("...")`. Setters return the widget, so you can set everything where you declare it.

## Text

```java
new Text("Hello");
new Text(() -> "Hi " + user.value());   // live, see state-and-events
new Text(longStory).maxLines(2);         // cut with "…"
```

Style is picked from position (heading, subtitle, body). Force it with `.heading()`, `.title()`, `.body()`,
`.muted()`, or `.color(Color.hex("#e33"))`. Change it later with `text.text("new")`.

## Button

```java
new Button("Save").onClick(this::save);
new Button(() -> dark ? "Light mode" : "Dark mode");   // live label
```

Looks: `.primary()` filled, `.secondary()` outlined, `.ghost()` just text, `.danger()` red. Without one it's
chosen for you. Enter or Space clicks a focused button.

## Field

```java
Field name = new Field("Name");                    // the text is a placeholder
Field pass = new Field("Password").masked();       // dots, no copy/cut
name.value();          // read
name.value("Alex");    // set (clears undo)
name.onChange(v -> ...).onSubmit(this::save);      // every edit / Enter
```

All the usual keys: arrows, Ctrl+arrows by word, Shift to select, Home/End, Ctrl+A/C/X/V, Ctrl+Z/Ctrl+Y undo,
double click selects a word, triple click everything.

## Checkbox and Toggle

```java
Checkbox remember = new Checkbox("Remember me");
Toggle dark = new Toggle("Dark mode").checked(true).onChange(on -> ...);
remember.checked();   // read
```

Toggle has the label left and the switch right, for settings.

## Slider

```java
new Slider("Volume", 0, 100).step(5).value(40).format(v -> v + "%").onChange(v -> ...);
```

Drag, arrow keys, Page Up/Down, mouse wheel while focused.

## Dropdown

```java
Dropdown<Language> lang = new Dropdown<>("Language", List.of(Language.values()))
        .labels(Language::displayName)
        .select(Language.EN)
        .onChange(l -> ...);
lang.selected();
```

Works with the keyboard too (arrows, Enter, Escape).

## ListView

```java
ListView<String> list = new ListView<>(() -> tasks)     // live list
        .empty("Nothing to do")
        .onSelect(t -> ...);
list.selected();
```

For rows with more than text, use `Each` with your own component ([layout](layout.md#lists-of-things)).

## Image

```java
new Image("/images/logo.png").size(56, 56);     // classpath
Image.file(Path.of("cover.jpg"));
Image.url("https://.../icon.webp").radius(8);   // downloaded in the background
```

PNG, JPEG, GIF, BMP, TGA. WebP works when the TwelveMonkeys ImageIO plugin is on the classpath
(`unweird-install` brings it).

Next: [State and events](state-and-events.md)
