# CLAUDE.md

Java UI library with its own renderer (LWJGL: GLFW + OpenGL 3.3 + NanoVG). Gradle multi-module, Java 21
(`--release 21`; the local JDK may be newer). Versions live in `gradle/libs.versions.toml`.

## Commands

```bash
./gradlew build                 # compile + all tests
./gradlew :unweird-core:test    # one module
./gradlew :example:run          # demo app (needs a display)
```

## Modules

- `unweird-core` — window/loop (`Window`), widgets, layout (`Flow`, `Docking`), dialogs, toasts, shortcuts, `Canvas`
- `unweird-processor` — annotation processor; writes `META-INF/unweird/fields.idx` (widget field order) and
  `defaults.idx` (`@Default` components). Runs on core itself, the other modules and their tests.
- `unweird-components` — ready-made components (`LoginForm`)
- `unweird-install` — generic `Source` → `Installer` → `ContentBrowser`; nothing catalog-specific belongs here
- `unweird-3d` — `View3D`, scene graph, renderer; built on `Canvas.gl` + `requestFrame` from core
- `unweird-android` — Android backend (`UnweirdActivity`, `AndroidWindow`, NanoVG via JNI in `src/main/cpp`);
  only in the build when an Android SDK is found. `nix develop .#android` provides one. `example-android` is its demo
- `intellij-plugin` — IntelliJ plugin (Kotlin, separate Gradle build: `./gradlew -p intellij-plugin test`). Its
  inspections mirror library rules (`ShortcutSyntax` ↔ `KeyPress.Shortcut.parse`): keep them in step
- `unweird-gradle-plugin` — `com.dervarex.unweird` plugin (plain Java, `java-gradle-plugin`); talks to the Android
  plugin by reflection so it doesn't depend on a particular AGP version
- `example` — demo app; Modrinth source, skin viewer, world editor live here (app-specific code, not library)

Docs for users: `docs/`. Design decisions and roadmap: `DESIGN.md`. Keep both current when behavior changes.

## How the core works (things that bite)

- **Rendering is event-driven.** After every event the whole screen re-evaluates its lambdas, lays out and draws.
  The loop sleeps otherwise; `requestFrame()` asks for continuous frames. Input must be polled before every frame
  (see the comment in `Window.run`) or animating screens stop reacting under vsync.
- **Widget fields are auto-added via the processor index.** Any new widget-typed field in a `Container`
  subclass becomes a child unless marked `@Skip` (see `Dialog`'s internal fields).
- **Built-in widgets use package-private input paths** (`keyPressed`, `pointerDown`, `activate(Host)`...);
  the defaults call the public hooks (`onKey`, `onPress`...) for custom widgets. Keep both working.
- **Fluent setters are overridden covariantly** in every widget (`below`, `rightOf`, `gap`, `visibleIf`,
  `enabledIf`, `tooltip`...). A new widget, or a new relation method on `Widget`, needs these overrides.
- **GL state**: code inside `Canvas.gl` may change anything; `GlSurface` restores what NanoVG needs. Library GL code
  goes through `Gl` (`canvas.openGl()`), never LWJGL's `GL33C`, so it runs on Android too.
- Package-private on purpose: the layout, docking, overlays and window internals are not public API.
- **Platforms**: `Window` is platform-neutral; GLFW lives only in `GlfwWindow`/`LwjglVg`. Core code outside those must
  not touch LWJGL classes (GLFW key constants are fine: they're inlined at compile time). Avoid Java APIs Android
  lacks (`List.getFirst`, virtual threads without a fallback, `java.awt`, `java.net.http`). Public methods must not
  overload on AWT types: javac on Android then can't resolve the call at all (`Pixels` exists for that).

## API style (the owner cares about this)

Minimal, type-checked, declarative. Fields over registration, annotations (`@Title`, `@Default`, `@Shortcut`)
over setup code, short calls (`.danger()`, `.muted()`) over style lambdas, no string IDs, no `bind().map()` chains.
Lombok for getters, never hand-written. Error messages say what to do.

## Tests

- Unit tests run without a GPU: layout decisions (`Flow` helpers are package-private for this), input via a
  `FieldTest.FakeHost`, installer against a local `HttpServer`, 3D math and picking.
- Visual checks: run the demo on a headless X server (`Xvfb :99 -screen 0 1280x800x24 +extension GLX`),
  drive it with `xdotool`, screenshot with `ffmpeg -f x11grab`. Xvfb has no vsync and no window manager:
  frame-timing bugs and min-size/icon behavior don't show there.
- Don't use `pkill -f <pattern>` in the same shell command that contains the pattern; it kills the shell.

## Environment

Dev machine is NixOS with dwm. GLFW finds the X11/GL libraries through nix-ld when the JDK is a regular one
(Temurin, downloaded by Gradle/IntelliJ). A JDK from the Nix store can't load `libGL` ("couldn't load OpenGL");
`LD_LIBRARY_PATH=/run/current-system/sw/share/nix-ld/lib` fixes that, `/run/opengl-driver/lib` alone doesn't.

## Git

Commit finished pieces of work on your own with clear messages; push only when asked.
Add a Co-authored-by line unless the owner tells you to exclude it.
You may use Branches, Pull Requests, Issues, Releases and all other Features provided trough GitHub CLI.
Before releasing, always ask the owner and verify the current state.