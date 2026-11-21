plugins {
    `java-library`
}

dependencies {
    api(project(":unweird-core"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    annotationProcessor(project(":unweird-processor"))
    testAnnotationProcessor(project(":unweird-processor"))
}
