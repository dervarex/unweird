package com.dervarex.unweird.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.JavaExec;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Sets a project up for unweird. With the {@code java} or {@code application} plugin: unweird-core, the annotation
 * processor and the LWJGL natives for this computer. With an Android plugin: unweird-android, the processor, no
 * LWJGL, Java 21 and the packaging rule for unweird's index files. Optional modules come from {@code unweird { }}.
 */
public class UnweirdPlugin implements Plugin<Project> {

    /** Where JitPack publishes unweird. */
    static final String PUBLISHED_GROUP = "com.github.dervarex.unweird";
    /** unweird's own group, used when it's an included build ({@code includeBuild("../unweird")}). */
    static final String LOCAL_GROUP = "com.dervarex";

    private static final List<String> LWJGL_MODULES = List.of("lwjgl", "lwjgl-glfw", "lwjgl-opengl", "lwjgl-nanovg");

    @Override
    public void apply(Project project) {
        Properties versions = versions();
        UnweirdExtension unweird = project.getExtensions().create("unweird", UnweirdExtension.class);
        unweird.getVersion().convention(versions.getProperty("unweird"));
        unweird.getThreeD().convention(false);
        unweird.getComponents().convention(false);
        unweird.getInstall().convention(false);

        project.getPluginManager().withPlugin("java", plugin -> desktop(project, unweird, versions.getProperty("lwjgl")));
        project.getPluginManager().withPlugin("com.android.application", plugin -> android(project, unweird));
        project.getPluginManager().withPlugin("com.android.library", plugin -> android(project, unweird));
    }

    private void desktop(Project project, UnweirdExtension unweird, String lwjglVersion) {
        if (isAndroid(project)) {
            return;
        }
        project.getConfigurations().named("implementation", c -> c.getDependencies().addAllLater(project.provider(() -> {
            List<Dependency> modules = new ArrayList<>();
            modules.add(module(project, unweird, "unweird-core"));
            if (unweird.getThreeD().get()) {
                modules.add(module(project, unweird, "unweird-3d"));
            }
            if (unweird.getComponents().get()) {
                modules.add(module(project, unweird, "unweird-components"));
            }
            if (unweird.getInstall().get()) {
                modules.add(module(project, unweird, "unweird-install"));
            }
            return modules;
        })));
        processor(project, unweird);

        // unweird's published POM brings the Linux natives; add the ones for this computer
        String natives = nativesClassifier(System.getProperty("os.name"), System.getProperty("os.arch"));
        project.getConfigurations().named("runtimeOnly", c -> {
            for (String module : LWJGL_MODULES) {
                c.getDependencies().add(project.getDependencies().create("org.lwjgl:" + module + ":" + lwjglVersion + ":" + natives));
            }
        });

        project.getTasks().withType(JavaExec.class).configureEach(run -> {
            run.jvmArgs("--enable-native-access=ALL-UNNAMED"); // LWJGL loads native code; newer JDKs warn otherwise
            if (natives.startsWith("natives-macos")) {
                run.jvmArgs("-XstartOnFirstThread"); // GLFW must own the first thread on macOS
            }
        });
    }

    private void android(Project project, UnweirdExtension unweird) {
        project.getConfigurations().named("implementation", c -> c.getDependencies().addAllLater(project.provider(() -> {
            List<Dependency> modules = new ArrayList<>();
            modules.add(module(project, unweird, "unweird-android"));
            if (unweird.getThreeD().get()) {
                modules.add(module(project, unweird, "unweird-3d"));
            }
            if (unweird.getComponents().get()) {
                modules.add(module(project, unweird, "unweird-components"));
            }
            return modules;
        })));
        processor(project, unweird);
        // LWJGL is the desktop backend; Android draws with its own
        project.getConfigurations().configureEach(c -> c.exclude(java.util.Map.of("group", "org.lwjgl")));

        Object android = project.getExtensions().getByName("android");
        // every module adds its own unweird index file; they're combined, not one picked
        Object resources = call(call(android, "getPackaging"), "getResources");
        @SuppressWarnings("unchecked")
        Collection<String> merges = (Collection<String>) call(resources, "getMerges");
        merges.add("META-INF/unweird/**");
        // unweird is compiled for Java 21
        Object compileOptions = call(android, "getCompileOptions");
        call(compileOptions, "setSourceCompatibility", JavaVersion.VERSION_21);
        call(compileOptions, "setTargetCompatibility", JavaVersion.VERSION_21);
    }

    private void processor(Project project, UnweirdExtension unweird) {
        project.getConfigurations().named("annotationProcessor", c -> c.getDependencies().addLater(
                project.provider(() -> module(project, unweird, "unweird-processor"))));
    }

    private static boolean isAndroid(Project project) {
        return project.getPluginManager().hasPlugin("com.android.application") || project.getPluginManager().hasPlugin("com.android.library");
    }

    /** One of unweird's modules: from the included unweird build if there is one, otherwise from JitPack. */
    static Dependency module(Project project, UnweirdExtension unweird, String name) {
        return project.getDependencies().create(coordinates(project.getGradle().getIncludedBuilds().stream()
                .anyMatch(build -> isUnweird(build.getProjectDir())), name, unweird.getVersion().get()));
    }

    /** Whether an included build is a checkout of unweird, whatever its folder is called. */
    static boolean isUnweird(java.io.File buildDir) {
        return new java.io.File(buildDir, "unweird-core/build.gradle.kts").isFile()
                && new java.io.File(buildDir, "unweird-processor").isDirectory();
    }

    static String coordinates(boolean includedBuild, String module, String version) {
        return (includedBuild ? LOCAL_GROUP : PUBLISHED_GROUP) + ":" + module + ":" + version;
    }

    /** LWJGL's natives classifier for an OS and CPU, as Java names them. */
    static String nativesClassifier(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = osArch.toLowerCase(Locale.ROOT);
        boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");
        if (os.contains("win")) {
            return arm64 ? "natives-windows-arm64" : arch.equals("x86") ? "natives-windows-x86" : "natives-windows";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return arm64 ? "natives-macos-arm64" : "natives-macos";
        }
        if (os.contains("linux")) {
            if (arm64) {
                return "natives-linux-arm64";
            }
            return arch.startsWith("arm") ? "natives-linux-arm32" : "natives-linux";
        }
        if (os.contains("freebsd")) {
            return "natives-freebsd";
        }
        throw new GradleException("unweird: no LWJGL natives for " + osName + " (" + osArch + "). "
                + "Linux, Windows and macOS on x64 or ARM work.");
    }

    private static Properties versions() {
        Properties versions = new Properties();
        try (InputStream in = UnweirdPlugin.class.getResourceAsStream("versions.properties")) {
            versions.load(in);
        } catch (IOException | NullPointerException e) {
            throw new GradleException("unweird: the plugin's versions.properties is missing", e);
        }
        return versions;
    }

    /**
     * Calls a getter or setter on the Android extension. Reflection, because the Android plugin's classes may live
     * in another class loader than this plugin.
     */
    private static Object call(Object target, String name, Object... args) {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                try {
                    return method.invoke(target, args);
                } catch (ReflectiveOperationException e) {
                    throw new GradleException("unweird: couldn't set up the Android plugin (" + name + "): " + e.getMessage(), e);
                }
            }
        }
        throw new GradleException("unweird: this version of the Android Gradle plugin has no " + name
                + "; set it in the android { } block yourself");
    }
}
