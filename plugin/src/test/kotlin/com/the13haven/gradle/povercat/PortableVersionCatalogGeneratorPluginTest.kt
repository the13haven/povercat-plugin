/*
 * Copyright 2025
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.the13haven.gradle.povercat

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File


/**
 * Test cases for PoVerCat Plugin.
 *
 * @author ssidorov@the13haven.com
 */
class PortableVersionCatalogGeneratorPluginTest {
    @TempDir
    lateinit var projectDir: File

    private val versionsFileName = "versions.toml"

    @Test
    fun `generate catalog with reusable configuration cache`() {
        // 1. Create test build.gradle.kts
        writeBuildFile()

        // 2. Create file versions.toml
        writeTomlFile()

        val srcMainKotlin = projectDir.resolve("src/main/kotlin")
        val srcMainJava = projectDir.resolve("src/main/java")

        srcMainKotlin.mkdirs()
        srcMainJava.mkdirs()

        // 3. Run Gradle task
        val runner = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "compileKotlin",
                "--configuration-cache",
                "--configuration-cache-problems=fail"
            )
            .forwardOutput()

        val firstRun = runner.build()

        // 4. Verify
        assertEquals(
            TaskOutcome.SUCCESS,
            firstRun.task(":generatePortableVersionCatalog")?.outcome
        )
        assertTrue(firstRun.output.contains("Configuration cache entry stored."))

        val secondRun = runner.build()
        assertEquals(
            TaskOutcome.UP_TO_DATE,
            secondRun.task(":generatePortableVersionCatalog")?.outcome
        )
        assertTrue(secondRun.output.contains("Configuration cache entry reused."))

        // 5. Check files
        val generatedDir = projectDir.resolve("build/generated/sources")
        assertTrue(generatedDir.exists())
        val generatedCatalog = generatedDir.resolve("com/example/catalog/VersionsCatalog.kt")
        assertTrue(generatedCatalog.exists())
        assertTrue(generatedCatalog.readText().contains("@version v1.2.3"))
        assertTrue(
            projectDir.resolve("build/classes/kotlin/main/com/example/catalog/Versions.class").exists()
        )

        // 6. Change a declared task input and verify the catalog is regenerated
        writeBuildFile(projectVersion = "2.0.0")

        val thirdRun = runner.build()
        assertEquals(
            TaskOutcome.SUCCESS,
            thirdRun.task(":generatePortableVersionCatalog")?.outcome
        )
        assertTrue(generatedCatalog.readText().contains("@version v2.0.0"))
    }

    @Test
    fun `fail with clear message when producer has no supported Kotlin plugin`() {
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.the13haven.povercat")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks")
            .buildAndFail()

        assertTrue(
            result.output.contains(PortableVersionCatalogGeneratorPlugin.KOTLIN_PLUGIN_REQUIRED_MESSAGE)
        )
    }

    @Test
    fun `skip generation when catalogs are explicitly empty`() {
        writeBuildFile(tomlFiles = emptyList())

        val result = runner("generatePortableVersionCatalog").build()

        assertEquals(
            TaskOutcome.SKIPPED,
            result.task(":generatePortableVersionCatalog")?.outcome
        )
        assertFalse(projectDir.resolve("build/generated/sources").exists())
    }

    @Test
    fun `fail when default catalog does not exist`() {
        writeBuildFile(tomlFiles = null)

        val result = runner("generatePortableVersionCatalog").buildAndFail()

        assertTrue(result.output.contains("gradle/libs.versions.toml"))
        assertTrue(result.output.contains("Version catalog file not found"))
    }

    @Test
    fun `fail when configured catalog does not exist`() {
        val missingCatalog = projectDir.resolve("catalog/missing.toml")
        writeBuildFile(tomlFiles = listOf(missingCatalog))

        val result = runner("generatePortableVersionCatalog").buildAndFail()

        assertTrue(result.output.contains(missingCatalog.absolutePath))
        assertTrue(result.output.contains("Version catalog file not found"))
    }

    @Test
    fun `remove stale generated sources when catalogs change`() {
        val firstCatalog = projectDir.resolve("first.toml")
        val secondCatalog = projectDir.resolve("second.toml")
        writeMinimalTomlFile(firstCatalog)
        writeMinimalTomlFile(secondCatalog)
        writeBuildFile(tomlFiles = listOf(firstCatalog, secondCatalog))

        val generatedDir = projectDir.resolve("build/generated/sources")
        val firstSource = generatedDir.resolve("com/example/catalog/FirstCatalog.kt")
        val secondSource = generatedDir.resolve("com/example/catalog/SecondCatalog.kt")
        val manifest = generatedDir.resolve(
            PortableVersionCatalogGeneratorPluginTask.GENERATED_FILES_MANIFEST
        )

        assertEquals(
            TaskOutcome.SUCCESS,
            runner("generatePortableVersionCatalog")
                .build()
                .task(":generatePortableVersionCatalog")
                ?.outcome
        )
        assertTrue(firstSource.exists())
        assertTrue(secondSource.exists())
        assertTrue(manifest.exists())

        writeBuildFile(tomlFiles = listOf(firstCatalog))
        runner("generatePortableVersionCatalog").build()
        assertTrue(firstSource.exists())
        assertFalse(secondSource.exists())

        val renamedCatalog = projectDir.resolve("renamed.toml")
        assertTrue(firstCatalog.renameTo(renamedCatalog))
        writeBuildFile(tomlFiles = listOf(renamedCatalog))
        runner("generatePortableVersionCatalog").build()

        val renamedSource = generatedDir.resolve("com/example/catalog/RenamedCatalog.kt")
        assertFalse(firstSource.exists())
        assertTrue(renamedSource.exists())

        writeBuildFile(tomlFiles = emptyList())
        assertEquals(
            TaskOutcome.SUCCESS,
            runner("generatePortableVersionCatalog")
                .build()
                .task(":generatePortableVersionCatalog")
                ?.outcome
        )
        assertFalse(renamedSource.exists())
        assertFalse(manifest.exists())

        assertEquals(
            TaskOutcome.SKIPPED,
            runner("generatePortableVersionCatalog")
                .build()
                .task(":generatePortableVersionCatalog")
                ?.outcome
        )
    }

    private fun writeBuildFile(
        projectVersion: String = "1.2.3",
        tomlFiles: List<File>? = listOf(projectDir.resolve(versionsFileName))
    ) {
        val tomlFilesConfiguration = when {
            tomlFiles == null -> ""
            tomlFiles.isEmpty() -> "tomlFiles.setFrom(emptyList<Any>())"
            else -> tomlFiles.joinToString(
                prefix = "tomlFiles.setFrom(listOf(",
                postfix = "))"
            ) { "\"${it.absolutePath}\"" }
        }
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            """
            plugins {
                id("jacoco-testkit-coverage")
                id("com.the13haven.povercat")
                `kotlin-dsl`
            }

            repositories {
                mavenCentral()
            }

            portableVersionCatalog {
                catalogPackage.set("com.example.catalog")
                $tomlFilesConfiguration
                outputDir.set(file("build/generated/sources"))
            }

            version = "$projectVersion"
            """.trimIndent()
        )
    }

    private fun runner(vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*arguments)

    private fun writeTomlFile() {
        val tomlFile = projectDir.resolve(versionsFileName)
        tomlFile.writeText(
            """
            [versions]

            version-simple = "1.2.3"
            version-as-object = { prefer = "1.0.0", require = "1.0.1", strictly = "1.1.1", reject = ["0.0.1", "0.0.2"] }
            version-reject-all = { rejectAll = true }

            [libraries]

            lib-simple-with-version = "com.mycompany:mylib:1.4"
            lib-simple-no-version.module = "com.mycompany:mylib"
            lib-module = { module = "com.mycompany:other", version = "1.4" }
            lib-with-version-ref = { group = "lib.test.version.ref", name = "version-ref", version.ref = "version-simple" }
            lib-with-version-ref-not-found = { group = "lib.test.version.ref", name = "version-ref", version.ref = "version-unknown" }
            lib-with-version-as-object = { group = "lib.test.version.as.object", name = "version-as-object", version = { prefer = "1.0.0", require = "1.0.1", strictly = "1.1.1", reject = ["0.0.1", "0.0.2"] } }

            [bundles]

            test-bundle = ["lib-module", "lib-with-version-ref", "lib-with-version-ref-not-found"]
            test-bundle-simple = ["lib-simple-with-version", "lib-with-version-as-object"]

            [plugins]

            plugin-simple-version = { id = "com.github.ben-manes.versions", version = "0.45.0" }
            plugin-version-ref = { id = "com.test.plugin-version-ref", version.ref = "version-simple" }
            plugin-version-as-object = { id = "com.test.version-as-object", version = { prefer = "1.0.0", require = "1.0.1", strictly = "1.1.1", reject = ["0.0.1", "0.0.2"] } }
            plugin-simple-id.id = "com.text.plugin-with-id"
            """.trimIndent()
        )
    }

    private fun writeMinimalTomlFile(file: File) {
        file.writeText(
            """
            [versions]
            kotlin = "2.3.0"
            """.trimIndent()
        )
    }
}
