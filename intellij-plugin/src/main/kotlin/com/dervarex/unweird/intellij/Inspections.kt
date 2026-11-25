package com.dervarex.unweird.intellij

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.compiler.CompilerConfiguration
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil

/** Names of unweird classes the inspections know about. */
internal object Unweird {
    const val PACKAGE = "com.dervarex.unweird"
    const val CONTAINER = "$PACKAGE.Container"
    const val SCREEN = "$PACKAGE.Screen"
    const val COMPONENT = "$PACKAGE.Component"
    const val SHORTCUT = "$PACKAGE.annotation.Shortcut"
    const val DEFAULT = "$PACKAGE.annotation.Default"

    /** Whether unweird is on the classpath of the project at all. */
    fun used(project: Project): Boolean =
        JavaPsiFacade.getInstance(project).findClass(SCREEN, GlobalSearchScope.allScope(project)) != null
}

// ---- @Shortcut("...") ----

/** Shortcut patterns unweird would misread: "Ctl+S" is plain S, "Ctrl+S+D" only D. */
class ShortcutInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : JavaElementVisitor() {
        override fun visitAnnotation(annotation: PsiAnnotation) {
            if (annotation.qualifiedName == Unweird.SHORTCUT) {
                check(annotation.findDeclaredAttributeValue("value") as? PsiLiteralExpression, holder)
            }
        }

        override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
            // Unweird.shortcut("F11", ...) and key.is("Ctrl+S")
            val name = call.methodExpression.referenceName
            if (name != "shortcut" && name != "is") {
                return
            }
            val owner = call.resolveMethod()?.containingClass?.qualifiedName ?: return
            if (owner == "${Unweird.PACKAGE}.Unweird" || owner == "${Unweird.PACKAGE}.KeyPress") {
                check(call.argumentList.expressions.firstOrNull() as? PsiLiteralExpression, holder)
            }
        }
    }

    private fun check(literal: PsiLiteralExpression?, holder: ProblemsHolder) {
        val pattern = literal?.value as? String ?: return
        val problem = ShortcutSyntax.check(pattern) ?: return
        val fixes = problem.suggestion?.let { arrayOf<LocalQuickFix>(ReplaceShortcutFix(it)) } ?: LocalQuickFix.EMPTY_ARRAY
        holder.registerProblem(literal, problem.message, *fixes)
    }

    private class ReplaceShortcutFix(private val pattern: String) : LocalQuickFix {
        override fun getFamilyName() = "Fix the shortcut"
        override fun getName() = "Change to \"$pattern\""

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val literal = descriptor.psiElement as? PsiLiteralExpression ?: return
            val replacement = JavaPsiFacade.getElementFactory(project).createExpressionFromText("\"$pattern\"", literal)
            literal.replace(replacement)
        }
    }
}

// ---- new Text("Hi " + name) ----

/**
 * A widget made from a value that changes, but evaluated only once: {@code new Text("Hi " + name)} keeps showing the
 * first name. unweird re-reads {@code new Text(() -> "Hi " + name)} after every event.
 */
class LiveTextInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : JavaElementVisitor() {
        override fun visitNewExpression(expression: PsiNewExpression) {
            val field = PsiTreeUtil.getParentOfType(expression, PsiField::class.java) ?: return
            if (field.initializer == null || !PsiTreeUtil.isAncestor(field.initializer, expression, false)) {
                return
            }
            val owner = field.containingClass ?: return
            if (!InheritanceUtil.isInheritor(owner, Unweird.CONTAINER)) {
                return
            }
            val type = expression.classReference?.resolve() as? PsiClass ?: return
            if (type.qualifiedName?.startsWith(Unweird.PACKAGE + ".") != true) {
                return
            }
            val arguments = expression.argumentList?.expressions ?: return
            for ((index, argument) in arguments.withIndex()) {
                if (argument.type?.equalsToText(CommonClassNames.JAVA_LANG_STRING) != true || PsiUtil.isConstantExpression(argument)) {
                    continue
                }
                val changing = changingField(argument, owner) ?: continue
                if (!acceptsSupplier(type, index, arguments.size)) {
                    continue
                }
                holder.registerProblem(argument,
                    "Evaluated once: this won't show later changes to '${changing.name}'. unweird re-reads a lambda after every event.",
                    MakeLiveFix())
            }
        }
    }

    /** A field read by {@code expression} whose value can change, or null. */
    private fun changingField(expression: PsiExpression, owner: PsiClass): PsiField? {
        for (reference in PsiTreeUtil.collectElementsOfType(expression, PsiReferenceExpression::class.java)) {
            val field = reference.resolve() as? PsiField ?: continue
            val mutable = !field.hasModifierProperty(PsiModifier.FINAL)
            val calledOn = reference.parent is PsiReferenceExpression && reference.parent.parent is PsiMethodCallExpression
            if ((mutable || calledOn) && (field.containingClass == owner || InheritanceUtil.isInheritorOrSelf(owner, field.containingClass, true))) {
                return field
            }
        }
        return null
    }

    /** Whether {@code type} has a constructor taking a {@code Supplier} at {@code index}. */
    private fun acceptsSupplier(type: PsiClass, index: Int, count: Int): Boolean = type.constructors.any { constructor ->
        val parameters = constructor.parameterList.parameters
        parameters.size == count && (parameters[index].type as? com.intellij.psi.PsiClassType)?.rawType()
            ?.equalsToText("java.util.function.Supplier") == true
    }

    private class MakeLiveFix : LocalQuickFix {
        override fun getFamilyName() = "Make it live: () -> ..."

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val argument = descriptor.psiElement as? PsiExpression ?: return
            val lambda = JavaPsiFacade.getElementFactory(project).createExpressionFromText("() -> " + argument.text, argument)
            JavaCodeStyleManager.getInstance(project).shortenClassReferences(argument.replace(lambda))
        }
    }
}

// ---- screens need a constructor without parameters ----

/** unweird creates screens and {@code @Default} components itself, so they need a constructor without parameters. */
class ConstructorInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : JavaElementVisitor() {
        override fun visitClass(type: PsiClass) {
            if (type.isInterface || type.hasModifierProperty(PsiModifier.ABSTRACT) || type.nameIdentifier == null) {
                return
            }
            val screen = InheritanceUtil.isInheritor(type, Unweird.SCREEN)
            val default = type.hasAnnotation(Unweird.DEFAULT) && InheritanceUtil.isInheritor(type, Unweird.COMPONENT)
            if (!screen && !default) {
                return
            }
            val what = if (screen) "screens" else "@Default components"
            if (type.containingClass != null && !type.hasModifierProperty(PsiModifier.STATIC)) {
                holder.registerProblem(type.nameIdentifier!!,
                    "unweird creates $what itself and can't create an inner class: make it static", MakeStaticFix())
                return
            }
            val constructors = type.constructors
            if (constructors.isNotEmpty() && constructors.none { it.parameterList.isEmpty }) {
                holder.registerProblem(type.nameIdentifier!!,
                    "unweird creates $what itself and needs a constructor without parameters", AddConstructorFix())
            }
        }
    }

    private class MakeStaticFix : LocalQuickFix {
        override fun getFamilyName() = "Make the class static"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            (descriptor.psiElement.parent as? PsiClass)?.modifierList?.setModifierProperty(PsiModifier.STATIC, true)
        }
    }

    private class AddConstructorFix : LocalQuickFix {
        override fun getFamilyName() = "Add a constructor without parameters"

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val type = descriptor.psiElement.parent as? PsiClass ?: return
            val constructor = JavaPsiFacade.getElementFactory(project).createConstructor(type.name!!, type)
            val anchor = type.constructors.firstOrNull()
            if (anchor != null) type.addBefore(constructor, anchor) else type.add(constructor)
        }
    }
}

// ---- the annotation processor ----

/**
 * Screens and components in a module without unweird's annotation processor: their widget fields aren't added, and
 * the app stops with "no field order recorded".
 */
class ProcessorInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : JavaElementVisitor() {
        override fun visitClass(type: PsiClass) {
            val name = type.nameIdentifier ?: return
            if (type.containingClass != null || !InheritanceUtil.isInheritor(type, Unweird.CONTAINER)) {
                return
            }
            val module = ModuleUtilCore.findModuleForPsiElement(type) ?: return
            if (hasProcessor(module)) {
                return
            }
            holder.registerProblem(name,
                "unweird's annotation processor isn't set up for this module, so widget fields won't be added " +
                        "(\"no field order recorded\"). Apply the com.dervarex.unweird Gradle plugin, or add " +
                        "unweird-processor as an annotationProcessor dependency.",
                ProblemHighlightType.WARNING)
        }
    }

    private fun hasProcessor(module: com.intellij.openapi.module.Module): Boolean {
        val config = CompilerConfiguration.getInstance(module.project).getAnnotationProcessingConfiguration(module)
        if (!config.isEnabled) {
            return false
        }
        if (config.processorPath.contains("unweird-processor")) {
            return true
        }
        if (!config.isObtainProcessorsFromClasspath) {
            return false
        }
        return OrderEnumerator.orderEntries(module).recursively().classes().pathsList.pathList
            .any { it.contains("unweird-processor") }
    }
}
