plugins {
    id("com.android.library")
    `maven-publish`
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
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

// Published like the other modules (JitPack builds it when its build machine has an Android SDK).
afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["release"])
                pom {
                    name = project.name
                    description = "unweird on Android"
                    url = "https://github.com/dervarex/unweird"
                    licenses {
                        license {
                            name = "Apache License 2.0"
                            url = "https://www.apache.org/licenses/LICENSE-2.0"
                        }
                    }
                }
            }
        }
    }
}
