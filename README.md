> [!IMPORTANT]
> **The development of unweird was heavily assisted by Claude (Anthropic's AI).**
> Claude wrote most of the code. Decisions like what the library should do how it should work and how its API
> should look like, were made by me though, since the API is shaped around how I want frontend development to be (as a backend dev lol).
>
> Why even do that stuff? No existing Java UI library fit my use case:
> **Swing (+ FlatLaf)** is too old, **JavaFX** is too bloated, and **Compose Desktop** isn't available for Java
> (it needs Kotlin).

# unweird

A Java UI library with its own renderer and an API that stays out of your way. Screens and components are plain
classes, widget fields are laid out for you, and state is just fields.

```java
@Title("Sign in")
public class LoginScreen extends Screen {
    Text heading  = new Text("Welcome back");
    Field user    = new Field("Username");
    Field pass    = new Field("Password").masked();
    Button signIn = new Button("Sign in").onClick(() -> go(HomeScreen.class));
}
```

No layout code, no string IDs, no listeners to wire up: the heading, fields and button above end up centered on a
card, in the order you declared them.

## Features

- **Inferred layout** — short forms become centered cards, everything else a page; tweak with
  `below(...)`, `rightOf(...)`, `alignRight(...)`, `gap(...)`
- **Live values** — `new Text(() -> "Hi " + user.value())` updates after every event, no binding
- **Widgets** — text, buttons, fields (selection, clipboard, undo), checkboxes, toggles, sliders, dropdowns,
  lists, images (also from URLs), tooltips
- **Navigation** — `go(HomeScreen.class)`, cached or `@Fresh` screens, `@Default` sidebars/headers on every screen
- **App stuff** — dialogs, toasts, `async(...)` background work, `@Shortcut("Ctrl+S")`, themes (light/dark/custom),
  remembered window position
- **Installing content** (`unweird-install`) — search a catalog, install with verified downloads and dependencies,
  and an installed state that never lies
- **3D** (`unweird-3d`) — 3D views inside normal layouts: scene graph, textures, orbit camera, picking
- **Custom widgets** — public hooks for mouse, keyboard, scroll and file drops; raw OpenGL via `Canvas.gl(...)`

## Requirements

- Java 21 or newer
- OpenGL 3.3
- Linux (X11 or Wayland). Windows and macOS are planned.

## Getting started

unweird isn't published to a Maven repository yet. Use it from source with a Gradle composite build:

```bash
git clone <this repository> unweird
```

In your project's `settings.gradle.kts`:

```kotlin
includeBuild("../unweird")
```

In your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.dervarex:unweird-core:0.1.0-SNAPSHOT")
    // the annotation processor records the order of your widget fields
    annotationProcessor("com.dervarex:unweird-processor:0.1.0-SNAPSHOT")

    // optional
    implementation("com.dervarex:unweird-components:0.1.0-SNAPSHOT")
    implementation("com.dervarex:unweird-install:0.1.0-SNAPSHOT")
    implementation("com.dervarex:unweird-3d:0.1.0-SNAPSHOT")
}
```

Then start your first screen:

```java
public class Main {
    public static void main(String[] args) {
        Unweird.app("My App")
                .size(1000, 680)
                .remember()            // window position and size between runs
                .start(LoginScreen.class);
    }
}
```

`Unweird.start(...)` must be called from the main thread; it takes it over as the UI thread.

## Examples

### State and live text

```java
public class CounterScreen extends Screen {
    @State int clicks = 0;

    Text counter = new Text(() -> "Clicked " + clicks + " times");
    Button plus  = new Button("+1").onClick(() -> clicks++);
}
```

### Navigation and a sidebar on every screen

```java
@Default(Side.LEFT)
public class Sidebar extends Component {
    Button home     = new Button("Home").onClick(() -> go(HomeScreen.class));
    Button settings = new Button("Settings").onClick(() -> go(SettingsScreen.class));
}

@Without(Sidebar.class)        // except here
public class LoginScreen extends Screen { ... }
```

### Dialogs, toasts and background work

```java
show(Dialog.confirm("Delete world?", "This can't be undone.")
        .action("Delete", this::delete).danger()
        .cancel("Keep it"));

async(() -> api.loadWorlds())
        .then(worlds -> this.worlds = worlds)
        .failed(error -> toast(Toast.error(error.getMessage())));
```

### Shortcuts

```java
@Shortcut("Ctrl+N")
void newTask() {
    taskField.focus();
}
```

### 3D

```java
View3D view = new View3D(new Scene().add(new Node(Mesh.box(1, 1, 1), Material.color(Color.hex("#5fae3e")))))
        .camera(Camera.orbit(0, 0, 0, 5))
        .autoRotate(20)
        .onClick((hit, button) -> ...);
```

More in [DESIGN.md](DESIGN.md), which describes how everything works.

## Running the demo

```bash
./gradlew :example:run
```

Sign in with anything. The demo has a task list, a Modrinth browser that installs mods into `example/run/`,
a 3D skin viewer (load your own skin by username) and a small block-world editor.

## Building and testing

```bash
./gradlew build
```

## Modules

| Module | What it is |
|---|---|
| `unweird-core` | Window, renderer, layout, widgets, dialogs, toasts, shortcuts |
| `unweird-processor` | Annotation processor (widget field order, `@Default` components) |
| `unweird-components` | Ready-made components, e.g. `LoginForm` |
| `unweird-install` | Browse catalogs and install content from any source |
| `unweird-3d` | 3D views: scene graph, meshes, textures, camera, picking |
| `example` | The demo app |

## Troubleshooting

- **"no field order recorded for ..."** — add the annotation processor (see *Getting started*), or list the widgets
  yourself with `add(...)`.
- **The window doesn't open on NixOS** — GLFW needs the X11/Wayland and OpenGL libraries on the library path,
  e.g. via `nix-ld` or a dev shell that provides them.

## Credits

Text is set in Noto Sans, with Noto Sans Symbols 2, Noto Sans Math and Noto Emoji as fallbacks
(all SIL Open Font License). Rendering by [LWJGL](https://www.lwjgl.org/) and NanoVG.
