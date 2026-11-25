buildscript {
    // The Android Gradle plugin, only when the Android modules are part of the build (same check as settings.gradle.kts).
    val localProperties = rootDir.resolve("local.properties")
    val androidSdk = System.getenv("ANDROID_HOME") != null || System.getenv("ANDROID_SDK_ROOT") != null
            || localProperties.exists() && localProperties.readLines().any { it.trim().startsWith("sdk.dir") }
    if (androidSdk) {
        repositories {
            google()
            mavenCentral()
        }
        dependencies {
            classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
        }
    }
}

plugins {
    java
}

allprojects {
    group = "com.dervarex"
    version = "0.1.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
    }
}

/** Plain Java modules; the Android ones are built by the Android Gradle plugin. */
val javaModules = subprojects.filter { !it.name.endsWith("android") }

configure(javaModules) {
    apply(plugin = "java")

    tasks.withType<JavaCompile>().configureEach {
        options.release = 21
        options.encoding = "UTF-8"
    }

    dependencies {
        "testImplementation"(platform(rootProject.libs.junit.bom))
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testRuntimeOnly"(rootProject.libs.junit.launcher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

// Library modules are published (JitPack builds them from a git tag); the example app isn't.
configure(javaModules.filter { it.name != "example" }) {
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        withSourcesJar()
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                pom {
                    name = project.name
                    description = "unweird: a Java UI library with its own renderer"
                    url = "https://github.com/dervarex/unweird"
                    licenses {
                        license {
                            name = "Apache License 2.0"
                            url = "https://www.apache.org/licenses/LICENSE-2.0"
                        }
                    }
                }
            }
        }
    }
}

// Gradle module metadata can't express the LWJGL "natives-linux" classifier dependencies and silently drops them,
// so consumers would compile fine and then crash at startup. Publish only the POM, which keeps them.
configure(javaModules) {
    tasks.withType<GenerateModuleMetadata>().configureEach {
        enabled = false
    }
}

// The sources jar must match the bytecode: modules using Lombok publish "delomboked" sources (with the generated
// getters written out), otherwise IDEs report "library source does not match the bytecode".
configure(javaModules.filter { it.name != "example" }) {
    afterEvaluate {
        val compileClasspath = configurations.getByName("compileClasspath")
        if (compileClasspath.allDependencies.none { it.group == "org.projectlombok" }) {
            return@afterEvaluate
        }
        val sources = extensions.getByType<SourceSetContainer>()["main"].java
        val delombok = tasks.register<JavaExec>("delombok") {
            val output = layout.buildDirectory.dir("delombok")
            inputs.files(sources.srcDirs)
            inputs.files(compileClasspath)
            outputs.dir(output)
            classpath = compileClasspath
            mainClass = "lombok.launch.Main"
            argumentProviders.add(CommandLineArgumentProvider {
                listOf("delombok", "--quiet", "--encoding=UTF-8",
                        "--classpath=" + compileClasspath.asPath,
                        "-d", output.get().asFile.absolutePath) +
                        sources.srcDirs.filter { it.exists() }.map { it.absolutePath }
            })
            doFirst { output.get().asFile.deleteRecursively() }
        }
        val originals = sources.srcDirs.map { it.toPath() }
        tasks.named<Jar>("sourcesJar") {
            from(delombok)
            // drop the sources as written; their delomboked versions take their place
            eachFile {
                if (originals.any { file.toPath().startsWith(it) }) {
                    exclude()
                }
            }
        }
    }
}
