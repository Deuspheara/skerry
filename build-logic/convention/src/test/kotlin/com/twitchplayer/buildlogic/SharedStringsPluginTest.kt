package com.twitchplayer.buildlogic

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SharedStringsPluginTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun theExtensionPointsAtAnotherModulesResDirectory() {
        val project = ProjectBuilder.builder().withProjectDir(tmp.root).build()
        project.pluginManager.apply(SharedStringsPlugin::class.java)

        val extension = project.extensions.getByType(SharedStringsExtension::class.java)
        extension.fromModule(":app")

        assertEquals(File(tmp.root, "app/src/main/res").canonicalFile, extension.resDirectory.get().asFile.canonicalFile)
        extension.fromModule(":tv:app")
        assertEquals(File(tmp.root, "tv/app/src/main/res").canonicalFile, extension.resDirectory.get().asFile.canonicalFile)
    }

    @Test fun theTaskWritesTheGeneratedDirectoryFromItsInputs() {
        val project = ProjectBuilder.builder().withProjectDir(tmp.root).build()
        val source = File(tmp.root, "app/src/main/res/values-fr").apply { mkdirs() }
        File(source, "strings.xml").writeText("<resources><string name=\"app_name\">Twitch</string></resources>")
        File(tmp.root, "app/src/main/res/values/colors.xml").apply { parentFile.mkdirs() }.writeText("<resources/>")

        val task = project.tasks.register("syncDebugSharedStrings", SyncSharedStringsTask::class.java) {
            sourceResDir.set(File(tmp.root, "app/src/main/res"))
            outputDir.set(project.layout.buildDirectory.dir("generated/sharedStrings/debug"))
        }.get()
        task.sync()

        val out = File(tmp.root, "build/generated/sharedStrings/debug")
        assertTrue(File(out, "values-fr/strings.xml").readText().contains("app_name"))
        assertTrue(!File(out, "values/colors.xml").exists())
        assertNotNull(task.outputDir.get().asFile)
    }
}
