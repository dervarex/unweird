// Small, self-contained apps shown on the docs site. Each folder under src/ is its own source set, so one example's
// @Default sidebar doesn't show up in the others. Run one with `./gradlew :examples:run<Name>`, e.g. runCounter.

val examples = file("src").listFiles { f -> f.isDirectory && f.name != "main" && f.name != "test" }!!
        .map { it.name }.sorted()

val sourceSets = the<SourceSetContainer>()

examples.forEach { name ->
    val set = sourceSets.create(name)
    dependencies {
        set.implementationConfigurationName(project(":unweird-components"))
        set.implementationConfigurationName(project(":unweird-3d"))
        set.compileOnlyConfigurationName(libs.lombok)
        set.annotationProcessorConfigurationName(libs.lombok)
        set.annotationProcessorConfigurationName(project(":unweird-processor"))
    }
    tasks.register<JavaExec>("run" + name.replaceFirstChar { it.uppercase() }) {
        group = "examples"
        description = "Runs the $name example"
        classpath = set.runtimeClasspath
        mainClass = "com.dervarex.examples.$name.Main"
    }
    tasks.named("assemble") { dependsOn(set.classesTaskName) }
}
