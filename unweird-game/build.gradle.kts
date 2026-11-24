plugins {
    `java-library`
}

// Easy games: 2D (sprites, levels, platformer physics) and 3D (block worlds, terrain, first-person explorer),
// plus sound. Built on core's Screen + Canvas.gl and unweird-3d's renderer.
val lwjglNatives = "natives-linux"
val audioModules = listOf(libs.lwjgl.openal, libs.lwjgl.stb)

dependencies {
    api(project(":unweird-core"))
    api(project(":unweird-3d"))
    audioModules.forEach { module ->
        api(module)
        runtimeOnly(variantOf(module) { classifier(lwjglNatives) })
    }

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(project(":unweird-processor"))
    testAnnotationProcessor(project(":unweird-processor"))
}
