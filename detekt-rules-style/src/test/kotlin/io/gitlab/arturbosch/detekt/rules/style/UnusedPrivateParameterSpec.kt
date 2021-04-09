package io.gitlab.arturbosch.detekt.rules.style

import io.gitlab.arturbosch.detekt.test.assertThat
import io.gitlab.arturbosch.detekt.test.lint
import org.assertj.core.api.Assertions.assertThat
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class UnusedPrivateParameterSpec : Spek({

    val subject by memoized { UnusedPrivateParameter() }

    describe("interface functions") {

        it("should not report parameters in interface functions") {
            val code = """
                interface UserPlugin {
                    fun plug(application: Application)
                    fun unplug()
                }
            """
            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("expect functions") {

        it("should not report parameters in expect class functions") {
            val code = """
                expect class Foo {
                    fun bar(i: Int)
                    fun baz(i: Int, s: String)
                }
            """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("should not report parameters in expect object functions") {
            val code = """
                expect object Foo {
                    fun bar(i: Int)
                    fun baz(i: Int, s: String)
                }
            """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("should not report parameters in expect functions") {
            val code = """
                expect fun bar(i: Int)
                expect fun baz(i: Int, s: String)
            """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("should not report parameters in expect class with constructor") {
            val code = """
                expect class Foo1(private val bar: String) {}
                expect class Foo2(bar: String) {}
            """
            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("actual functions") {

        it("reports unused parameters in actual functions") {
            val code = """
                actual class Foo {
                    actual fun bar(i: Int) {}
                    actual fun baz(i: Int, s: String) {}
                }
            """
            assertThat(subject.lint(code)).hasSize(3)
        }

        it("reports unused parameters in constructors") {
            val code = """
                actual class Foo actual constructor(private val bar: String) {}
            """
            assertThat(subject.lint(code)).hasSize(1)
        }
    }

    describe("external functions") {

        it("should not report parameters in external functions") {
            val code = "external fun foo(bar: String)"
            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("overridden functions") {

        it("should not report parameters in not private functions") {
            val code = """
                override fun funA() {
                    objectA.resolve(valA, object : MyCallback {
                        override fun onResolveFailed(throwable: Throwable) {
                            errorMessage.visibility = View.VISIBLE
                        }
                    })
                }
            """
            assertThat(subject.lint(code)).isEmpty()
        }
    }


    describe("function parameters") {
        it("reports single parameters if they are unused") {
            val code = """
            class Test {
                val value = usedMethod(1)

                private fun usedMethod(unusedParameter: Int): Int {
                    return 5
                }
            }
            """

            assertThat(subject.lint(code)).hasSize(1)
        }

        it("reports two parameters if they are unused and called the same in different methods") {
            val code = """
            class Test {
                val value = usedMethod(1)
                val value2 = usedMethod2(1)

                private fun usedMethod(unusedParameter: Int): Int {
                    return 5
                }

                private fun usedMethod2(unusedParameter: Int) {
                    return 5
                }
            }
            """

            assertThat(subject.lint(code)).hasSize(2)
        }

        it("does not report single parameters if they used in return statement") {
            val code = """
            class Test {
                val value = usedMethod(1)

                private fun usedMethod(used: Int): Int {
                    return used
                }
            }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report single parameters if they used in function") {
            val code = """
            class Test {
                val value = usedMethod(1)

                private fun usedMethod(used: Int) {
                    println(used)
                }
            }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("reports parameters that are unused in return statement") {
            val code = """
            class Test {
                val value = usedMethod(1, 2)

                private fun usedMethod(unusedParameter: Int, usedParameter: Int): Int {
                    return usedParameter
                }
            }
            """

            assertThat(subject.lint(code)).hasSize(1)
        }

        it("reports parameters that are unused in function") {
            val code = """
            class Test {
                val value = usedMethod(1, 2)

                private fun usedMethod(unusedParameter: Int, usedParameter: Int) {
                    println(usedParameter)
                }
            }
            """

            assertThat(subject.lint(code)).hasSize(1)
        }
    }

    describe("top level function parameters") {
        it("reports single parameters if they are unused") {
            val code = """
            fun function(unusedParameter: Int): Int {
                return 5
            }
            """

            assertThat(subject.lint(code)).hasSize(1)
        }

        it("does not report single parameters if they used in return statement") {
            val code = """
            fun function(used: Int): Int {
                return used
            }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report single parameters if they used in function") {
            val code = """
            fun function(used: Int) {
                println(used)
            }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("reports parameters that are unused in return statement") {
            val code = """
            fun function(unusedParameter: Int, usedParameter: Int): Int {
                return usedParameter
            }
            """

            assertThat(subject.lint(code)).hasSize(1)
        }

        it("reports parameters that are unused in function") {
            val code = """
            fun function(unusedParameter: Int, usedParameter: Int) {
                println(usedParameter)
            }
            """

            assertThat(subject.lint(code)).hasSize(1)
        }
    }

    describe("parameters in primary constructors") {
        it("reports unused private property") {
            val code = """
                class Test(private val unused: Any)
                """
            assertThat(subject.lint(code)).hasSize(1)
        }

        it("reports unused parameter") {
            val code = """
                class Test(unused: Any)
                """
            assertThat(subject.lint(code)).hasSize(1)
        }

        it("does not report used parameter for calling super") {
            val code = """
                class Parent(val ignored: Any)
                class Test(used: Any) : Parent(used)
                """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report used parameter in init block") {
            val code = """
                class Test(used: Any) {
                    init {
                        used.toString()
                    }
                }
                """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report used parameter to initialize property") {
            val code = """
                class Test(used: Any) {
                    val usedString = used.toString()
                }
                """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report public property") {
            val code = """
                class Test(val unused: Any)
                """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report private property used in init block") {
            val code = """
                class Test(private val used: Any) {
                    init { used.toString() }
                }
                """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report private property used in function") {
            val code = """
                class Test(private val used: Any) {
                    fun something() {
                        used.toString()
                    }
                }
                """
            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("error messages") {
        val code = """
                fun foo(unused: Int){}
            """

        val lint = subject.lint(code)

        assertThat(lint.first().message).startsWith("Function parameter")
    }

    describe("suppress unused parameter warning annotations") {
        it("does not report annotated parameters") {
            val code = """
                fun foo(@Suppress("UNUSED_PARAMETER") unused: String){}
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("reports parameters without annotation") {
            val code = """
                fun foo(@Suppress("UNUSED_PARAMETER") unused: String, unusedWithoutAnnotation: String){}
            """

            val lint = subject.lint(code)

            assertThat(lint).hasSize(1)
            assertThat(lint[0].entity.signature).isEqualTo("Test.kt\$unusedWithoutAnnotation: String")
        }

        it("does not report parameters in annotated function") {
            val code = """
                @Suppress("UNUSED_PARAMETER")
                fun foo(unused: String, otherUnused: String){}
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report parameters in annotated class") {
            val code = """
                @Suppress("UNUSED_PARAMETER")
                class Test {
                    fun foo(unused: String, otherUnused: String){}
                    fun bar(unused: String){}
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report parameters in annotated object") {
            val code = """
                @Suppress("UNUSED_PARAMETER")
                object Test {
                    fun foo(unused: String){}
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report parameters in class with annotated outer class") {
            val code = """
                @Suppress("UNUSED_PARAMETER")
                class Test {
                    fun foo(unused: String){}

                    class InnerTest {
                        fun bar(unused: String){}
                    }
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report parameters in annotated file") {
            val code = """
                @file:Suppress("UNUSED_PARAMETER")

                class Test {
                    fun foo(unused: String){}

                    class InnerTest {
                        fun bar(unused: String){}
                    }
                }
            """

            assertThat(subject.lint(code)).isEmpty()
        }
    }

    describe("main methods") {

        it("does not report the args parameter of the main function inside an object") {
            val code = """
                object O {

                    @JvmStatic
                    fun main(args: Array<String>) {
                        println("b")
                    }
                }
            """
            assertThat(subject.lint(code)).isEmpty()
        }

        it("does not report the args parameter of the main function as top level function") {
            val code = """
                fun main(args: Array<String>) {
                    println("b")
                }
            """
            assertThat(subject.lint(code)).isEmpty()
        }
    }
})
