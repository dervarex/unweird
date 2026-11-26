---
title: Getting started
---

# Getting started

Java 21+, OpenGL 3.3. Linux works today (X11 and Wayland); Windows and macOS are planned. Android: see [Android](android.md).

## Add it to your build

The Gradle plugin is the short way. It adds the library, the annotation processor and the native libraries for your OS.

::: code-group

```kotlin [settings.gradle.kts]
pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.dervarex.unweird") {
                useModule("com.github.dervarex.unweird:unweird-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```kotlin [build.gradle.kts]
plugins {
    application
    id("com.dervarex.unweird") version "v0.2.0"
}

application {
    mainClass = "com.example.Main"
}

unweird {
    components = true   // unweird-components (LoginForm)
    threeD = true       // unweird-3d
    install = true      // unweird-install
}
```

:::

Without the plugin, add the dependencies yourself:

::: code-group

```kotlin [build.gradle.kts]
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.dervarex.unweird:unweird-core:v0.2.0")
    annotationProcessor("com.github.dervarex.unweird:unweird-processor:v0.2.0")

    // optional
    implementation("com.github.dervarex.unweird:unweird-components:v0.2.0")
    implementation("com.github.dervarex.unweird:unweird-install:v0.2.0")
    implementation("com.github.dervarex.unweird:unweird-3d:v0.2.0")
}
```

```groovy [build.gradle]
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.dervarex.unweird:unweird-core:v0.2.0'
    annotationProcessor 'com.github.dervarex.unweird:unweird-processor:v0.2.0'

    // optional
    implementation 'com.github.dervarex.unweird:unweird-components:v0.2.0'
    implementation 'com.github.dervarex.unweird:unweird-install:v0.2.0'
    implementation 'com.github.dervarex.unweird:unweird-3d:v0.2.0'
}
```

:::

::: warning Don't skip the annotation processor
Widget fields show up in the order you declare them, and Java can't see that order at runtime. The processor
writes it down at compile time. Without it: `no field order recorded for ...` on startup.
:::

The plain dependencies bring the Linux natives only. For Windows and macOS use the plugin, or add LWJGL's
`natives-windows` / `natives-macos` classifiers yourself (and `-XstartOnFirstThread` on macOS).

Working on unweird at the same time? Clone it next to your project and add `includeBuild("../unweird")` to
`settings.gradle.kts` (also inside `pluginManagement` when you use the plugin).

| Module | |
|---|---|
| `unweird-core` | Window, widgets, layout, dialogs, toasts, shortcuts |
| `unweird-processor` | Annotation processor (field order, `@Default` components) |
| `unweird-components` | Ready-made components: `LoginForm` |
| `unweird-install` | Browse a catalog, install content into a folder |
| `unweird-3d` | 3D views |
| `unweird-android` | The Android backend |

## First window

::: code-group

```java [HelloScreen.java]
import com.dervarex.unweird.*;
import com.dervarex.unweird.annotation.Title;

@Title("Hello")
public class HelloScreen extends Screen {
    Text heading = new Text("Hello there");
    Field name   = new Field("Your name");
    Text greet   = new Text(() -> name.value().isBlank() ? "" : "Hi " + name.value() + "!");
}
```

```java [Main.java]
import com.dervarex.unweird.Unweird;

public class Main {
    public static void main(String[] args) {
        Unweird.start(HelloScreen.class);
    }
}
```

:::

That's a whole app: a centered card with a heading, a field, and a greeting that follows what you type.
`Unweird.start` must run on the main thread and blocks until the window closes.

## Window settings

```java
Unweird.app("My Launcher")
        .size(1100, 720)
        .minSize(800, 520)
        .icon("/images/logo.png")   // from the classpath (src/main/resources)
        .remember()                  // position, size, maximized: kept between runs
        .maximized()                 // first start only, if remember() knows better
        .theme(Theme.dark())
        .fallbackFont("/fonts/NotoSansJP-Regular.ttf")
        .start(LoginScreen.class);
```

| Call | |
|---|---|
| `size(w, h)` | Initial size in logical pixels |
| `minSize(w, h)` | The window can't get smaller |
| `icon(resource)` | Window and taskbar icon. Ignored on Wayland and macOS |
| `remember()` | Stored under the app name in your config folder (`~/.config/my-launcher/` on Linux) |
| `maximized()` | Start maximized |
| `theme(theme)` | Start with this theme, see [Styling](styling.md) |
| `fallbackFont(resource)` | A font for scripts the built-in ones lack (CJK, ...) |

The window title is `<screen's @Title> — <app name>`.

App-wide shortcuts go before `start`:

```java
Unweird.shortcut("Ctrl+Q", Unweird::quit);
```

## A typical project

```
src/main/java/com/example/
├── Main.java                  Unweird.app(...).start(LoginScreen.class)
├── screens/
│   ├── LoginScreen.java       one class per screen
│   ├── HomeScreen.java
│   └── SettingsScreen.java
└── components/
    ├── Sidebar.java           @Default(Side.LEFT): on every screen
    └── AccountSection.java    a reusable group of widgets
```

The [examples](/examples/) are small complete apps in this shape.
