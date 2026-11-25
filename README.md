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
- **Android** (`unweird-android`) — the same screens on phones: `class Main extends UnweirdActivity<LoginScreen> {}`,
  touch scrolling, the on-screen keyboard and the back gesture handled for you
- **Custom widgets** — public hooks for mouse, keyboard, scroll and file drops; raw OpenGL via `Canvas.gl(...)`

## Requirements

- Java 21 or newer
- OpenGL 3.3
- Linux (X11 or Wayland). Windows and macOS are planned.

## Getting started

unweird is on [JitPack](https://jitpack.io/#dervarex/unweird). Add the JitPack repository and the dependencies:

**Kotlin DSL** (`build.gradle.kts`):

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.dervarex.unweird:unweird-core:v0.1.2")
    // the annotation processor records the order of your widget fields
    annotationProcessor("com.github.dervarex.unweird:unweird-processor:v0.1.2")

    // optional
    implementation("com.github.dervarex.unweird:unweird-components:v0.1.2")
    implementation("com.github.dervarex.unweird:unweird-install:v0.1.2")
    implementation("com.github.dervarex.unweird:unweird-3d:v0.1.2")
}
```

**Groovy DSL** (`build.gradle`):

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.dervarex.unweird:unweird-core:v0.1.2'
    // the annotation processor records the order of your widget fields
    annotationProcessor 'com.github.dervarex.unweird:unweird-processor:v0.1.2'

    // optional
    implementation 'com.github.dervarex.unweird:unweird-components:v0.1.2'
    implementation 'com.github.dervarex.unweird:unweird-install:v0.1.2'
    implementation 'com.github.dervarex.unweird:unweird-3d:v0.1.2'
}
```

Working on unweird itself at the same time? Clone it next to your project and add `includeBuild("../unweird")`
to your settings file; Gradle then uses your local copy.

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

More in the [documentation](docs/README.md). Design notes and the roadmap are in [DESIGN.md](DESIGN.md).

## Running the demo

```bash
./gradlew :example:run
```

Sign in with anything. The demo has a task list, a Modrinth browser that installs mods into `example/run/`,
a 3D skin viewer (load your own skin by username) and a small block-world editor.

The Android demo needs an Android SDK; `nix develop .#android` sets one up (see [Android](docs/android.md)):

```bash
./gradlew :example-android:installDebug
```

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
| `unweird-android` | Android backend: run the same screens on phones |
| `example` | The demo app |
| `example-android` | A small Android demo |

## Troubleshooting

- **"no field order recorded for ..."** — add the annotation processor (see *Getting started*), or list the widgets
  yourself with `add(...)`.
- **"couldn't load OpenGL" on NixOS** — the JDK comes from the Nix store, so it doesn't see the system's `libGL`.
  Use a regular JDK (e.g. one Gradle or IntelliJ downloads; it runs through `nix-ld`), or start with
  `LD_LIBRARY_PATH=/run/current-system/sw/share/nix-ld/lib` (needs `programs.nix-ld.enable`), or use a dev shell
  that provides libGL. `/run/opengl-driver/lib` alone isn't enough: it lacks `libGL.so.1`.

## Credits

Text is set in Noto Sans, with Noto Sans Symbols 2, Noto Sans Math and Noto Emoji as fallbacks
(all SIL Open Font License). Rendering by [LWJGL](https://www.lwjgl.org/) and NanoVG.

## License

unweird is licensed under the [Apache License 2.0](LICENSE). The bundled fonts keep their own licenses
(SIL Open Font License), included next to them.
