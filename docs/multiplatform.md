# Desktop + Android

One set of screens, two apps. The screens live in a plain Java module; each platform gets a tiny launcher module
that depends on it.

```
my-app/
├── settings.gradle.kts
├── shared/    screens, components, logic     java-library
├── desktop/   Main: Unweird.app(...)         application
└── android/   Main extends UnweirdActivity   com.android.application
```
The [IntelliJ plugin](intellij.md)'s New Project wizard creates exactly this ("Desktop + Android").

## Build files

::: code-group

```kotlin [settings.gradle.kts]
pluginManagement {
    repositories {
        google()
        mavenCentral()
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
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "my-app"
include("shared", "desktop", "android")
```

```kotlin [build.gradle.kts]
plugins {
    id("com.android.application") version "9.5.2" apply false
}
```

```kotlin [shared/build.gradle.kts]
plugins {
    `java-library`
    id("com.dervarex.unweird") version "v0.2.0"
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

unweird {
    components = true
    threeD = true
}
```

```kotlin [desktop/build.gradle.kts]
plugins {
    application
    id("com.dervarex.unweird") version "v0.2.0"
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

application {
    mainClass = "com.example.desktop.Main"
}

dependencies {
    implementation(project(":shared"))
}

unweird {
    components = true
    threeD = true
}
```

```kotlin [android/build.gradle.kts]
plugins {
    id("com.android.application")
    id("com.dervarex.unweird") version "v0.2.0"
}

android {
    namespace = "com.example.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example"
        minSdk = 26
        targetSdk = 36
    }
}

dependencies {
    implementation(project(":shared"))
}

unweird {
    components = true
    threeD = true
}
```

:::

::: code-group

```java [desktop/.../Main.java]
public class Main {
    public static void main(String[] args) {
        Unweird.app("My App").size(1000, 680).remember().start(HomeScreen.class);
    }
}
```

```java [android/.../Main.java]
public class Main extends UnweirdActivity<HomeScreen> {
}
```

:::

The plugin does the platform part in each module: LWJGL and its natives on desktop, `unweird-android` without
LWJGL on Android, the annotation processor everywhere. Keep the `unweird { }` flags the same in all three modules.

## Where does a dependency go?

| The code that uses it lives in | Put it in | Example |
|---|---|---|
| Screens, components, shared logic | `shared` | Gson, your API client, a pure-Java library |
| Desktop only | `desktop` | A JDBC driver, AWT/ImageIO code, `unweird-install` |
| Android only | `android` | AndroidX, Play services, anything with `android.*` |

```kotlin
// shared/build.gradle.kts
dependencies {
    api("com.google.code.gson:gson:2.13.1")        // the launchers use it directly too
    implementation("org.jsoup:jsoup:1.21.1")       // only shared code uses it
}
```

- `implementation` in `shared` is enough for the apps to *run* with it. Use `api` when `desktop` or `android`
  code also calls it.
- Whatever is in `shared` ends up in the Android app, so it must work there. Android has no `java.awt`, no
  `javax.swing`, no `java.net.http`, no `java.lang.management`; `List.getFirst()` and friends need a recent
  Android. Libraries that only use `java.util`, `java.io`, `java.nio.file` and `java.net.URL` are fine.
- On Android, add the `INTERNET` permission to the manifest if a library goes online.

## Platform-specific code

When something must work differently per platform (file locations, sharing, notifications), declare an interface
in `shared` and implement it in each launcher:

::: code-group

```java [shared/.../Platform.java]
public interface Platform {
    Path dataDir();
    void share(String text);

    /** Set by the launcher before the first screen opens. */
    final class Current {
        public static Platform it;
    }
}
```

```java [desktop/.../Main.java]
public class Main {
    public static void main(String[] args) {
        Platform.Current.it = new Platform() {
            public Path dataDir() { return Path.of(System.getProperty("user.home"), ".my-app"); }
            public void share(String text) { Unweird.open("mailto:?body=" + URLEncoder.encode(text, UTF_8)); }
        };
        Unweird.app("My App").start(HomeScreen.class);
    }
}
```

```java [android/.../Main.java]
public class Main extends UnweirdActivity<HomeScreen> {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Platform.Current.it = new Platform() {
            public Path dataDir() { return getFilesDir().toPath(); }
            public void share(String text) {
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                        .setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null));
            }
        };
        super.onCreate(savedInstanceState);
    }
}
```

```java [shared/.../NoteScreen.java]
public class NoteScreen extends Screen {
    Field note   = new Field("Note");
    Button share = new Button("Share").onClick(() -> Platform.Current.it.share(note.value()));
}
```

:::

Set it before `super.onCreate(...)` / `start(...)`: the first screen is created right there.

### Smaller differences

Most screens need no branching at all. Where they do, check at runtime:

```java
static final boolean ANDROID = System.getProperty("java.vendor", "").contains("Android");

Button quit = new Button("Quit").visibleIf(() -> !ANDROID).onClick(Unweird::quit);
```

## What changes on a phone

Taps instead of clicks, drag to scroll, the on-screen keyboard, the back gesture as Escape. See
[Android](android.md#whats-different-on-a-phone).

## Running

```bash
./gradlew :desktop:run
./gradlew :android:installDebug     # needs the Android SDK, see Android
```
