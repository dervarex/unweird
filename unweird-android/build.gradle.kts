plugins {
    id("com.android.library")
}

// Versions match the Nix dev shell (flake.nix: `nix develop .#android`).
android {
    namespace = "com.dervarex.unweird.android"
    compileSdk = 36
    buildToolsVersion = "36.1.0"
    ndkVersion = "29.0.14206865"

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    // the shared UI; LWJGL is the desktop backend's and stays out of the app
    api(project(":unweird-core")) {
        exclude(group = "org.lwjgl")
    }
}
