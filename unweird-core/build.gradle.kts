plugins {
    `java-library`
}

// Linux only for now; Windows/macOS natives get added later (see DESIGN.md).
val lwjglNatives = "natives-linux"
val lwjglModules = listOf(libs.lwjgl.asProvider(), libs.lwjgl.glfw, libs.lwjgl.opengl, libs.lwjgl.nanovg)

dependencies {
    api(platform(libs.lwjgl.bom))
    lwjglModules.forEach { module ->
        api(module)
        runtimeOnly(variantOf(module) { classifier(lwjglNatives) })
    }

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Core's own components (Dialog, ...) and the tests' screens need the field-order index too.
    annotationProcessor(project(":unweird-processor"))
    testAnnotationProcessor(project(":unweird-processor"))
}
