package io.gitlab.arturbosch.detekt

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.internal.DetektAndroid
import io.gitlab.arturbosch.detekt.internal.DetektJvm
import io.gitlab.arturbosch.detekt.internal.DetektPlain
import org.gradle.api.Plugin
import org.gradle.api.Project

class DetektPlugin : Plugin<Project> {

    override fun apply(project: Project) {
    }

    private fun Project.registerDetektJvmTasks(extension: DetektExtension) {
        plugins.withId("org.jetbrains.kotlin.jvm") {
            DetektJvm(this).registerTasks(extension)
        }
    }

    private fun Project.registerDetektAndroidTasks(extension: DetektExtension) {
        plugins.withId("kotlin-android") {
            DetektAndroid(this).registerTasks(extension)
        }
    }

    private fun Project.registerDetektPlainTask(extension: DetektExtension) {
        DetektPlain(this).registerTasks(extension)
    }

    private fun Project.registerGenerateConfigTask(extension: DetektExtension) {
        tasks.register(GENERATE_CONFIG, DetektGenerateConfigTask::class.java) {
            it.config.setFrom(project.provider { extension.config })
        }
    }

    private fun configurePluginDependencies(project: Project, extension: DetektExtension) {
        project.configurations.create(CONFIGURATION_DETEKT_PLUGINS) { configuration ->
            configuration.isVisible = false
            configuration.isTransitive = true
            configuration.description = "The $CONFIGURATION_DETEKT_PLUGINS libraries to be used for this project."
        }

        project.configurations.create(CONFIGURATION_DETEKT) { configuration ->
            configuration.isVisible = false
            configuration.isTransitive = true
            configuration.description = "The $CONFIGURATION_DETEKT dependencies to be used for this project."

            configuration.defaultDependencies { dependencySet ->
                val version = extension.toolVersion ?: DEFAULT_DETEKT_VERSION
                dependencySet.add(project.dependencies.create("io.gitlab.arturbosch.detekt:detekt-cli:$version"))
            }
        }
    }

    private fun setTaskDefaults(project: Project) {
        project.tasks.withType(Detekt::class.java).configureEach {
            it.detektClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT))
            it.pluginClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT_PLUGINS))
        }

        project.tasks.withType(DetektCreateBaselineTask::class.java).configureEach {
            it.detektClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT))
            it.pluginClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT_PLUGINS))
        }

        project.tasks.withType(DetektGenerateConfigTask::class.java).configureEach {
            it.detektClasspath.setFrom(project.configurations.getAt(CONFIGURATION_DETEKT))
        }
    }

    companion object {
        const val DETEKT_TASK_NAME = "detekt"
        const val BASELINE_TASK_NAME = "detektBaseline"
        const val DETEKT_EXTENSION = "detekt"
        private const val GENERATE_CONFIG = "detektGenerateConfig"
        internal val defaultExcludes = listOf("build/")
        internal val defaultIncludes = listOf("**/*.kt", "**/*.kts")
        internal const val CONFIG_DIR_NAME = "config/detekt"
        internal const val CONFIG_FILE = "detekt.yml"
    }
}


const val CONFIGURATION_DETEKT = "detekt"
const val CONFIGURATION_DETEKT_PLUGINS = "detektPlugins"
