plugins {
    application
}

dependencies {
    implementation(project(":unweird-components"))
    implementation(project(":unweird-install"))
    implementation(project(":unweird-3d"))
    implementation(libs.gson) // for the Modrinth source

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(project(":unweird-processor"))
}

application {
    mainClass = "com.dervarex.example.Example"
}
