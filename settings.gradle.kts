pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "unweird"

include(
    "unweird-core",
    "unweird-processor",
    "unweird-components",
    "unweird-install",
    "unweird-3d",
    "unweird-gradle-plugin",
    "example",
)

// The Android modules need an Android SDK (ANDROID_HOME, or sdk.dir in local.properties; `nix develop .#android`
// sets one up). Without one they're left out, so desktop builds and JitPack don't need it.
val localSdk = file("local.properties").takeIf { it.exists() }?.readLines()?.any { it.trim().startsWith("sdk.dir") } == true
if (System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null || localSdk) {
    include("unweird-android", "example-android")
}
