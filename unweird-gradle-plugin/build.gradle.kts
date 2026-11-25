plugins {
    `java-gradle-plugin`
}

// `plugins { id("com.dervarex.unweird") }`: unweird, its annotation processor and the platform bits in one line.
gradlePlugin {
    plugins {
        create("unweird") {
            id = "com.dervarex.unweird"
            implementationClass = "com.dervarex.unweird.gradle.UnweirdPlugin"
            displayName = "unweird"
            description = "Sets up a project for the unweird UI library (desktop or Android)"
        }
    }
}

// The versions the plugin hands out: unweird's own and the LWJGL it was built with.
tasks.processResources {
    val versions = mapOf("unweird" to project.version.toString(), "lwjgl" to libs.versions.lwjgl.get())
    inputs.properties(versions)
    filesMatching("**/versions.properties") {
        expand(versions)
    }
}
