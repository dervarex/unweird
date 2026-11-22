plugins {
    java
}

allprojects {
    group = "com.dervarex"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
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
configure(subprojects.filter { it.name != "example" }) {
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
