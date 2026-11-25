package com.dervarex.unweird.intellij

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFilesTest {

    private fun files(target: Target, local: String = "", threeD: Boolean = false) =
        ProjectFiles.generate(ProjectOptions("My Notes", "com.example.notes", target, threeD = threeD, localCheckout = local))

    @Test
    fun desktopIsOneModule() {
        val files = files(Target.DESKTOP)
        assertTrue(files.keys.containsAll(listOf("settings.gradle.kts", "build.gradle.kts",
            "src/main/java/com/example/notes/Main.java", "src/main/java/com/example/notes/HomeScreen.java")))
        assertTrue(files.getValue("build.gradle.kts").contains("id(\"com.dervarex.unweird\") version \"$DEFAULT_VERSION\""))
        assertTrue(files.getValue("build.gradle.kts").contains("mainClass = \"com.example.notes.Main\""))
        assertTrue(files.getValue("src/main/java/com/example/notes/Main.java").contains("Unweird.app(\"My Notes\")"))
        assertTrue(files.getValue("settings.gradle.kts").contains("jitpack"))
        assertFalse(files.getValue("settings.gradle.kts").contains("google()"))
    }

    @Test
    fun androidGetsAnAppModuleAndActivity() {
        val files = files(Target.ANDROID)
        assertTrue(files.getValue("settings.gradle.kts").contains("include(\"app\")"))
        assertTrue(files.getValue("settings.gradle.kts").contains("google()"))
        assertTrue(files.getValue("app/build.gradle.kts").contains("id(\"com.android.application\")"))
        assertTrue(files.getValue("app/src/main/java/com/example/notes/Main.java").contains("extends UnweirdActivity<HomeScreen>"))
        assertTrue(files.getValue("app/src/main/AndroidManifest.xml").contains("android:label=\"My Notes\""))
    }

    @Test
    fun bothShareTheScreens() {
        val files = files(Target.BOTH)
        assertTrue(files.containsKey("shared/src/main/java/com/example/notes/HomeScreen.java"))
        assertTrue(files.getValue("desktop/src/main/java/com/example/notes/desktop/Main.java").contains("import com.example.notes.HomeScreen;"))
        assertTrue(files.getValue("android/src/main/java/com/example/notes/android/Main.java").contains("import com.example.notes.HomeScreen;"))
        assertTrue(files.getValue("desktop/build.gradle.kts").contains("implementation(project(\":shared\"))"))
        assertTrue(files.getValue("android/build.gradle.kts").contains("implementation(project(\":shared\"))"))
        assertEquals("shared/src/main/java/com/example/notes/HomeScreen.java",
            ProjectFiles.mainFile(ProjectOptions("x", "com.example.notes", Target.BOTH)))
    }

    @Test
    fun localCheckoutIsAnIncludedBuild() {
        val files = files(Target.DESKTOP, local = "/home/me/unweird")
        val settings = files.getValue("settings.gradle.kts")
        assertEquals(2, Regex("includeBuild\\(\"/home/me/unweird\"\\)").findAll(settings).count())
        assertFalse(settings.contains("jitpack"))
        assertTrue(files.getValue("build.gradle.kts").contains("id(\"com.dervarex.unweird\")\n"))
    }

    @Test
    fun optionalModules() {
        assertTrue(files(Target.DESKTOP, threeD = true).getValue("build.gradle.kts").contains("threeD = true"))
        assertFalse(files(Target.DESKTOP).getValue("build.gradle.kts").contains("unweird {"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadPackages() {
        ProjectOptions("x", "com.example.my-app", Target.DESKTOP)
    }

    @Test
    fun quotesInNamesAreEscaped() {
        val files = ProjectFiles.generate(ProjectOptions("Say \"hi\"", "com.example.hi", Target.ANDROID))
        assertTrue(files.getValue("app/src/main/java/com/example/hi/HomeScreen.java").contains("new Text(\"Say \\\"hi\\\"\")"))
        assertTrue(files.getValue("app/src/main/AndroidManifest.xml").contains("Say &quot;hi&quot;"))
    }
}
