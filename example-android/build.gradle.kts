plugins {
    id("com.android.application")
}

android {
    namespace = "com.dervarex.example.android"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.dervarex.unweird.example"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            // every module adds its own unweird index; they're combined, not one picked
            merges += "META-INF/unweird/**"
        }
    }
}

dependencies {
    implementation(project(":unweird-android"))
    annotationProcessor(project(":unweird-processor"))
}
