# unweird — design

A Java UI library with its own renderer and an API that stays out of your way:
no string IDs, no layout boilerplate, no `bind().map()` chains.

## Target

| | |
|---|---|
| Language | Java 21 (compiled with `--release 21`) |
| Rendering | Own renderer: LWJGL (GLFW window + OpenGL) with NanoVG for shapes and text |
| Platforms | Linux first, Windows later, macOS nice-to-have |
| Getters | Lombok (`@Getter`, `@With`) in the library and in user code — never hand-written |
| Versions | `gradle/libs.versions.toml` |

## Modules

| Module | Contents |
|---|---|
| `unweird-core` | App runtime, renderer, layout engine, base widgets (Text, Button, Field, …), annotations |
| `unweird-processor` | Compile-time annotation processor (field order + `@Default` index) |
| `unweird-components` | Ready-made composites (login form, settings panel, …) |
| `unweird-anim` | Animations & transitions (v2) |
| `example` | The login/home demo app |

More `unweird-*` modules can be added later.

## The whole API in one example

```java
// components/Sidebar.java
@Default(Side.LEFT)
public class Sidebar extends Component {
    @Getter private final Button home     = new Button("Home").onClick(() -> go(HomeScreen.class));
    @Getter private final Button settings = new Button("Settings");
}

// screens/LoginScreen.java
@Title("Sign in")
@Without({Sidebar.class, Header.class})
public class LoginScreen extends Screen {
    Text heading  = new Text("Welcome back");
    Text subtitle = new Text("Sign in to continue");
    Field user    = new Field("Username");
    Field pass    = new Field("Password").masked();
    Text greeting = new Text(() -> "Hi " + user.value());   // live while typing
    Button signIn = new Button("Sign in").onClick(() -> go(HomeScreen.class));
}

// screens/HomeScreen.java
@Title("Home")
public class HomeScreen extends Screen {
    @State int clicks = 0;

    Text welcome = new Text("Welcome home!");
    Text counter = new Text(() -> "Clicked " + clicks + " times");
    Button plus  = new Button("+1").onClick(() -> clicks++);
    Button logout = new Button("Log out").onClick(() -> go(LoginScreen.class));
}

// Example.java
public class Example {
    public static void main(String[] args) {
        Unweird.start(LoginScreen.class);
    }
}
```

## Components

- Every widget is its own class: `Text`, `Button`, `Field`, … in `unweird-core`.
- A composite component extends `Component` and declares child widgets as fields.
- **A component owns its own behavior.** Handlers are wired inside the component.
  Getters exist so others can read or style the parts, not rewire them.
- `onClick(...)` etc. return `this` so a widget can be declared in one expression.

## Widgets

| Widget | Example | Notes |
|---|---|---|
| `Text` | `new Text("Hi")`, `new Text(() -> "Hi " + name)` | Role inferred (heading / title / subtitle / body) |
| `Button` | `new Button("Save").onClick(...)`, `new Button(() -> label)` | Variant inferred; `primary/secondary/ghost/danger()` |
| `Field` | `new Field("Username").masked().onSubmit(...)` | Single-line input, see "Text input" |
| `Checkbox` | `new Checkbox("Remember me").onChange(on -> ...)` | `checked()`, Space toggles |
| `Toggle` | `new Toggle("Dark mode").checked(true)` | Label left, switch right |
| `Dropdown<T>` | `new Dropdown<>("Language", List.of(...)).labels(...)` | Popup above everything; keyboard-driven too |
| `ListView<T>` | `new ListView<>(() -> tasks).onSelect(...)` | Live items; arrows select; `empty("...")` text |
| `Image` | `new Image("/images/logo.png").size(56, 56)` | Classpath, `Image.file(path)` or `Image.url(...)` (background download); placeholder if missing |
| `Component` | `class Sidebar extends Component { ... }` | Your own groups of widgets; `@Card` draws one on a padded surface |
| `Each<T>` | `new Each<>(() -> results, ResultRow::new)` | One widget per item of a live list; rows keep their state while their item stays |

Every widget can be shown or enabled conditionally, re-evaluated after every event:
`.visibleIf(() -> ...)` (hidden widgets take no space) and `.enabledIf(() -> ...)` (dimmed, no input, skipped by Tab).

### Ready-made components (`unweird-components`)

| Component | Example |
|---|---|
| `LoginForm` | `new LoginForm().onSignIn(c -> ...).onForgotPassword(...)`; `fail("Wrong password")`, `reset()` |

All widgets support the relational overrides (`below`, `rightOf`, `alignRight`, `gap`, ...) and return their own type,
so they chain at the declaration.

Pages taller than the window scroll (mouse wheel, thin scrollbar); Tab scrolls the focused widget into view.

## Screens

- One class per screen, extending `Screen`.
- **Component fields are added automatically, in declaration order.**
  - Superclass fields come first.
  - `@Skip` excludes a field.
  - Calling `add(...)` yourself switches auto-adding off for that class, and only what you pass is shown, in that order.
- Metadata goes in annotations: `@Title("...")`, `@Without(...)`, `@Fresh`. All three are inherited by subclasses.
- Navigation: `go(HomeScreen.class)` is available in both `Screen` and `Component`. You never need an app reference.
  From other code, `Unweird.go(...)` does the same. Calls from other threads are handed to the UI thread.
- **Lifecycle:** each screen class is created once and cached, so typed input survives navigating away and back.
  A screen annotated `@Fresh` is rebuilt on every `go(...)`.
- Hooks: `onShow()` runs on every visit (also the first), `onHide()` when another screen replaces it.
  Example: the login screen clears its password field in `onShow()`.
- **Sharing data between screens:** keep it on a `@Default` component and reach it with `shared(...)`,
  e.g. `shared(Header.class).setUser(name)` on login, and the header shows it.

### Field order: why an annotation processor

Java reflection does not promise that fields come back in source order.
`unweird-processor` records the exact source order at compile time into
`META-INF/unweird/fields.idx`, and the runtime reads it from there.
If the index is missing, startup fails with a message that names the
`annotationProcessor` line to add. It never silently falls back to a wrong order.

## Defaults (Sidebar, Header, …)

- `@Default(Side.LEFT)` on a component class makes it appear on **every** screen,
  docked on the given side. Main needs no registration: the processor writes all
  `@Default` classes into `META-INF/unweird/defaults.idx`.
- The component decides where it docks: `Side.LEFT | RIGHT | TOP | BOTTOM`.
- A screen opts out with `@Without(Sidebar.class)` (accepts several).
- Defaults are **single shared instances**. Access one from anywhere with `shared(Sidebar.class)`, so you never pass it around.
- When several defaults dock on the same side, they stack in alphabetical order of class name for now.
  An explicit `order` attribute can come later.
- `@Default` on something that isn't a non-abstract `Component` is a **compile error**.
  `@Without(X.class)` where `X` isn't a default fails when the screen is shown.

### How docks are laid out

- Left/right docks take the full window height (200–300px wide); top/bottom docks span the width that's left.
  Each dock is drawn as a surface-colored panel with a hairline towards the content.
- **Side docks** stack vertically: the first `Text` becomes a title, buttons become plain full-width
  navigation items (label on the left, highlight on hover).
- **Top/bottom docks** are a row: texts on the left (the first one a title), buttons on the right.
- Tab order: top docks, left docks, the screen's content, right docks, bottom docks.
- The screen's content is laid out in the area left over, exactly as without docks.

## State & reactivity

- Widgets that show data take either a value or a lambda:
  `new Text("Hi")` or `new Text(() -> "Hi " + user.value())`.
- **After every event the runtime re-runs all lambdas on the visible screen and its defaults.**
  Events are clicks, keystrokes, focus changes, and navigation.
  If any result changed, the affected widgets are laid out again and redrawn.
  So a plain field (`clicks++`) or an input (`user.value()`) just works, with no `bind()` and no build-time bytecode magic.
- `@State` marks fields that matter for the UI. It **changes nothing at runtime** and exists only for readability.
- A lambda can only use fields declared **above** it (Java's forward-reference rule); write `this.field` to reach one declared later.
- Changes made from another thread (a network callback, a timer) need `refresh()`.
  It is thread-safe: it wakes the UI thread, which then re-evaluates the lambdas.

## Layout: inferred, with readable overrides

Default rule: **you shouldn't need to write layout code for a normal screen.**
The engine looks at the content (declaration order plus widget types) and picks a layout.

### Inference rules

1. **Regions:** docked defaults take their edges, and the rest is the content area.
2. **Card or page:** content that is *form-like* (only texts, fields and buttons, also inside components)
   and fits is centered on a **card** (~360px wide; texts centered; single buttons full width).
   Anything else, or content too tall for the window, becomes a **page**: a top-aligned column up to 640px wide,
   texts left-aligned, buttons at their natural width.
3. **Flow:** content flows top to bottom in declaration order, as rows.
4. **Heading:** the first `Text` of a screen is a heading; inside a component it's a section title
   (unless that component opens the screen, then it carries the heading). A `Text` right after a heading
   or title is a muted subtitle; other texts are body text.
5. **Groups:** consecutive `Button`s share a row. On a card they split the width evenly; on a page they keep their
   natural width. The first button in a row is primary, the others secondary.
   Spacing is tighter inside groups (heading → subtitle 6px, field → field 10px) and larger between them.
6. **Empty texts take no space**, so a status line appears only when it has something to say.
7. **Components** in the content use these same rules, so a form looks the same whether its widgets sit
   directly on the screen or inside a `Component`.

### Overrides (relational, type-checked)

Use these only for what the inference got wrong. They return the widget itself, so they chain right
where it's declared:

```java
Button forgot = new Button("Forgot password?").ghost()
        .below(pass).alignRight(pass).gap(4);
```

| Call | Effect |
|---|---|
| `below(x)` / `above(x)` | Moved directly below/above `x`, wherever it was declared. |
| `rightOf(x)` / `leftOf(x)` | Joins `x`'s row, next to it. |
| `alignLeft(x)` / `alignRight(x)` | Natural width, edge lined up with `x`'s. |
| `gap(px)` | Space before it, instead of the inferred spacing. |

A relation to a widget that's not in the same screen or component is an error when the screen is laid out.
A relation to a text that's currently empty (hidden) is ignored until that text has content.

## Styling

- One `Theme` object holds colors and corner radius. Built-in `Theme.light()` and `Theme.dark()`;
  tweak one with Lombok withers, `Theme.dark().withAccent(Color.hex("#7c5cff"))`, or build your own with the constructor.
  Switch at runtime with `Unweird.theme(...)`; `Unweird.theme()` returns the current one.
- Per widget, explicit look beats inference. Short calls instead of style lambdas:
  - `Button`: `primary()`, `secondary()`, `ghost()`, `danger()`
  - `Text`: `heading()`, `title()`, `body()`, `muted()`, `color(Color)`
- Buttons can have live labels, too: `new Button(() -> dark ? "Light mode" : "Dark mode")`.
- No CSS files.

## Runtime & threading

- `Unweird.start(...)` takes over the calling thread as the UI thread.
  It must be the main thread, which GLFW requires on macOS.
- All handlers run on the UI thread.
- Rendering is event-driven: the loop waits on `glfwWaitEvents` and only redraws when something changed.
  Continuous rendering comes with `unweird-anim`.
- Focus: Tab and Shift+Tab move through focusable widgets (fields, buttons) in declaration order,
  including those inside components. Keyboard focus shows a focus ring; tabbing into a field selects its text.
  The first `Field` on a screen is focused automatically. Clicking empty space removes focus.

## Text input (`Field`)

- `new Field("Username")`: the text is a placeholder shown while the field is empty.
- `value()` reads, `value("...")` sets. `masked()` shows dots and disables copy/cut.
- `onChange(value -> ...)` after every edit, `onSubmit(() -> ...)` on Enter.
- Keys: arrows, Home/End, Backspace/Delete. Ctrl+arrow or Ctrl+Backspace/Delete work on whole words
  (Alt on macOS). Shift extends the selection. Ctrl+A/C/X/V (Cmd on macOS).
- Mouse: click places the caret, drag or Shift+click selects, double click selects a word, triple click selects everything.
- Editing works on code points, so an emoji is never split in half.
- Buttons react to Enter and Space when focused.

## Roadmap

| Milestone | Goal |
|---|---|
| **M0** ✅ | Multi-module Gradle build, annotations, this document |
| **M1** ✅ | Window + NanoVG + bundled font (Noto Sans); `Text` and `Button` drawn; annotation processor + auto-added fields; simple flow layout (centered card, heading/subtitle inference, button rows); lambdas re-evaluated on every redraw; hover/press/click |
| **M2** ✅ | `Field`: typing, cursor, selection, clipboard, masking, scrolling, blinking caret; focus & Tab; keyboard-activated buttons; empty texts collapse |
| **M3** ✅ | `go(...)`, screen caching, `@Fresh`, `onShow`/`onHide`; `@Default` docking, `@Without`, `shared(...)`; compile-time `@Default` checks |
| **M4** ✅ | Card/page inference, components laid out like screens, button-row variants; relational overrides; theme switching, explicit variants and text styles |
| **M5** 🚧 | ✅ scrolling pages, checkbox, toggle, dropdown (overlay layer), list, image · ⏳ first `unweird-components` (`LoginForm`) |
| **v2** | `unweird-anim`: hover/press transitions, screen transitions; Windows support |

## Open points

- `Field` label: placeholder for now; a floating label could come with `unweird-anim`.
- Undo/redo in fields (Ctrl+Z): not yet.
- Default ordering when several components dock on the same side.
