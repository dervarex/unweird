# Getting started

## Add it to your build

unweird isn't on Maven yet, so pull it in as a composite build. Clone it next to your project:

```bash
git clone https://github.com/dervarex/unweird.git
```

`settings.gradle.kts`:

```kotlin
includeBuild("../unweird")
```

`build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.dervarex:unweird-core:0.1.0-SNAPSHOT")
    annotationProcessor("com.dervarex:unweird-processor:0.1.0-SNAPSHOT")
}
```

Don't skip the annotation processor. unweird adds your widget fields to the screen in the order you wrote them, and
Java can't tell that order at runtime. The processor writes it down at compile time. Without it you get
`no field order recorded for ...` on startup.

You need Java 21+ and OpenGL 3.3. Linux works; Windows and macOS aren't done yet.

## First window

```java
@Title("Hello")
public class HelloScreen extends Screen {
    Text heading = new Text("Hello there");
    Field name   = new Field("Your name");
    Text greet   = new Text(() -> name.value().isBlank() ? "" : "Hi " + name.value() + "!");
}

public class Main {
    public static void main(String[] args) {
        Unweird.start(HelloScreen.class);
    }
}
```

That's a whole app. Type into the field and the greeting updates.

`Unweird.start` blocks until the window closes and has to run on the main thread.

## Window settings

```java
Unweird.app("My Launcher")
        .size(1100, 720)
        .minSize(800, 520)
        .icon("/images/logo.png")   // classpath
        .remember()                  // keeps position/size/maximized between runs
        .theme(Theme.dark())
        .start(LoginScreen.class);
```

The window title becomes `<screen @Title> — My Launcher`.

`remember()` stores a small file in your config folder (`~/.config/my-launcher/` on Linux).

Next: [Screens and components](screens-and-components.md)
