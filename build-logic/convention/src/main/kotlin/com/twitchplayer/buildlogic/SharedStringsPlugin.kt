package com.twitchplayer.buildlogic

import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/** `sharedStrings { fromModule(":app") }` in an Android application or library module. */
abstract class SharedStringsExtension(private val project: Project) {
    /** The `res` directory the strings are taken from; set it with [fromModule] or directly. */
    abstract val resDirectory: DirectoryProperty

    /** Takes the strings of another module's `src/main/res` (path and name only: no project state is read). */
    fun fromModule(path: String) {
        resDirectory.set(project.rootProject.layout.projectDirectory.dir(path.trim(':').replace(':', '/') + "/src/main/res"))
    }
}

/**
 * Reuses the TV app's translated text resources (`strings.xml`, plurals, string arrays: see [SharedStrings]) in another app (the phone app) WITHOUT copying them into git: one
 * generated `res` directory per variant, registered through AGP's variant API, rebuilt by [SyncSharedStringsTask].
 *
 * The consuming module keeps its own strings in a file with a different name (e.g. `phone_strings.xml`; the plugin takes every text file of the other module's `values*` directories, so avoid reusing a name that module uses): the same resource
 * name defined in both places fails the resource merge on purpose, so a TV string is never silently shadowed. The consumer also
 * declares its own `resources.properties` / `generateLocaleConfig`.
 */
class SharedStringsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("sharedStrings", SharedStringsExtension::class.java, project)
        project.pluginManager.withPlugin("com.android.application") { register(project, extension) }
        project.pluginManager.withPlugin("com.android.library") { register(project, extension) }
    }

    private fun register(project: Project, extension: SharedStringsExtension) {
        val components = project.extensions.getByType(AndroidComponentsExtension::class.java)
        components.onVariants { variant ->
            val sync = project.tasks.register(
                "sync${variant.name.replaceFirstChar { it.uppercase() }}SharedStrings",
                SyncSharedStringsTask::class.java,
            ) {
                sourceResDir.set(extension.resDirectory)
                outputDir.set(project.layout.buildDirectory.dir("generated/sharedStrings/${variant.name}"))
            }
            variant.sources.res?.addGeneratedSourceDirectory(sync, SyncSharedStringsTask::outputDir)
        }
    }
}
