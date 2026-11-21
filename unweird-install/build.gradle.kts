plugins {
    `java-library`
}

// Browsing catalogs and installing content (mods, packs, plugins, assets, ...) from any source.
dependencies {
    api(project(":unweird-core"))
    implementation(libs.gson)
    // Catalog icons are often WebP, which NanoVG can't decode; this ImageIO plugin (pure Java) can.
    runtimeOnly(libs.imageio.webp)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(project(":unweird-processor"))
    testAnnotationProcessor(project(":unweird-processor"))
}
