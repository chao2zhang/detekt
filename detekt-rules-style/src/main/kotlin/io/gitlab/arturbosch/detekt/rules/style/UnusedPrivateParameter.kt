package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.DetektVisitor
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import io.gitlab.arturbosch.detekt.api.internal.Configuration
import io.gitlab.arturbosch.detekt.api.internal.config
import io.gitlab.arturbosch.detekt.rules.isAbstract
import io.gitlab.arturbosch.detekt.rules.isExpect
import io.gitlab.arturbosch.detekt.rules.isExternal
import io.gitlab.arturbosch.detekt.rules.isMainFunction
import io.gitlab.arturbosch.detekt.rules.isOpen
import io.gitlab.arturbosch.detekt.rules.isOperator
import io.gitlab.arturbosch.detekt.rules.isOverride
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression

/**
 * Reports unused function parameters.
 * If these private elements are unused they should be removed. Otherwise this dead code
 * can lead to confusion and potential bugs.
 */
class UnusedPrivateParameter(config: Config = Config.empty) : Rule(config) {

    override val defaultRuleIdAliases: Set<String> = setOf("UNUSED_VARIABLE", "UNUSED_PARAMETER", "unused")

    override val issue: Issue = Issue("UnusedPrivateMember",
        Severity.Maintainability,
        "Private member is unused.",
        Debt.FIVE_MINS)

    @Configuration("Unused parameter names matching this regex are ignored")
    private val allowedNames by config("(_|ignored|expected)".toRegex())

    override fun visit(root: KtFile) {
        super.visit(root)
        val visitor = UnusedParameterVisitor(allowedNames)
        root.accept(visitor)
        visitor.getUnusedReports(issue).forEach { report(it) }
    }
}


private class UnusedParameterVisitor(private val allowedNames: Regex): DetektVisitor() {

    private var unusedParameters: MutableSet<KtParameter> = mutableSetOf()

    fun getUnusedReports(issue: Issue): List<CodeSmell> {
        return unusedParameters.map {
            CodeSmell(issue, Entity.from(it), "Function parameter ${it.nameAsSafeName.identifier} is unused.")
        }
    }

    override fun visitClassOrObject(klassOrObject: KtClassOrObject) {
        if (klassOrObject.isExpect()) return
        super.visitClassOrObject(klassOrObject)
    }

    override fun visitClass(klass: KtClass) {
        if (klass.isInterface()) return
        super.visitClass(klass)
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        if (function.isAllowedToHaveUnusedParameters()) {
            return
        }
        collectParameters(function)
        super.visitNamedFunction(function)
    }

    private fun collectParameters(function: KtNamedFunction) {
        val parameters = mutableMapOf<String, KtParameter>()
        function.valueParameterList?.parameters?.forEach { parameter ->
            val name = parameter.nameAsSafeName.identifier
            if (!allowedNames.matches(name)) {
                parameters[name] = parameter
            }
        }

        function.accept(object : DetektVisitor() {
            override fun visitProperty(property: KtProperty) {
                if (property.isLocal) {
                    val name = property.nameAsSafeName.identifier
                    parameters.remove(name)
                }
                super.visitProperty(property)
            }

            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                parameters.remove(expression.text)
                super.visitReferenceExpression(expression)
            }
        })

        unusedParameters.addAll(parameters.values)
    }

    private fun KtNamedFunction.isAllowedToHaveUnusedParameters() =
        isAbstract() || isOpen() || isOverride() || isOperator() || isMainFunction() || isExternal() || isExpect()
}
