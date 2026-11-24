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
| `unweird-3d` | 3D views: scene graph, meshes, textures, cameras, picking |
| `unweird-game` | Games: 2D (sprites, levels) and 3D (block worlds, terrain, explorer), sound |
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
| `Slider` | `new Slider("Volume", 0, 100).step(5).onChange(v -> ...)` | Drag, arrows, Page Up/Down, wheel while focused |
| `Image` | `new Image("/images/logo.png").size(56, 56)` | Classpath, `Image.file(path)` or `Image.url(...)` (background download); placeholder if missing |
| `Component` | `class Sidebar extends Component { ... }` | Your own groups of widgets; `@Card` draws one on a padded surface |
| `Each<T>` | `new Each<>(() -> results, ResultRow::new)` | One widget per item of a live list; rows keep their state while their item stays |

Text basics:
- `new Text(...).maxLines(2)` cuts long text with "…"; wrapping is unweird's own, so measuring and drawing always agree.
- `.tooltip("...")` on any widget: shown after resting the mouse for a moment, hidden on click, key or scroll.
- Fields have undo/redo: Ctrl+Z, Ctrl+Shift+Z or Ctrl+Y. A run of typing (a word and its space) is one step.
- Characters Noto Sans lacks fall back to Noto Sans Symbols 2, Noto Sans Math (arrows, math) and Noto Emoji
  (monochrome). For CJK or other scripts add one: `Unweird.app(...).fallbackFont("/fonts/NotoSansJP-Regular.ttf")`.

Every widget can be shown or enabled conditionally, re-evaluated after every event:
`.visibleIf(() -> ...)` (hidden widgets take no space) and `.enabledIf(() -> ...)` (dimmed, no input, skipped by Tab).

### Writing your own widgets

Extend `Widget`, implement `measure` and `draw` (with `Canvas`), and override the input hooks you need:

| Hook | When |
|---|---|
| `focusable()`, `cursor()` | Can it take focus (Tab, click)? Which mouse cursor? |
| `onPress(Pointer)` / `onDrag(Pointer)` / `onRelease(Pointer)` | Any mouse button; drags keep coming outside the widget until release; `dx`/`dy` per drag |
| `onMove(Pointer)` | Hover movement; return true if it changes the drawing |
| `onScroll(Pointer, dx, dy)` | Return true to use it, otherwise the page scrolls |
| `onKey(KeyPress)` / `onType(String)` | While focused; `key.is("Ctrl+S")`, letters follow the keyboard layout |

| `onDrop(Pointer, List<Path>)` | Files dropped from the file manager; return true to take them, otherwise the parent gets them, up to the screen |

Handlers just change state; the UI re-evaluates and redraws after every event. `Slider` is built only on these hooks.

### Ready-made components (`unweird-components`)

| Component | Example |
|---|---|
| `LoginForm` | `new LoginForm().onSignIn(c -> ...).onForgotPassword(...)`; `fail("Wrong password")`, `reset()` |

All widgets support the relational overrides (`below`, `rightOf`, `alignRight`, `gap`, ...) and return their own type,
so they chain at the declaration.

Pages taller than the window scroll (mouse wheel, thin scrollbar); Tab scrolls the focused widget into view.

## Installing content (`unweird-install`)

A general module for apps that browse a catalog and install things into a folder: mods, resource packs,
shaders, modpacks, plugins, assets. It doesn't know any particular catalog; you implement one small interface:

```java
public interface Source {
    List<Kind> kinds();                                   // "Mods", "Resource packs", ...
    Results search(String query, Kind kind, int offset);  // background thread, may block
    Release release(Item item);                           // what installing it means right now
}
```

A `Release` lists verified downloads (url, relative path, SHA-1, size), required items, archives to unpack
(e.g. a modpack's `overrides/`) and info to keep (e.g. the Minecraft version a pack needs).

```java
Installer installer = new Installer(source, gameDirectory);
ContentBrowser browser = new ContentBrowser(installer);   // a ready-made UI: search, kinds, cards, Install/Remove
```

**Installed state can't go stale.** `installer.status(item)` is computed every time from a running job,
the last failure, and the manifest (`.unweird-installed.json`) checked against the files on disk. The UI only
ever shows `() -> installer.status(item)...`, re-evaluated after every event:
- deleting a file by hand → the item shows "Install" again (also after a restart)
- an item installed only as a dependency shows as installed in any search
- a failed install leaves nothing behind (downloads go to `.part` files, are hash-checked, and are only
  recorded once everything succeeded); the button says "Retry" with the reason
- Remove deletes the item's files and dependencies nothing else needs; updating removes the old version's files
- paths that would escape the folder (`../`, zip slip) and writes over the manifest are refused

The example app's `ModrinthSource` (≈150 lines) plugs Modrinth in: mods, resource packs, shaders, data packs
and `.mrpack` modpacks (server-only files skipped, overrides unpacked). Icons in WebP work through the
TwelveMonkeys ImageIO plugin that `unweird-install` brings along; core falls back to ImageIO for any image
format NanoVG can't read.

## 3D (`unweird-3d`)

A 3D view is just another widget in the layout; it clips, scrolls and has rounded corners like everything else.

```java
Scene scene = new Scene().add(new Node(Mesh.box(1, 1, 1), Material.color(Color.hex("#5fae3e"))));
View3D view = new View3D(scene)
        .camera(Camera.orbit(0, 0, 0, 5).angles(30, 20))
        .height(360)
        .autoRotate(20)                         // degrees per second, pauses while dragging
        .onFrame(t -> ...)                      // animate; keeps frames coming while set
        .onClick((hit, button) -> ...);         // click without dragging: what's under the mouse (or null)
```

- **Scene graph:** `Node`s with mesh, material, position/rotation/scale and children (rotate a limb around its
  shoulder by putting the shoulder in a parent). `Scene` adds a sun direction, ambient light and an optional background.
- **Meshes:** `Mesh.box(...)`, `Mesh.plane(...)`, a builder with quads/triangles/vertex colors, `blockFace(...)` for
  voxel worlds, and `BoxUv` for the common "box UV" texture layout (Blockbench, Minecraft skins).
- **Materials:** color tint, texture (`Texture.resource/file/url`, `.nearest()` for pixel art, swappable live),
  lit or unlit, `cutout()` (sharp holes), `transparent()` (blended, sorted back to front), `doubleSided()`.
- **Camera:** orbit camera; left-drag orbits, right/middle-drag pans, wheel zooms, arrows and +/- when focused.
- **Picking:** `scene.pick(ray)` / `view.pick(x, y)` → `Hit` with node, point, normal, distance;
  `hit.block()` and `hit.blockInFront()` for block worlds.
- **Under the hood:** OpenGL 3.3, rendered into a 4× multisampled offscreen surface (`Canvas.gl(...)` in core, usable
  for any custom GL drawing) and composited by NanoVG. GPU copies of meshes and textures are made on first use and
  freed when unused for ~300 frames, so rebuilding meshes after edits is fine. Animations use `requestFrame()`
  (core): the window renders continuously only while something asks for it.

The example app has a skin viewer (`SkinModel` ~80 lines, `SkinLoader` ~150: load by Minecraft username via Mojang's API
with slim-arm detection, by URL, by file path or by dropping a PNG; legacy 64×32 skins converted; spin toggle and turn slider)
and a block-world editor (`VoxelWorld`: place with click, remove with right-click).

## Games (`unweird-game`)

Goal: making a game is as short as making a screen, for 2D and 3D. User docs: `docs/games.md`.

- **A game is a `Screen`** (`Game`, `Game3D`): `Unweird.start(...)`, `go(...)`, widget fields as a HUD
  (`Screen.overlay()` lays them along the top). `GameView` puts one inside another screen; the frame logic draws into
  any area and the mouse capture belongs to whoever shows it (`Game3D.Host`).
- **Behavior from the base class, not from code.** `Player`, `Wall`, `Platform`, `Pickup`, `Hazard`, `Goal`,
  `Enemy`, `Shot`; `Block` (+ `@SeeThrough`, `@Liquid`, `@Plant`, `@Unbreakable`), `Explorer`, `Creature`, `Thing`.
  Tuning is annotations (`@Speed`, `@Jump`, `@Shoots`, `@TopDown`, `@Resolution`, `@Sky`, `@ViewDistance`, `@Seed`,
  `@Saved`) or short calls on parts (`new Hills().height(50)`, `new Trees(log, leaves).dense()`).
- **Hooks by name** (`tick`, `touch(Coin c)`, `picked`, `hurt`, `clicked`, `started`, `won`, …) are found by
  reflection (`Hooks`), so beginners write `void picked()` without `protected`/`@Override` (an override of a
  protected method with package access wouldn't compile). The most specific `touch(T)` wins; exceptions are printed
  and the game goes on.
- **No strings**: pictures, models and sounds are found by class or field name (`CoinBox` → `coin_box.png`,
  `Sound explosion` → `explosion.ogg`). Missing art gets placeholders that fit the kind and words in the name;
  missing sounds fall back to synthesized effects. A game runs with zero assets.
- **Things are inner classes**, so they see the game's fields (`coins++`). Copies (`from(..).to(..)`) are clones;
  levels are recreated through their inner-class constructor on every restart.
- **2D**: positions in tiles on a fixed world grid, y up. `World` (no GL, unit-tested) steps physics at ≤ 1/120 s:
  axis-separated AABB movement against a cell index of walls, one-way platforms, touch events once per contact.
  Drawn by `Batch` (GL 3.3 sprite batch, nearest filtering, whole-number scaling of the `@Resolution`).
- **3D**: 16 × 128 × 16 chunks generated and meshed on a small thread pool around the explorer, dropped far away;
  edits are kept apart so regenerated chunks keep them (and `@Saved` writes them). `Terrain` = ordered `Part`s with
  `height` / `block` / `decorate` steps and seeded Perlin noise; decoration covers a margin around the chunk so trees
  cross chunk borders. `Mesher`: face culling, per-vertex ambient occlusion, separate solid / cutout / liquid
  meshes, one texture atlas. Rendered by `unweird-3d` (`Renderer` is public for this; first-person `Camera`, fog,
  frustum culling); HUD with the same `Batch`.
- **Core support** it needed: `Keyboard.held/pressed`, `Widget.lockPointer()`, `@Without` for all docks,
  `Screen.title()` and `Screen.overlay()`.

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
- Hooks: `onShow()` runs on every visit (also the first), `onHide()` when another screen replaces it or the window closes.
  `whileShown(onShown, onHidden)` does the same for screens *and* components (also docked ones and `Each` rows):
  for subscribing to listeners outside the UI.
- `Unweird.open(link or path)` opens things in the default app (AWT `Desktop` isn't available: AWT runs headless).
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
6. **Rows that don't fit wrap** onto the next line (fields keep at least 160px) instead of squeezing widgets.
7. **Empty texts take no space**, so a status line appears only when it has something to say.
8. **Components** in the content use these same rules, so a form looks the same whether its widgets sit
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

## Dialogs

```java
show(Dialog.confirm("Delete “My world”?", "This can't be undone.")
        .action("Delete", this::delete).danger()
        .cancel("Keep it"));
```

- Modal: a dimmed backdrop blocks everything below; Tab stays inside; Escape cancels.
- Actions close the dialog after running, unless the handler calls `stay()` (e.g. invalid input).
- The first action is the default: Enter runs it unless the focused widget uses Enter itself.
  Focus starts on the first input, else on Cancel, so Enter never confirms something destructive by accident.
- Forms: subclass `Dialog` and declare fields like on a screen; they go between the message and the buttons.
- Popups (a dropdown inside a dialog) open on top of it; overlays are a stack.

## Focus and shortcuts

```java
@Shortcut("Ctrl+N") void newTask() { task.focus(); }   // on any screen or component method
{ shortcut("Ctrl+F", this::find); }                     // dynamic; same keys again replace it
Unweird.shortcut("Ctrl+Q", Unweird::quit);             // app-wide
```

- A key the focused widget uses itself (Ctrl+A in a field, Delete while typing) never triggers a shortcut.
- Otherwise the nearest wins: the component containing the focused widget, then outwards, the screen,
  its docks, then app-wide shortcuts. An open dialog blocks all of them.
- `widget.focus()` moves focus there from code (any thread), if it can take focus and is on screen.
- `Unweird.quit()` closes the window as if the user did.

## Background work, timers and toasts

```java
async(() -> api.loadWorlds())                 // runs on a virtual thread
        .then(worlds -> this.worlds = worlds)   // UI thread, then the UI refreshes
        .failed(error -> status = error.getMessage());
toast("Saved");                               // corner message, gone after a few seconds (click to dismiss)
toast(Toast.error("Couldn't connect"));       // success / info / error
Unweird.after(Duration.ofSeconds(2), () -> ...);  // timer on the UI thread
Unweird.later(() -> ...);                     // UI thread, after the current event
```

Without a `failed` handler, an error becomes an error toast (and is printed), it's never swallowed.
The event loop sleeps until the next input *or* the next timer, so timers cost nothing while idle.

## Runtime & threading

```java
Unweird.app("My Launcher")
        .size(1100, 720).minSize(800, 520)   // logical pixels; the window manager enforces the minimum
        .icon("/images/logo.png")            // not on Wayland/macOS, which don't have window icons
        .remember()                          // position, size, maximized: <config dir>/<app>/window.properties
        .start(LoginScreen.class);
```
The window title is "<screen @Title> — <app name>". A remembered position is only used if it's on a connected monitor.
`Unweird.start(Screen.class)` alone still works, with defaults.

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
| **M5** ✅ | Scrolling pages, checkbox, toggle, dropdown (overlay layer), list, image, `Each`, `@Card`, `visibleIf`/`enabledIf`; `unweird-components` with `LoginForm` |
| **M6** ✅ | `unweird-install`: generic `Source` → `Installer` (verified downloads, dependencies, archives, honest installed state) + `ContentBrowser`; Modrinth source in the example |
| **v2** | `unweird-anim`: hover/press transitions, screen transitions; Windows support |
| **M7** ✅ | Foundations: custom-widget input API (`Slider` built on it), window options (`Unweird.app(...)`), dialogs, toasts, `async`, timers, `@Shortcut`/`focus()`, tooltips, `maxLines`, field undo, fallback fonts |
| **3D** ✅ | `unweird-3d` (branch `3d-rendering`): `View3D` in normal layouts, scene graph, meshes/box UV, textures, materials, orbit camera, picking; `Canvas.gl` + `requestFrame` in core; skin viewer and world editor demos |
| **Games** ✅ | `unweird-game` (branch `game-module`): 2D games (kinds, levels, placeholders, sounds, `@TopDown`), 3D block worlds (terrain parts, chunks, explorer, creatures, `@Saved`), `GameView`; demos Coin Jump and Wilds |
| next | Games: day/night, health and inventories, falling and flowing blocks, moving platforms, gamepads |

## Open points

- `Field` label: placeholder for now; a floating label could come with `unweird-anim`.
- Color emoji (NanoVG renders monochrome glyphs only).
- `unweird-install`: update checks, "adopt" files that were already in the folder (via the source looking up hashes),
  and an "Installed" tab in `ContentBrowser`.
- Default ordering when several components dock on the same side.
