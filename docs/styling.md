# Styling

## Themes

```java
Unweird.theme(Theme.dark());
Unweird.theme(Theme.light().withAccent(Color.hex("#0d9488")).withRadius(12));
Unweird.theme().isDark();   // current one
```

Switch any time; every screen follows right away.

A theme is a handful of colors plus a corner radius: `background`, `surface` (cards, panels), `border`, `shadow`,
`text`, `mutedText`, `accent`, `onAccent`, `success`, `danger`, `onDanger`, `radius`. Every one has a `withX(...)`.
For a completely custom one use the constructor.

## Single widgets

No style sheets. Each widget has a few calls:

```java
new Button("Delete").danger();
new Button("Later").ghost();
new Text("v1.2").muted();
new Text("Error!").color(Color.hex("#e33"));
```

That's on purpose: the inferred look is meant to be right most of the time.

## Fonts

Noto Sans is built in, with fallbacks for symbols, arrows, math and emoji (black and white).
For other scripts add a font from your resources:

```java
Unweird.app("My App").fallbackFont("/fonts/NotoSansJP-Regular.ttf").start(...);
```

Next: [Your own widgets](custom-widgets.md)
