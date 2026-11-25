package com.dervarex.unweird.intellij

/** Where the app runs. */
enum class Target(val label: String) {
    DESKTOP("Desktop"),
    ANDROID("Android"),
    BOTH("Desktop + Android");
}

/** What the New Project wizard asked. */
data class ProjectOptions(
    /** The project's name, e.g. "Notes"; also the app's name. */
    val name: String,
    /** Java package for the code, e.g. "com.example.notes". */
    val packageName: String,
    val target: Target,
    val threeD: Boolean = false,
    val components: Boolean = false,
    /** The unweird release, e.g. "v0.2.0". */
    val version: String = DEFAULT_VERSION,
    /** A local checkout of unweird to build against instead of a release; blank for none. */
    val localCheckout: String = "",
) {
    init {
        require(name.isNotBlank()) { "The project needs a name" }
        require(packageName.matches(Regex("[a-zA-Z_][\\w]*(\\.[a-zA-Z_][\\w]*)*"))) {
            "\"$packageName\" isn't a Java package name (letters, digits and dots, like com.example.notes)"
        }
    }

    val local: Boolean get() = localCheckout.isNotBlank()
}

/** The first unweird release with the Gradle plugin and Android support. */
const val DEFAULT_VERSION = "v0.2.0"
const val ANDROID_GRADLE_PLUGIN = "9.5.2"

/**
 * The files of a new unweird project: paths relative to the project folder → contents.
 * The Gradle wrapper's jar and scripts are copied separately (they're binary or need the executable bit).
 */
object ProjectFiles {

    fun generate(options: ProjectOptions): Map<String, String> {
        val files = linkedMapOf<String, String>()
        files["settings.gradle.kts"] = settings(options)
        files[".gitignore"] = GITIGNORE
        files["gradle/wrapper/gradle-wrapper.properties"] = WRAPPER_PROPERTIES
        val packagePath = options.packageName.replace('.', '/')
        when (options.target) {
            Target.DESKTOP -> {
                files["build.gradle.kts"] = desktopBuild(options, shared = false)
                files["src/main/java/$packagePath/Main.java"] = desktopMain(options)
                files["src/main/java/$packagePath/HomeScreen.java"] = homeScreen(options)
            }
            Target.ANDROID -> {
                files["build.gradle.kts"] = androidRootBuild()
                files["app/build.gradle.kts"] = androidBuild(options, "app", shared = false)
                files["app/src/main/AndroidManifest.xml"] = manifest(options)
                files["app/src/main/java/$packagePath/Main.java"] = androidMain(options)
                files["app/src/main/java/$packagePath/HomeScreen.java"] = homeScreen(options)
            }
            Target.BOTH -> {
                files["build.gradle.kts"] = androidRootBuild()
                files["shared/build.gradle.kts"] = sharedBuild(options)
                files["shared/src/main/java/$packagePath/HomeScreen.java"] = homeScreen(options)
                files["desktop/build.gradle.kts"] = desktopBuild(options, shared = true)
                files["desktop/src/main/java/$packagePath/desktop/Main.java"] = desktopMain(options, "${options.packageName}.desktop")
                files["android/build.gradle.kts"] = androidBuild(options, "android", shared = true)
                files["android/src/main/AndroidManifest.xml"] = manifest(options)
                files["android/src/main/java/$packagePath/android/Main.java"] = androidMain(options, "${options.packageName}.android")
            }
        }
        return files
    }

    /** The file to open in the editor once the project is created. */
    fun mainFile(options: ProjectOptions): String {
        val packagePath = options.packageName.replace('.', '/')
        return when (options.target) {
            Target.DESKTOP -> "src/main/java/$packagePath/HomeScreen.java"
            Target.ANDROID -> "app/src/main/java/$packagePath/HomeScreen.java"
            Target.BOTH -> "shared/src/main/java/$packagePath/HomeScreen.java"
        }
    }

    private fun settings(o: ProjectOptions): String {
        val android = o.target != Target.DESKTOP
        val modules = when (o.target) {
            Target.DESKTOP -> ""
            Target.ANDROID -> "\ninclude(\"app\")\n"
            Target.BOTH -> "\ninclude(\"shared\", \"desktop\", \"android\")\n"
        }
        val google = if (android) "        google()\n" else ""
        val pluginSource = if (o.local) {
            "    // unweird's Gradle plugin from your checkout\n    includeBuild(\"${escape(o.localCheckout)}\")\n"
        } else {
            """    // unweird's Gradle plugin is published on JitPack
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.dervarex.unweird") {
                useModule("com.github.dervarex.unweird:unweird-gradle-plugin:${'$'}{requested.version}")
            }
        }
    }
"""
        }
        val jitpack = if (o.local) "" else "        maven(\"https://jitpack.io\")\n"
        val includeBuild = if (o.local) "\n// unweird itself from your checkout\nincludeBuild(\"${escape(o.localCheckout)}\")\n" else ""
        return """pluginManagement {
    repositories {
$google        mavenCentral()
        gradlePluginPortal()
$jitpack    }
$pluginSource}

dependencyResolutionManagement {
    repositories {
$google        mavenCentral()
$jitpack    }
}

rootProject.name = "${escape(o.name)}"
$modules$includeBuild"""
    }

    private fun unweirdBlock(o: ProjectOptions): String {
        val lines = buildList {
            if (o.threeD) add("    threeD = true")
            if (o.components) add("    components = true")
        }
        return if (lines.isEmpty()) "" else "\nunweird {\n${lines.joinToString("\n")}\n}\n"
    }

    private fun pluginVersion(o: ProjectOptions) = if (o.local) "" else " version \"${o.version}\""

    private fun desktopBuild(o: ProjectOptions, shared: Boolean): String {
        val mainClass = if (shared) "${o.packageName}.desktop.Main" else "${o.packageName}.Main"
        val dependency = if (shared) "\ndependencies {\n    implementation(project(\":shared\"))\n}\n" else ""
        return """plugins {
    application
    id("com.dervarex.unweird")${pluginVersion(o)}
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "$mainClass"
}
$dependency${unweirdBlock(o)}"""
    }

    private fun sharedBuild(o: ProjectOptions) = """// Screens and everything else both apps share.
plugins {
    `java-library`
    id("com.dervarex.unweird")${pluginVersion(o)}
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
${unweirdBlock(o)}"""

    private fun androidRootBuild() = """plugins {
    id("com.android.application") version "$ANDROID_GRADLE_PLUGIN" apply false
}
"""

    private fun androidBuild(o: ProjectOptions, module: String, shared: Boolean): String {
        val namespace = if (shared) "${o.packageName}.android" else o.packageName
        val dependency = if (shared) "\ndependencies {\n    implementation(project(\":shared\"))\n}\n" else ""
        return """plugins {
    id("com.android.application")
    id("com.dervarex.unweird")${pluginVersion(o)}
}

android {
    namespace = "$namespace"
    compileSdk = 36

    defaultConfig {
        applicationId = "${o.packageName}"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}
$dependency${unweirdBlock(o)}"""
    }

    private fun manifest(o: ProjectOptions) = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="${xml(o.name)}"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <!-- configChanges: unweird lays the screen out again on rotation, so the app keeps its state -->
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
"""

    private fun desktopMain(o: ProjectOptions, packageName: String = o.packageName): String {
        val import = if (packageName != o.packageName) "import ${o.packageName}.HomeScreen;\n" else ""
        return """package $packageName;

${import}import com.dervarex.unweird.Unweird;

public class Main {

    public static void main(String[] args) {
        Unweird.app("${java(o.name)}").size(900, 600).start(HomeScreen.class);
    }
}
"""
    }

    private fun androidMain(o: ProjectOptions, packageName: String = o.packageName): String {
        val import = if (packageName != o.packageName) "import ${o.packageName}.HomeScreen;\n" else ""
        return """package $packageName;

${import}import com.dervarex.unweird.UnweirdActivity;

/** The Android app: the type argument is the first screen. */
public class Main extends UnweirdActivity<HomeScreen> {
}
"""
    }

    private fun homeScreen(o: ProjectOptions) = """package ${o.packageName};

import com.dervarex.unweird.Button;
import com.dervarex.unweird.Screen;
import com.dervarex.unweird.Text;
import com.dervarex.unweird.annotation.State;
import com.dervarex.unweird.annotation.Title;

@Title("${java(o.name)}")
public class HomeScreen extends Screen {

    @State int clicks;

    Text heading  = new Text("${java(o.name)}");
    Text subtitle = new Text(() -> clicks == 0 ? "Nothing clicked yet." : "Clicked " + clicks + (clicks == 1 ? " time." : " times."));
    Button click  = new Button("Click me").onClick(() -> clicks++);
}
"""

    private fun escape(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")
    private fun java(text: String) = text.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun xml(text: String) = text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")

    private const val WRAPPER_PROPERTIES = """distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.0-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
"""

    private const val GITIGNORE = """.gradle/
build/
.idea/
*.iml
local.properties
.cxx/
"""
}
