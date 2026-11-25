package com.dervarex.unweird.intellij

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GitNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gradle.service.project.open.linkAndSyncGradleProject
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/** File → New → Project → unweird: a ready-to-run desktop and/or Android app. */
class UnweirdProjectWizard : GeneratorNewProjectWizard {

    override val id = "unweird"
    override val name = "unweird"
    override val icon = UnweirdIcons.Logo
    override val description = "A desktop or Android app made of unweird screens"

    override fun createStep(context: WizardContext): NewProjectWizardStep =
        RootNewProjectWizardStep(context)
            .nextStep(::NewProjectWizardBaseStep)
            .nextStep(::GitNewProjectWizardStep)
            .nextStep(::UnweirdStep)
}

private class UnweirdStep(parent: NewProjectWizardStep) : AbstractNewProjectWizardStep(parent) {

    private val target = propertyGraph.property(Target.DESKTOP)
    private val packageName = propertyGraph.lazyProperty { suggestPackage(baseData?.name ?: "app") }
    private val threeD = propertyGraph.property(false)
    private val components = propertyGraph.property(false)
    private val version = propertyGraph.property(DEFAULT_VERSION)
    private val localCheckout = propertyGraph.property("")

    init {
        baseData?.nameProperty?.afterChange { packageName.set(suggestPackage(it)) }
    }

    override fun setupUI(builder: Panel) {
        with(builder) {
            row("Runs on:") {
                segmentedButton(Target.entries) { text = it.label }.bind(target)
            }
            row("Package:") {
                textField().bindText(packageName).columns(COLUMNS_MEDIUM)
                    .validationOnInput { field ->
                        if (field.text.matches(Regex("[a-zA-Z_][\\w]*(\\.[a-zA-Z_][\\w]*)*"))) null
                        else error("Letters, digits and dots, like com.example.notes")
                    }
            }
            row("Add:") {
                checkBox("3D views").bindSelected(threeD)
                checkBox("Ready-made components").bindSelected(components)
            }
            collapsibleGroup("unweird version") {
                row("Release:") {
                    textField().bindText(version).columns(COLUMNS_MEDIUM)
                        .comment("A tag of github.com/dervarex/unweird, fetched from JitPack")
                }
                row("Or a local checkout:") {
                    textFieldWithBrowseButton(FileChooserDescriptorFactory.createSingleFolderDescriptor()
                        .withTitle("unweird Checkout"), context.project)
                        .bindText(localCheckout).align(AlignX.FILL)
                        .comment("Build against your own copy of unweird (includeBuild) instead of a release")
                }
            }
        }
    }

    override fun setupProject(project: Project) {
        val base = baseData ?: return
        val options = ProjectOptions(
            name = base.name,
            packageName = packageName.get(),
            target = target.get(),
            threeD = threeD.get(),
            components = components.get(),
            version = version.get().trim().ifEmpty { DEFAULT_VERSION },
            localCheckout = localCheckout.get().trim(),
        )
        val root = Path.of(base.path, base.name)
        write(root, options)

        val opened = ProjectFiles.mainFile(options)
        StartupManager.getInstance(project).runAfterOpened {
            project.service<UnweirdCoroutines>().scope.launch {
                withContext(Dispatchers.EDT) {
                    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(root.resolve(opened))
                        ?.let { OpenFileDescriptor(project, it).navigate(true) }
                }
                linkAndSyncGradleProject(project, root.toString())
            }
        }
    }

    private fun write(root: Path, options: ProjectOptions) {
        for ((path, content) in ProjectFiles.generate(options)) {
            val file = root.resolve(path)
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
        }
        // the Gradle wrapper, so ./gradlew works right away
        copyResource("/wrapper/gradle-wrapper.jar", root.resolve("gradle/wrapper/gradle-wrapper.jar"))
        copyResource("/wrapper/gradlew", root.resolve("gradlew"))
        copyResource("/wrapper/gradlew.bat", root.resolve("gradlew.bat"))
        root.resolve("gradlew").toFile().setExecutable(true)
        runWriteAction { VfsUtil.markDirtyAndRefresh(false, true, true, root.toFile()) }
    }

    private fun copyResource(resource: String, target: Path) {
        val input = UnweirdProjectWizard::class.java.getResourceAsStream(resource)
            ?: error("unweird plugin: missing $resource")
        input.use { Files.copy(it, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
    }

    companion object {
        /** "My Notes" → "com.example.mynotes". */
        fun suggestPackage(projectName: String): String {
            val word = projectName.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
            val safe = if (word.isEmpty() || word[0].isDigit()) "app$word" else word
            return "com.example.$safe"
        }
    }
}

/** A coroutine scope that lives as long as the project, for linking the new Gradle project. */
@Service(Service.Level.PROJECT)
internal class UnweirdCoroutines(val scope: CoroutineScope)
