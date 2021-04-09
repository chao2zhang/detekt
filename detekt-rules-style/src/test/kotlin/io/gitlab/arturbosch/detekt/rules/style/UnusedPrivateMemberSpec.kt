package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.api.SourceLocation
import io.gitlab.arturbosch.detekt.rules.Case
import io.gitlab.arturbosch.detekt.rules.setupKotlinEnvironment
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import io.gitlab.arturbosch.detekt.test.lint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.regex.PatternSyntaxException

class UnusedPrivateMemberSpec : Spek({
    setupKotlinEnvironment()

    val env: KotlinCoreEnvironment by memoized()
    val subject by memoized { UnusedPrivateMember() }

    val regexTestingCode = """
                class Test {
                    private val used = "This is used"
                    private val unused = "This is not used"

                    fun use() {
                        println(used)
                    }
                }
                """

    describe("a large chunk of code with different findings") {

        it("a positive chunk of code") {
            val code = """
                // reports 1 violation for every unused* element
                class UnusedPrivateMemberPositive {
                    private val unusedField = 5
                    val publicField = 2
                    private val clashingName = 4
                    private fun unusedFunction(someParam: Int) {
                        val unusedLocal = 5
                    }
                }

                object UnusedPrivateMemberPositiveObject {
                    private const val unusedObjectConst = 2
                    private val unusedField = 5
                    private val clashingName = 5
                    val useForClashingName = clashingName
                    private val unusedObjectField = 4

                    object Foo {
                        private val unusedNestedVal = 1
                    }
                }

                private fun unusedTopLevelFunction() = 5

                private val usedTopLevelVal = 1
                private const val unusedTopLevelConst = 1
                private val unusedTopLevelVal = usedTopLevelVal

                private class ClassWithSecondaryConstructor {
                    constructor(used: Any, unused: Any) {
                        used.toString()
                    }

                    // this is actually unused, but clashes with the other constructor
                    constructor(used: Any)
                }

                fun main(args: Array<String>) {
                    println("")
                }

                private fun unusedAndNotCalledFromMain() {} // unused
            """.trimIndent()
            assertThat(subject.lint(code)).hasSize(12)
        }

        it("a negative chunk of code") {
            val code = """
                package cases

                import kotlin.reflect.KProperty

                object O { // public
                    const val NUMBER = 5 // public
                }

                private object PO { // private, but constants may be used
                    const val TEXT = "text"
                }

                class C {
                    val myNumber = 5

                    fun publicFunction(usedParam: String) {
                        println(usedParam)
                        println(PC.THE_CONST)
                        println("Hello " ext "World" ext "!")
                        println(::doubleColonObjectReferenced)
                        println(this::doubleColonThisReferenced)
                    }

                    fun usesAllowedNames() {
                        for ((index, _) in mapOf(0 to 0, 1 to 1, 2 to 2)) {  // unused but allowed name
                            println(index)
                        }
                        try {
                        } catch (_: OutOfMemoryError) { // unused but allowed name
                        }
                    }

                    private fun doubleColonThisReferenced() {}

                    companion object {
                        private infix fun String.ext(other: String): String {
                            return this + other
                        }

                        private fun doubleColonObjectReferenced() {}
                    }
                }

                private class PC { // used private class
                    companion object {
                        internal const val THE_CONST = "" // used private const

                        object OO {
                            const val BLA = 4
                        }
                    }
                }

                internal fun libraryFunction() = run {
                    val o: Function1<Any, Any> = object : Function1<Any, Any> {
                        override fun invoke(p1: Any): Any { // unused but overridden param
                            throw UnsupportedOperationException("not implemented")
                        }
                    }
                    println(o("$\{PC.Companion.OO.BLA.toString() + ""}"))
                }

                internal class IC // unused but internal

                val stuff = object : Iterator<String?> {

                    var mutatable: String? = null

                    private fun preCall() {
                        mutatable = "done"
                    }

                    override fun next(): String? {
                        preCall()
                        return mutatable
                    }

                    override fun hasNext(): Boolean = true
                }

                fun main(args: Array<String>) {
                    println(stuff.next())
                    calledFromMain()
                }

                private fun calledFromMain() {}

                abstract class Parent {
                    abstract fun abstractFun(arg: Any)
                    open fun openFun(arg: Any): Int = 0
                }

                class Child : Parent() {
                    override fun abstractFun(arg: Any) {
                        println(arg)
                    }

                    override fun openFun(arg: Any): Int {
                        println(arg)
                        return 1
                    }
                }

                class SingleAssign<String> {

                    // ignore unused operator function parameters
                    operator fun getValue(thisRef: Any?, property: KProperty<*>): kotlin.String {
                        return ""
                    }
                }
            """.trimIndent()
            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("classes accessing constants from companion objects") {

        it("should not report used constants") {
            val code = """
                class A {
                    companion object {
                        private const val MY_CONST = 42
                    }

                    fun a() {
                        Completable.timer(MY_CONST.toLong(), TimeUnit.MILLISECONDS)
                                .subscribe()
                    }
                }
            """
            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("several classes with properties") {

        it("reports an unused member") {
            val code = """
                class Test {
                    private val unused = "This is not used"

                    fun use() {
                        println("This is not using a property")
                    }
                }
                """
            assertThat(subject.lint(code)).hasSize(1)
        }

        it("does not report unused public members") {
            val code = """
                class Test {
                    val unused = "This is not used"

                    fun use() {
                        println("This is not using a property")
                    }
                }
                """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report used members") {
            val code = """
                class Test {
                    private val used = "This is used"

                    fun use() {
                        println(used)
                    }
                }
                """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report used members but reports unused members") {
            val code = """
                class Test {
                    private val used = "This is used"
                    private val unused = "This is not used"

                    fun use() {
                        println(used)
                    }
                }
                """
            assertThat(subject.lint(code)).hasSize(1)
        }

        it("does not fail when disabled with invalid regex") {
            val configRules = mapOf(
                "active" to "false",
                UnusedPrivateMember.ALLOWED_NAMES_PATTERN to "*foo"
            )
            val config = TestConfig(configRules)
            assertThat(UnusedPrivateMember(config).lint(regexTestingCode)).isEmpty()
        }

        it("does fail when enabled with invalid regex") {
            val configRules = mapOf(UnusedPrivateMember.ALLOWED_NAMES_PATTERN to "*foo")
            val config = TestConfig(configRules)
            assertThatExceptionOfType(PatternSyntaxException::class.java)
                .isThrownBy { UnusedPrivateMember(config).lint(regexTestingCode) }
        }
    }

    describe("several classes with properties and local properties") {

        it("reports an unused member") {
            val code = """
                class Test {
                    private val unused = "This is not used"

                    fun use() {
                        val used = "This is used"
                        println(used)
                    }
                }
                """
            assertThat(subject.lint(code)).hasSize(1)
        }

        it("does not report used members") {
            val code = """
                class Test {
                    private val used = "This is used"

                    fun use() {
                        val text = used
                        println(text)
                    }
                }
                """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("reports unused local properties") {
            val code = """
                class Test {
                    private val used = "This is used"

                    fun use() {
                        val unused = used
                        println(used)
                    }
                }
                """
            assertThat(subject.lint(code)).hasSize(1)
        }
    }

    describe("loop iterators") {

        it("should not depend on evaluation order of functions or properties") {
            val code = """
                fun RuleSetProvider.provided() = ruleSetId in defaultRuleSetIds

                val defaultRuleSetIds = listOf("comments", "complexity", "empty-blocks",
                        "exceptions", "potential-bugs", "performance", "style")
            """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("doesn't report loop properties") {
            val code = """
                class Test {
                    fun use() {
                        for (i in 0 until 10) {
                            println(i)
                        }
                    }
                }
                """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("reports unused loop property") {
            val code = """
                class Test {
                    fun use() {
                        for (i in 0 until 10) {
                        }
                    }
                }
                """
            assertThat(subject.lint(code)).hasSize(1)
        }

        it("reports unused loop property in indexed array") {
            val code = """
                class Test {
                    fun use() {
                        val array = intArrayOf(1, 2, 3)
                        for ((index, value) in array.withIndex()) {
                            println(index)
                        }
                    }
                }
                """
            assertThat(subject.lint(code)).hasSize(1)
        }

        it("reports all unused loop properties in indexed array") {
            val code = """
                class Test {
                    fun use() {
                        val array = intArrayOf(1, 2, 3)
                        for ((index, value) in array.withIndex()) {
                        }
                    }
                }
                """
            assertThat(subject.lint(code)).hasSize(2)
        }

        it("does not report used loop properties in indexed array") {
            val code = """
                class Test {
                    fun use() {
                        val array = intArrayOf(1, 2, 3)
                        for ((index, value) in array.withIndex()) {
                            println(index)
                            println(value)
                        }
                    }
                }
                """
            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("properties used to initialize other properties") {

        it("does not report properties used by other properties") {
            val code = """
                class Test {
                    private val used = "This is used"
                    private val text = used

                    fun use() {
                        println(text)
                    }
                }
                """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report properties used by inner classes") {
            val code = """
                class Test {
                    private val unused = "This is not used"

                    inner class Something {
                        val test = unused
                    }
                }
                """
            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("unused private functions") {
        it("does not report used private functions") {
            val code = """
            class Test {
                val value = usedMethod()

                private fun usedMethod(): Int {
                    return 5
                }
            }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("reports unused private functions") {
            val code = """
            class Test {
                private fun unusedFunction(): Int {
                    return 5
                }
            }
            """

            assertThat(subject.lint(code)).hasSize(1)
        }

        it("does not report function used in interface - #1613") {
            val code = """
                interface Bar {
                    fun doSomething() {
                        doSomethingElse()
                    }
                }
                private fun doSomethingElse() {}
            """

            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("private functions only used by unused private functions") {

        it("reports the non called private function") {
            val code = """
            class Test {
                private fun unusedFunction(): Int {
                    return someOtherUnusedFunction()
                }

                private fun someOtherUnusedFunction() {
                    println("Never used")
                }
            }
            """

            assertThat(subject.lint(code)).hasSize(1)
        }
    }

    describe("unused class declarations which are allowed") {

        it("does not report the unused private property") {
            val code = """
                class Test {
                    private val ignored = ""
                }"""
            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report the unused private function") {
            val code = """
                class Test {
                    private fun ignored(ignored: Int) {}
                }"""
            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("nested class declarations") {

        it("reports unused nested private property") {
            val code = """
                class Test {
                    class Inner {
                        private val unused = 1
                    }
                }"""
            assertThat(subject.lint(code)).hasSize(1)
        }

        it("does not report used nested private property") {
            val code = """
                class Test {
                    class Inner {
                        private val used = 1
                        fun someFunction() = used
                    }
                }"""
            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("error messages") {

        it("are specific for local variables") {
            val code = """
                fun foo(){ val unused = 1 }
            """

            val lint = subject.lint(code)

            assertThat(lint.first().message).startsWith("Private property")
        }

        it("are specific for private functions") {
            val code = """
            class Test {
                private fun unusedFunction(): Int {
                    return 5
                }
            }
            """

            val lint = subject.lint(code)

            assertThat(lint.first().message).startsWith("Private function")
        }
    }

    describe("suppress unused property warning annotations") {
        it("does not report annotated private constructor properties") {
            val code = """
                class Test(@Suppress("unused") private val foo: String) {}
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("reports private constructor properties without annotation") {
            val code = """
                class Test(
                    @Suppress("unused") private val foo: String,
                    private val bar: String
                ) {}
            """

            val lint = subject.lint(code)

            assertThat(lint).hasSize(1)
            assertThat(lint[0].entity.signature).isEqualTo("Test.kt\$Test\$private val bar: String")
        }

        it("does not report private constructor properties in annotated class") {
            val code = """
                @Suppress("unused")
                class Test(
                    private val foo: String,
                    private val bar: String
                ) {}
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report private constructor properties in class with annotated outer class") {
            val code = """
                @Suppress("unused")
                class Test(
                    private val foo: String,
                    private val bar: String
                ) {
                    class InnerTest(
                        private val baz: String
                    ) {}
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report private constructor properties in annotated file") {
            val code = """
                @file:Suppress("unused")

                class Test(
                    private val foo: String,
                    private val bar: String
                ) {
                    class InnerTest(
                        private val baz: String
                    ) {}
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report annotated private properties") {
            val code = """
                class Test {
                    @Suppress("unused") private val foo: String
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("reports private properties without annotation") {
            val code = """
                class Test {
                    @Suppress("unused") private val foo: String
                    private val bar: String
                }
            """

            val lint = subject.lint(code)

            assertThat(lint).hasSize(1)
            assertThat(lint[0].entity.signature).isEqualTo("Test.kt\$Test\$private val bar: String")
        }

        it("does not report private properties in annotated class") {
            val code = """
                @Suppress("unused")
                class Test {
                    private val foo: String
                    private val bar: String
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report private properties in class with annotated outer class") {
            val code = """
                @Suppress("unused")
                class Test {
                    private val foo: String
                    private val bar: String

                    class InnerTest {
                        private val baz: String
                    }
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report private properties in annotated file") {
            val code = """
                @file:Suppress("unused")

                class Test {
                    private val foo: String
                    private val bar: String

                    class InnerTest {
                        private val baz: String
                    }
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("suppress unused function warning annotations") {
        it("does not report annotated private functions") {
            val code = """
                @Suppress("unused")
                private fun foo(): String = ""
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("reports private functions without annotation") {
            val code = """
                private fun foo(): String = ""
            """

            val findings = subject.lint(code)

            assertThat(findings).hasSize(1)
            assertThat(findings[0].entity.signature).isEqualTo("Test.kt\$private fun foo(): String")
        }

        it("does not report private functions in annotated class") {
            val code = """
                @Suppress("unused")
                class Test {
                    private fun foo(): String = ""
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report private functions in class with annotated outer class") {
            val code = """
                @Suppress("unused")
                class Test {
                    private fun foo(): String = ""
                    private fun bar(): String = ""

                    class InnerTest {
                        private fun baz(): String = ""
                    }
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report private functions in annotated file") {
            val code = """
                @file:Suppress("unused")
                class Test {
                    private fun foo(): String = ""
                    private fun bar(): String = ""

                    class InnerTest {
                        private fun baz(): String = ""
                    }
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("operators") {

        it("does not report used plus operator - #1354") {
            val code = """
                import java.util.Date
                class Foo {
                    val bla: Date = Date(System.currentTimeMillis()) + 300L
                    companion object {
                        private operator fun Date.plus(diff: Long): Date = Date(this.time + diff)
                    }
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        it("does not report used operator methods when used with the equal sign") {
            val code = """
                class Test {
                    fun f() {
                        var number: Int? = 0
                        number += 1
                        number -= 1
                        number *= 1
                        number /= 1
                        number %= 1
                    }
                    private operator fun Int?.plus(other: Int) = 1
                    private operator fun Int?.minus(other: Int) = 2
                    private operator fun Int?.times(other: Int) = 3
                    private operator fun Int?.div(other: Int) = 4
                    private operator fun Int?.rem(other: Int) = 5
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).isEmpty()
        }

        it("report unused minus operator") {
            val code = """
                import java.util.Date
                class Foo {
                    companion object {
                        private operator fun Date.minus(diff: Long): Date = Date(this.time - diff)
                    }
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(1)
        }
    }

    describe("same named functions") {

        it("report it when the file has same named functions") {
            val code = """
                class Test {
                    private fun f(): Int {
                        return 5
                    }
                }

                class Test2 {
                    private fun f(): Int {
                        return 5
                    }
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(2)
        }

        it("report it when the class has same named functions") {
            val code = """
                class Test {
                    val value = f(1)

                    private fun f(): Int {
                        return 5
                    }

                    private fun f(num: Int): Int {
                        return num
                    }

                    private fun f(num: String): Int {
                        return num.toInt()
                    }
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(2)
        }

        it("report it when the class has same named extension functions") {
            val code = """
                class Test {
                    val value = 1.f()
                
                    private fun f(): Int {
                        return 5
                    }
                
                    private fun Int.f(): Int {
                        return this
                    }
                
                    private fun String.f(): Int {
                        return toInt()
                    }
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(2)
        }
    }

    describe("operator functions - #2579") {

        it("Does not report unused operators") {
            val code = """
                class Test {
                    private operator fun Foo.plus(other: Foo): Foo = Foo(value + other.value)
                
                    inner class Foo(val value: Int) {
                        fun double(): Foo = this + this
                    }
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(0)
        }

        it("Report unused operators") {
            val code = """
                class Test {
                    private operator fun Foo.plus(other: Foo): Foo = Foo(value + other.value)
                    private operator fun Foo.minus(other: Foo): Foo = Foo(value - other.value)
                
                    inner class Foo(val value: Int) {
                        fun double(): Foo = this + this
                    }
                }
            """
            val findings = subject.compileAndLintWithContext(env, code)
            assertThat(findings).hasSize(1).hasSourceLocations(
                SourceLocation(3, 5)
            )
        }
    }

    describe("overloaded extension functions - #2579") {

        it("Does not report used private extension functions") {
            val code = """
                class A
                class B
                class C(val elements: Set<B>, val flag: Boolean)
                
                class Test {
                    private fun A.someMethod(
                          param1: B,
                          param2: Boolean = true
                      ) = someMethod(setOf(param1), param2)
                    
                    private fun A.someMethod(
                          param1: Set<B>,
                          param2: Boolean = true
                      ) = C(param1, param2)
                    
                    fun main() {
                        val aInstance = A()
                        aInstance.someMethod(B(), true)
                        aInstance.someMethod(setOf(B(), B()), false)
                    }
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(0)
        }
    }

    describe("getValue/setValue operator functions - #3128") {

        it("does not report used private getValue/setValue operator functions") {
            val code = """
                import kotlin.reflect.KProperty
                
                class Test {
                    var delegated by "Hello"

                    private operator fun String.getValue(test: Test, prop: KProperty<*>): String {
                        return "working"
                    }
                    
                    private operator fun String.setValue(test: Test, prop: KProperty<*>, value: String) {
                        error("setValue")
                    }
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(0)
        }

        it("reports unused private getValue/setValue operator functions") {
            val code = """
                import kotlin.reflect.KProperty
                
                class Test {
                    private operator fun String.getValue(test: Test, prop: KProperty<*>): String {
                        return "working"
                    }
                    
                    private operator fun String.setValue(test: Test, prop: KProperty<*>, value: String) {
                        error("setValue")
                    }
                }
            """
            assertThat(subject.compileAndLintWithContext(env, code)).hasSize(2)
        }
    }
})
