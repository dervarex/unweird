# Getting started

## Add it to your build

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

Don't skip the annotation processor. unweird adds your widget fields to the screen in the order you wrote them, and
Java can't tell that order at runtime. The processor writes it down at compile time. Without it you get
`no field order recorded for ...` on startup.

You need Java 21+ and OpenGL 3.3. Linux works; Windows and macOS aren't done yet.

On NixOS, run with a regular JDK (one Gradle or IntelliJ downloads), not one from the Nix store: Nix-built JDKs
can't see the system's `libGL`, and you get "couldn't load OpenGL". The error message lists the alternatives.

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
