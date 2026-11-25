# IntelliJ plugin

`intellij-plugin/` is a plugin for IntelliJ IDEA (2026.3 and newer). It's a separate Gradle build because it compiles
against a whole IDE.

```bash
./gradlew -p intellij-plugin buildPlugin   # → intellij-plugin/build/distributions/unweird-intellij-<version>.zip
./gradlew -p intellij-plugin runIde        # a sandbox IDE with the plugin, to try it
```

Install the zip with *Settings → Plugins → ⚙ → Install Plugin from Disk…*.

## New Project wizard

*File → New → Project → unweird* creates a project that runs right away:

| Runs on | You get |
|---|---|
| Desktop | One module: `Main` (`Unweird.app(...).start(...)`) and a `HomeScreen` |
| Android | An `app` module with the activity (`extends UnweirdActivity<HomeScreen>`) and its manifest |
| Desktop + Android | `shared` (the screens), `desktop` and `android`, both using `shared` |

Every module uses the [Gradle plugin](getting-started.md#or-the-gradle-plugin), so the build files stay short.
*3D views* and *Ready-made components* add `unweird-3d` and `unweird-components`. Under *unweird version* you pick a
release, or a local checkout of unweird to build against (an `includeBuild`, handy when you work on unweird itself).
The Gradle wrapper is included, and the project is imported and synced when it opens.

## New unweird class

Right-click a package → *New → unweird Class*: a Screen, Component, Dialog or a Widget that draws itself. Only shown in
projects that use unweird.

## Live templates

Type these inside a class and press Tab:

| Abbreviation | Expands to |
|---|---|
| `uwtext` | `Text heading = new Text("...");` |
| `uwlive` | `Text status = new Text(() -> ...);` |
| `uwbutton` | `Button save = new Button("Save").onClick(() -> ...);` |
| `uwfield` | `Field name = new Field("Name");` |
| `uwtoggle` | `Toggle dark = new Toggle("Dark mode").onChange(on -> ...);` |
| `uwstate` | `@State int count;` |
| `uwshortcut` | an `@Shortcut("Ctrl+S")` method |
| `uwgo` | `go(HomeScreen.class);` (inside a method) |

## Inspections

Mistakes that otherwise only show when the app runs:

| Inspection | Example | Quick fix |
|---|---|---|
| Misspelled shortcut | `@Shortcut("Ctl+S")` is read as plain S; `"Ctrl+S+D"` only reacts to D | Corrects the spelling |
| Text that won't update | `new Text("Hi " + name)` keeps showing the first name | Turns it into `() -> "Hi " + name` |
| Screen unweird can't create | A screen with only `Detail(String id)`, or an inner (non-static) screen class | Adds a constructor / makes it static |
| Annotation processor missing | A module with screens but without `unweird-processor` ("no field order recorded") | — |

They're under *Settings → Editor → Inspections → unweird*.
