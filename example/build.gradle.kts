plugins {
    application
}

dependencies {
    implementation(project(":unweird-components"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(project(":unweird-processor"))
}

application {
    mainClass = "com.dervarex.example.Example"
}
