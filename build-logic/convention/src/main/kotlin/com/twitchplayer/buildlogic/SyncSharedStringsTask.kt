package com.twitchplayer.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** Generates a variant's extra `res` directory from another app's translated strings (see [SharedStrings]). */
@CacheableTask
abstract class SyncSharedStringsTask : DefaultTask() {
    /** The other app's `res` directory (e.g. `app/src/main/res`). */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceResDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun sync() {
        val written = SharedStrings.copy(sourceResDir.get().asFile, outputDir.get().asFile)
        logger.info("Shared strings: {} files from {}", written.size, sourceResDir.get().asFile)
    }
}
