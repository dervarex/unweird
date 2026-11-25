package com.dervarex.unweird.gradle;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnweirdPluginTest {

    private static Project desktop() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply("com.dervarex.unweird");
        return project;
    }

    private static List<String> deps(Project project, String configuration) {
        ((ProjectInternal) project).evaluate();
        return project.getConfigurations().getByName(configuration).getAllDependencies().stream()
                .map(d -> d.getGroup() + ":" + d.getName()).toList();
    }

    @Test
    void desktopGetsCoreAndProcessor() {
        Project project = desktop();
        assertEquals(List.of("com.github.dervarex.unweird:unweird-core"), deps(project, "implementation"));
        assertEquals(List.of("com.github.dervarex.unweird:unweird-processor"), deps(project, "annotationProcessor"));
    }

    @Test
    void optionalModules() {
        Project project = desktop();
        UnweirdExtension unweird = project.getExtensions().getByType(UnweirdExtension.class);
        unweird.getThreeD().set(true);
        unweird.getComponents().set(true);
        List<String> implementation = deps(project, "implementation");
        assertTrue(implementation.contains("com.github.dervarex.unweird:unweird-3d"));
        assertTrue(implementation.contains("com.github.dervarex.unweird:unweird-components"));
        assertFalse(implementation.contains("com.github.dervarex.unweird:unweird-install"));
    }

    @Test
    void nativesForThisComputer() {
        Project project = desktop();
        ((ProjectInternal) project).evaluate();
        List<Dependency> natives = List.copyOf(project.getConfigurations().getByName("runtimeOnly").getDependencies());
        assertEquals(4, natives.size());
        assertTrue(natives.stream().allMatch(d -> "org.lwjgl".equals(d.getGroup())));
    }

    @Test
    void versionCanBeChosen() {
        Project project = desktop();
        project.getExtensions().getByType(UnweirdExtension.class).getVersion().set("v0.1.2");
        ((ProjectInternal) project).evaluate();
        Dependency core = project.getConfigurations().getByName("implementation").getAllDependencies().iterator().next();
        assertEquals("v0.1.2", core.getVersion());
    }

    @Test
    void classifiers() {
        assertEquals("natives-linux", UnweirdPlugin.nativesClassifier("Linux", "amd64"));
        assertEquals("natives-linux-arm64", UnweirdPlugin.nativesClassifier("Linux", "aarch64"));
        assertEquals("natives-windows", UnweirdPlugin.nativesClassifier("Windows 11", "amd64"));
        assertEquals("natives-macos-arm64", UnweirdPlugin.nativesClassifier("Mac OS X", "aarch64"));
        assertEquals("natives-macos", UnweirdPlugin.nativesClassifier("Mac OS X", "x86_64"));
        assertThrows(GradleException.class, () -> UnweirdPlugin.nativesClassifier("Plan 9", "mips"));
    }

    @Test
    void recognizesACheckoutByItsModules(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
        assertFalse(UnweirdPlugin.isUnweird(dir.toFile()));
        java.nio.file.Files.createDirectories(dir.resolve("unweird-core"));
        java.nio.file.Files.createDirectories(dir.resolve("unweird-processor"));
        java.nio.file.Files.writeString(dir.resolve("unweird-core/build.gradle.kts"), "");
        assertTrue(UnweirdPlugin.isUnweird(dir.toFile()));
    }

    @Test
    void includedBuildUsesTheLocalGroup() {
        assertEquals("com.dervarex:unweird-core:1.0", UnweirdPlugin.coordinates(true, "unweird-core", "1.0"));
        assertEquals("com.github.dervarex.unweird:unweird-core:1.0", UnweirdPlugin.coordinates(false, "unweird-core", "1.0"));
    }
}
