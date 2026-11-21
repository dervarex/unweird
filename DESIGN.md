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

## Screens

- One class per screen, extending `Screen`.
- **Component fields are added automatically, in declaration order.**
  - Superclass fields come first.
  - `@Skip` excludes a field.
  - Calling `add(...)` yourself switches auto-adding off for that class, and only what you pass is shown, in that order.
- Metadata goes in annotations: `@Title("...")`, `@Without(...)`, `@Fresh`.
- Navigation: `go(HomeScreen.class)` is available in both `Screen` and `Component`. You never need an app reference.
- **Lifecycle:** each screen class is created once and cached, so typed input survives navigating away and back.
  A screen annotated `@Fresh` is rebuilt on every `go(...)`.

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

### Inference rules (v1 draft)

1. **Regions:** docked defaults take their edges, and the rest is the content area.
2. **Flow:** content flows top to bottom in declaration order.
3. **Heading:** if the first widget is a `Text`, it is styled as a heading.
   A `Text` directly after the heading becomes a muted subtitle.
4. **Groups:** consecutive widgets of the same kind are grouped, with tighter spacing
   inside a group and larger spacing between groups.
   Consecutive `Button`s form a horizontal button row.
5. **Short-form detection:** if the content is only Text/Field/Button and fits
   the content area, it is centered as a card with a readable max width (~360px).
   That is the classic login look, with zero code.
6. **Otherwise** content is top-aligned with a comfortable max width and side padding.

### Overrides (relational, type-checked)

Use these only for what the inference got wrong:

```java
signIn.alignRight(pass);
subtitle.below(heading).gap(4);
counter.rightOf(plus);
heading.centerIn(this);
```

Any widget with an override is pinned by it, and everything else keeps flowing.
Explicit styling like `new Text("x").heading()` also overrides inference.

## Styling

- One `Theme` object holds colors, fonts, spacing, corner radius, and an accent color.
- Built-in `Theme.light()` and `Theme.dark()`. Write your own by extending `Theme` or tweaking a built-in:
  `Unweird.theme(Theme.dark().withAccent(Color.hex("#7c5cff")))`.
- Per-widget tweaks in Java: `logout.style(s -> s.variant(DANGER))`.
- No CSS files.

## Runtime & threading

- `Unweird.start(...)` takes over the calling thread as the UI thread.
  It must be the main thread, which GLFW requires on macOS.
- All handlers run on the UI thread.
- Rendering is event-driven: the loop waits on `glfwWaitEvents` and only redraws when something changed.
  Continuous rendering comes with `unweird-anim`.
- Focus: Tab and Shift+Tab move through focusable widgets in declaration order.

## Roadmap

| Milestone | Goal |
|---|---|
| **M0** ✅ | Multi-module Gradle build, annotations, this document |
| **M1** ✅ | Window + NanoVG + bundled font (Noto Sans); `Text` and `Button` drawn; annotation processor + auto-added fields; simple flow layout (centered card, heading/subtitle inference, button rows); lambdas re-evaluated on every redraw; hover/press/click |
| **M2** | `Field`: typing, cursor, selection, clipboard, masking; focus & Tab; lambda re-evaluation after events |
| **M3** | `go(...)`, screen caching, `@Fresh`, `@Title`; `@Default` docking, `@Without`, `shared(...)` |
| **M4** | Full inference rules + relational overrides; `Theme` light/dark/custom, `style(...)` |
| **M5** | More widgets: checkbox, toggle, dropdown, list, image, scroll container; first `unweird-components` |
| **v2** | `unweird-anim`: hover/press transitions, screen transitions; Windows support |

## Open points

- `Field` label style: floating label vs. placeholder vs. label above.
- Default ordering when several components dock on the same side.
