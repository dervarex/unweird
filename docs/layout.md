# Layout

You mostly don't write layout. unweird looks at what you declared and picks something sensible.
When it guesses wrong, you nudge single widgets.

## What it does on its own

- **Card or page.** If a screen only has texts, fields, buttons, checkboxes and such (a form), and it fits,
  it's centered on a card. Anything else, or too tall, becomes a page: a column up to 640px wide, top-aligned,
  scrolling if needed.
- **Top to bottom** in declaration order.
- **First text = heading.** A text right after it is a grey subtitle. Inside a component the first text is a
  smaller section title. A logo image before the heading doesn't count.
- **Buttons next to each other share a row.** The first is the main (filled) one, the others are outlined.
- **Rows that don't fit wrap** onto the next line.
- **Empty texts take no space.** Handy for status lines: `new Text(() -> message)`.
- **Components** get the same treatment inside, so a form looks the same in a component or directly on a screen.

## Nudging

```java
Button forgot = new Button("Forgot password?").ghost()
        .below(pass)           // right under pass, wherever it was declared
        .alignRight(pass)      // natural width, right edge lined up with pass
        .gap(4);               // 4px above it instead of the usual spacing
```

| Call | Does |
|---|---|
| `below(x)` / `above(x)` | moves it right under/over `x` |
| `rightOf(x)` / `leftOf(x)` | puts it in `x`'s row |
| `alignLeft(x)` / `alignRight(x)` | natural width, edge lined up with `x` |
| `gap(px)` | space before it |

They return the widget, so chain them where you declare it. The other widget has to be declared above (Java rule)
and be in the same screen or component.

A classic "remember me" row:

```java
Button forgot = new Button("Forgot password?").ghost().alignRight(pass);
Checkbox remember = new Checkbox("Remember me").leftOf(forgot).alignLeft(pass);
```

## Showing and hiding

```java
Text error = new Text("Wrong password").visibleIf(() -> failed);    // hidden = takes no space
Button save = new Button("Save").enabledIf(() -> !name.value().isBlank());   // dimmed, can't click or Tab to it
```

Both are checked after every event.

## Lists of things

`Each` makes one widget per item of a list and keeps up with the list:

```java
List<World> worlds = new ArrayList<>();
Each<World> rows = new Each<>(() -> worlds, WorldRow::new);
```

Items are matched with `equals` (records are perfect), so a row keeps its state while its item stays in the list.

Next: [Widgets](widgets.md)
