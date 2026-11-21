plugins {
    `java-library`
}

// 3D views inside unweird layouts: meshes, textures, a scene graph, an orbit camera, picking.
dependencies {
    api(project(":unweird-core"))
    api(libs.joml)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(project(":unweird-processor"))
    testAnnotationProcessor(project(":unweird-processor"))
}
