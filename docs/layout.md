# Layout

You mostly don't write layout. unweird looks at what you declared and picks something sensible. When it guesses
wrong, you nudge single widgets.

## The rules

| What you declare | What you get |
|---|---|
| Only texts, fields, buttons, checkboxes, toggles, sliders, dropdowns (a form), and it fits | A centered card |
| Anything else (lists, `Each`, cards, images, 3D), or too tall | A page: a column up to 640px wide, top-aligned, scrolling |
| The first text | Heading |
| A text right after the heading | Grey subtitle |
| The first text inside a component | Section title |
| Buttons next to each other | One row. The first is filled, the others outlined |
| A row that doesn't fit | Wraps |
| A text whose value is `""` | Takes no space |

A logo image before the heading doesn't stop it from being the heading.

```java
public class LoginScreen extends Screen {
    Image logo     = new Image("/logo.png").size(56, 56);
    Text heading   = new Text("Welcome back");          // heading
    Text subtitle  = new Text("Sign in to continue");   // subtitle
    Field user     = new Field("Username");
    Field pass     = new Field("Password").masked();
    Button signIn  = new Button("Sign in");             // filled
    Button signUp  = new Button("Create account");      // outlined, same row
}
```

## Nudging

| Call | Does |
|---|---|
| `below(x)` / `above(x)` | Moves it right under / over `x` |
| `rightOf(x)` / `leftOf(x)` | Puts it in `x`'s row |
| `alignLeft(x)` / `alignRight(x)` | Natural width, edge lined up with `x` |
| `gap(px)` | Space before it, instead of the usual |

They return the widget, so chain them where you declare it. `x` must be declared above (Java rule) and live in the
same screen or component.

### A field with a button next to it

```java
Field query   = new Field("Search");
Button search = new Button("Go").rightOf(query);
```

### "Remember me" and "Forgot password?" in one row

```java
Field pass        = new Field("Password").masked();
Button forgot     = new Button("Forgot password?").ghost().alignRight(pass).gap(4);
Checkbox remember = new Checkbox("Remember me").leftOf(forgot).alignLeft(pass);
```

### A hint glued to its field

```java
Field email    = new Field("Email");
Text emailHint = new Text(this::emailProblem).gap(4);   // "" when the email is fine: no space taken
```

### Declared later, shown earlier

```java
Button help = new Button("Help").ghost().above(heading);
```

## Showing and hiding

```java
Text error  = new Text("Wrong password").visibleIf(() -> failed);           // hidden: takes no space
Button save = new Button("Save").enabledIf(() -> !name.value().isBlank());  // dimmed, can't click or Tab to it
```

Both are checked after every event. Components have them too; they return `Widget` there, so call them in an
initializer block:

```java
Toggle showAdvanced      = new Toggle("Advanced options");
AdvancedSection advanced = new AdvancedSection();

{
    advanced.visibleIf(showAdvanced::checked);
}
```

## Lists of things

`Each` makes one widget per item and keeps up with the list:

```java
@State final List<World> worlds = new ArrayList<>();
Each<World> rows = new Each<>(() -> worlds, WorldRow::new);
```

- Items are matched with `equals`. A row keeps its widget (and whatever is typed into it) while its item stays
  in the list. Records are perfect; for mutable classes, don't override `equals`.
- Sorting or filtering the list reorders the rows without recreating them.
- `rows.viewOf(item)` is the widget for an item.

```java
public class WorldRow extends Component {
    final Text name;
    final Button play;

    WorldRow(World world) {
        name = new Text(world.name());
        play = new Button("Play").rightOf(name).onClick(() -> launch(world));
    }
}
```

For plain text rows with selection, `ListView` is simpler ([Widgets](widgets.md#listview)).

## Sidebars and headers

Docked components are laid out separately, see [`@Default`](screens-and-components.md#on-every-screen-default).

## When it's still wrong

The layout code is package-private on purpose. If something can't be expressed with the calls above,
[open an issue](https://github.com/dervarex/unweird/issues) with a screenshot: the rules get better instead of
growing an escape hatch. For a widget that draws something unusual, [write your own](custom-widgets.md).
