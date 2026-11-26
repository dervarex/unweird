# Styling

No style sheets. A theme for the whole app, a few calls per widget. The inferred look is meant to be right most of
the time.

## Themes

```java
Unweird.theme(Theme.dark());
Unweird.theme(Theme.light().withAccent(Color.hex("#0d9488")).withRadius(12));
Unweird.theme().isDark();
```

Switch any time: every screen follows on the next frame. Set the first one with
`Unweird.app(...).theme(...)` so the window doesn't flash.

| Color | Used for | Light | Dark |
|---|---|---|---|
| `background` | Window | `#f5f5f7` | `#111114` |
| `surface` | Cards, panels, fields | `#ffffff` | `#1a1a1f` |
| `border` | Outlines, dividers | `#e6e6eb` | `#2b2b33` |
| `shadow` | Card shadows | | |
| `text` | Text | `#1c1c21` | `#ececf1` |
| `mutedText` | Subtitles, placeholders | `#6e6e78` | `#9c9ca8` |
| `accent` | Primary buttons, focus, selection | `#5b5bd6` | `#7c6cff` |
| `onAccent` | Text on the accent | | |
| `success` | Success toasts | `#2f9e5b` | `#3fb871` |
| `danger` | `danger()` buttons, error toasts | `#d93d42` | `#e5484d` |
| `onDanger` | Text on danger | | |
| `radius` | Corners of buttons and fields; cards use twice this | | |

Each has a getter (`getAccent()`) and a `withX(...)` that returns a copy.

### Your own theme

```java
Theme midnight = new Theme(
        Color.hex("#0b1020"),   // background
        Color.hex("#131a2e"),   // surface
        Color.hex("#222b45"),   // border
        Color.rgba(0, 0, 0, 0.5f), // shadow
        Color.hex("#e6e9f5"),   // text
        Color.hex("#8a93b2"),   // mutedText
        Color.hex("#4f8cff"),   // accent
        Color.WHITE,            // onAccent
        Color.hex("#34c38f"),   // success
        Color.hex("#ff5c70"),   // danger
        Color.WHITE,            // onDanger
        10);                    // radius
```

### A dark mode toggle that keeps the accent

```java
Toggle dark = new Toggle("Dark mode")
        .checked(Unweird.theme().isDark())
        .onChange(on -> Unweird.theme((on ? Theme.dark() : Theme.light())
                .withAccent(Unweird.theme().getAccent())
                .withRadius(Unweird.theme().getRadius())));
```

Full example: [Settings and themes](/examples/settings).

## Colors

```java
Color.hex("#7c6cff");
Color.hex("#7c6cff80");            // with alpha
Color.rgba(124, 108, 255, 0.5f);
Color.WHITE; Color.BLACK; Color.TRANSPARENT;

accent.mix(Color.WHITE, 0.2f);     // 20% towards white
accent.withAlpha(0.3f);
```

## Single widgets

```java
new Button("Delete").danger();
new Button("Later").ghost();
new Button("Export").secondary();
new Text("v1.2").muted();
new Text("Error!").color(Color.hex("#f87171"));
new Text("Big").heading();
new Image("/logo.png").radius(12);
```

| Widget | Calls |
|---|---|
| `Text` | `heading()`, `title()`, `body()`, `muted()`, `color(...)`, `maxLines(n)` |
| `Button` | `primary()`, `secondary()`, `ghost()`, `danger()` |
| `Image` | `size(w, h)`, `radius(r)` |
| `Component` | `@Card` on the class |

Need more? Draw it: [Your own widgets](custom-widgets.md).

## Fonts

Noto Sans is built in, with fallbacks for symbols, arrows, math and emoji (monochrome). For other scripts add a
font from your resources:

```java
Unweird.app("My App").fallbackFont("/fonts/NotoSansJP-Regular.ttf").start(HomeScreen.class);
```
