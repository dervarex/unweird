# Android

The same screens run on Android phones and tablets. `unweird-android` draws them with the same NanoVG as on
desktop (built for OpenGL ES 3), so an app looks the same on both.

```java
public class Main extends UnweirdActivity<LoginScreen> {
}
```

That's the whole Android-specific code. `LoginScreen` and everything it leads to are ordinary unweird screens.

## What's different on a phone

| Desktop | Android |
|---|---|
| Click | Tap |
| Mouse wheel | Drag anywhere to scroll; flick and it keeps going |
| Dragging (sliders, your `onDrag`) | Same. Widgets with `onDrag` keep the finger, everywhere else a drag scrolls the page |
| Typing | Tapping a field brings up the on-screen keyboard; `masked()` fields get a password keyboard. The page moves so the field stays above the keyboard |
| Escape | The back gesture. It closes a popup or dialog, otherwise runs the screen's `@Shortcut("Escape")` method. With neither, the app goes to the background |
| Hover, tooltips | None (no pointer). With a mouse plugged in (Chromebooks, desktop mode) hover works like on desktop |
| Logical pixels | Density-independent pixels (dp), so sizes match other apps |
| `Unweird.open(url)` | Opens the link in the browser (or the app that handles it) |
| 3D (`View3D`), `Canvas.gl` | Same, on OpenGL ES 3. Drag turns the camera; there's no pinch-to-zoom yet. Raw GL code must use `Gl` (see [Your own widgets](custom-widgets.md)) |
| Images, textures | PNG and JPEG as on desktop, plus whatever Android decodes (WebP, HEIF). `BufferedImage` doesn't exist: use `Pixels` |
| Window size, icon, `remember()` | Don't apply. The app's icon and name come from the manifest |

The status and navigation bars are drawn over by the theme's background, and their icons turn dark or light with
the theme. Screens are laid out in the space between them.

## Setting up an app

Android apps are built by the Android Gradle plugin. With unweird's Gradle plugin, a minimal app module is:

```kotlin
// build.gradle.kts
plugins {
    id("com.android.application")
    id("com.dervarex.unweird")
}

android {
    namespace = "com.example.notes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.notes"
        minSdk = 26
        targetSdk = 36
    }
}
```

The plugin adds `unweird-android` and the annotation processor, keeps LWJGL (the desktop backend) out, sets Java 21
and tells Android to combine unweird's index files (`META-INF/unweird/**`) instead of failing on duplicates.
`unweird { threeD = true }` adds 3D. See [Getting started](getting-started.md) for where the plugin comes from.

<details><summary>Without the plugin</summary>

```kotlin
android {
    // ...
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    packaging {
        resources {
            merges += "META-INF/unweird/**"
        }
    }
}

// LWJGL is unweird's desktop backend; Android draws with its own.
configurations.configureEach {
    exclude(group = "org.lwjgl")
}

dependencies {
    implementation(project(":unweird-android"))
    implementation(project(":unweird-3d"))          // optional: 3D views work on Android too
    annotationProcessor(project(":unweird-processor"))
}
```

</details>

```xml
<!-- src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="Notes"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".Main"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden|density|uiMode">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Keep the `configChanges` line: with it, rotating the phone lays the screen out again. Without it, Android
recreates the activity and the app starts over at the first screen.

Without the plugin's packaging rule the build fails with `2 files found with path 'META-INF/unweird/fields.idx'`:
each module (unweird itself and your app) records its widget field order in a file with that name.

`unweird-android` isn't on JitPack yet. Until it is, include unweird as a build next to your app
(`includeBuild("../unweird")` in your settings file) and use the project dependencies shown above.

### Theme and fonts

Override `app()` for the same settings as on desktop:

```java
public class Main extends UnweirdActivity<LoginScreen> {
    @Override
    protected App app() {
        return Unweird.app("Notes")
                .theme(Theme.dark())
                .fallbackFont("/fonts/NotoSansJP-Regular.ttf");
    }
}
```

## Building and running

You need the Android SDK and NDK. unweird's repository has a Nix shell with the exact versions:

```bash
nix develop .#android                      # SDK, NDK, emulator, JDK 21
./gradlew :example-android:assembleDebug   # → example-android/build/outputs/apk/debug/
adb install -r example-android/build/outputs/apk/debug/example-android-debug.apk
```

Without Nix, install Android Studio (or the command-line tools) and set `ANDROID_HOME`, or put `sdk.dir=...` into
`local.properties`. The Android modules are only part of the build when an SDK is found; desktop builds don't need one.

## Not there yet

- More than one finger (pinch to zoom), long-press menus, text selection handles.
- Dropping files onto the window (`onDrop`).
