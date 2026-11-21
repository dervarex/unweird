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

    // Tests declare Screen subclasses, so they need the field-order index too.
    testAnnotationProcessor(project(":unweird-processor"))
}
