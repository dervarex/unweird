package com.dervarex.unweird.intellij

import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.PsiDirectory
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes

/** New → unweird Class: a Screen, Component, Dialog or Widget, from the file templates in fileTemplates/internal. */
class NewUnweirdClassAction : CreateFileFromTemplateAction(), DumbAware {

    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder.setTitle("New unweird Class")
            .addKind("Screen", UnweirdIcons.Logo, "unweird Screen")
            .addKind("Component", UnweirdIcons.Logo, "unweird Component")
            .addKind("Dialog", UnweirdIcons.Logo, "unweird Dialog")
            .addKind("Widget (draws itself)", UnweirdIcons.Logo, "unweird Widget")
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String) = "Create unweird $newName"

    /** Only in Java source folders of projects that use unweird. */
    override fun isAvailable(dataContext: DataContext): Boolean {
        if (!super.isAvailable(dataContext)) {
            return false
        }
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
        val view = LangDataKeys.IDE_VIEW.getData(dataContext) ?: return false
        val index = ProjectRootManager.getInstance(project).fileIndex
        val inSources = view.directories.any { index.isUnderSourceRootOfType(it.virtualFile, JavaModuleSourceRootTypes.SOURCES) }
        return inSources && (DumbService.isDumb(project) || Unweird.used(project))
    }
}
