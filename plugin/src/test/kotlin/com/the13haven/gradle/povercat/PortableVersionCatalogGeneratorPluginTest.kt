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

    val versionsFileName = "versions.toml"

    @Test
    fun `apply plugin and verify task exists`() {
        // 1. Create test build.gradle.kts
        writeBuildFile()

        // 2. Create file versions.toml
        writeTomlFile()

        val srcMainKotlin = projectDir.resolve("src/main/kotlin")
        val srcMainJava = projectDir.resolve("src/main/java")

        srcMainKotlin.mkdirs()
        srcMainJava.mkdirs()

        // 3. Run Gradle task
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generatePortableVersionCatalog", "--info")
            .withDebug(true)
            .forwardOutput()
            .build()

        // 4. Verify
        assertTrue(result.output.contains("BUILD SUCCESSFUL"))

        // 5. Check files
        val generatedDir = projectDir.resolve("build/generated/sources")
        assertTrue(generatedDir.exists())
        assertTrue(generatedDir.resolve("com/example/catalog/VersionsCatalog.kt").exists())
    }

    private fun writeBuildFile() {
        val buildFile = projectDir.resolve("build.gradle.kts")
        buildFile.writeText(
            """
            plugins {
                `kotlin-dsl`
                id("jacoco-testkit-coverage")
                id("com.l13.plugin.povercat")
            }

            portableVersionCatalog {
                catalogPackage.set("com.example.catalog")
                tomlFiles.setFrom("${projectDir.absolutePath}/${versionsFileName}")
                outputDir.set(file("build/generated/sources"))
            }
            """.trimIndent()
        )
    }

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
}