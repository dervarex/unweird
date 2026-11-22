# unweird

A Java UI library with its own renderer (LWJGL + NanoVG) and an API that stays out of your way:
screens and components are plain classes, widget fields are laid out automatically, and state is just fields.

```java
@Title("Sign in")
public class LoginScreen extends Screen {
    Text heading  = new Text("Welcome back");
    Field user    = new Field("Username");
    Field pass    = new Field("Password").masked();
    Button signIn = new Button("Sign in").onClick(() -> go(HomeScreen.class));
}

Unweird.start(LoginScreen.class);
```

See [DESIGN.md](DESIGN.md) for how everything works, and `example/` for a demo app (`./gradlew :example:run`).

## Modules

| Module | What it is |
|---|---|
| `unweird-core` | Window, renderer, layout, widgets, dialogs, toasts, shortcuts |
| `unweird-processor` | Annotation processor (field order, `@Default` components) |
| `unweird-components` | Ready-made components such as `LoginForm` |
| `unweird-install` | Browsing catalogs and installing content from any source |
| `unweird-3d` | 3D views inside layouts: scene graph, meshes, textures, picking |

## Why another UI library?

None of the existing options fit my use case:

- **Swing (+ FlatLaf)**: too old.
- **JavaFX**: too bloated.
- **Compose Desktop**: not available for Java, it needs Kotlin.

## How this was made

The development of unweird was heavily assisted by Claude (Anthropic's AI). The code was largely written by Claude;
the decisions — what the library should do, how it should work and how its API should feel — were made by me.
