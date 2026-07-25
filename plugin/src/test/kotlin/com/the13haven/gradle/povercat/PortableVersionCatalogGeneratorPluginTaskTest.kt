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

import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockkObject
import org.gradle.api.Project
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.io.File


/**
 * Test cases for PoVerCat Plugin Task.
 *
 * @author ssidorov@the13haven.com
 */
@ExtendWith(MockKExtension::class)
class PortableVersionCatalogGeneratorPluginTaskTest {

    private lateinit var task: TaskProvider<PortableVersionCatalogGeneratorPluginTask>
    private lateinit var project: Project

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.register("generateCatalog", PortableVersionCatalogGeneratorPluginTask::class.java)

        task.get().catalogPackage.set("com.example.catalog")
        task.get().projectVersion.set("1.0.0")
        task.get().catalogClassNames.convention(emptyMap())
        task.get().projectDirectory.set(tempDir)
        task.get().outputDir.set(tempDir)
    }

    @Test
    fun `should not require work when no catalogs or previous outputs exist`() {
        assertFalse(task.get().hasConfiguredCatalogsOrPreviousOutputs())
    }

    @Test
    fun `should require work when previous outputs manifest exists`() {
        File(
            tempDir,
            PortableVersionCatalogGeneratorPluginTask.GENERATED_FILES_MANIFEST
        ).writeText("com/example/catalog/StaleCatalog.kt\n")

        assertTrue(task.get().hasConfiguredCatalogsOrPreviousOutputs())
    }

    @Test
    fun `should require work when class name is configured without catalog file`() {
        task.get().catalogClassNames.put("missing.toml", "MissingVersions")

        assertTrue(task.get().hasConfiguredCatalogsOrPreviousOutputs())
    }

    @Test
    fun `should throw exception when toml file does not exist`() {
        val missingFile = File(tempDir, "missing.toml")

        task.get().tomlFiles.setFrom(missingFile)

        val exception = assertThrows<GradleException> {
            task.get().executeTask()
        }

        assertTrue(exception.message!!.contains(missingFile.absolutePath))
    }

    @Test
    fun `should generate Kotlin class file for each TOML file`() {
        val validTomlFile = File(tempDir, "valid.toml").apply { writeText("[versions]") }

        task.get().tomlFiles.setFrom(validTomlFile)

        mockkObject(TomlParserUtils)
        mockkObject(PortableVersionCatalogClassGenerator)

        every { TomlParserUtils.toCamelCase("valid") } returns "Valid"
        every {
            PortableVersionCatalogClassGenerator.generateClass(
                any(),
                any(),
                any(),
                any()
            )
        } returns "class Valid {}"

        task.get().executeTask()

        val generatedFile = File(tempDir, "com/example/catalog/ValidCatalog.kt")
        assertTrue(generatedFile.exists())
        assertTrue(generatedFile.readText().contains("class Valid {}"))
        assertEquals(
            "com/example/catalog/ValidCatalog.kt\n",
            File(
                tempDir,
                PortableVersionCatalogGeneratorPluginTask.GENERATED_FILES_MANIFEST
            ).readText()
        )
    }

    @Test
    fun `should use explicitly configured catalog class name`() {
        val catalogFile = File(tempDir, "custom.toml").apply { writeText("[versions]") }
        task.get().tomlFiles.setFrom(catalogFile)
        task.get().catalogClassNames.put("custom.toml", "CompanyVersions")

        mockkObject(PortableVersionCatalogClassGenerator)
        every {
            PortableVersionCatalogClassGenerator.generateClass(
                catalogFile,
                "com.example.catalog",
                "CompanyVersions",
                "1.0.0"
            )
        } returns "class CompanyVersions {}"

        task.get().executeTask()

        val generatedFile = File(tempDir, "com/example/catalog/CompanyVersionsCatalog.kt")
        assertTrue(generatedFile.exists())
        assertTrue(generatedFile.readText().contains("class CompanyVersions {}"))
    }

    @Test
    fun `should fail with actionable message when generated class names collide`() {
        val firstCatalog = File(tempDir, "libs-main.toml").apply { writeText("[versions]") }
        val secondCatalog = File(tempDir, "libs_main.toml").apply { writeText("[versions]") }
        task.get().tomlFiles.setFrom(firstCatalog, secondCatalog)

        val exception = assertThrows<GradleException> {
            task.get().executeTask()
        }

        assertTrue(exception.message!!.contains("Multiple version catalogs"))
        assertTrue(exception.message!!.contains(firstCatalog.absolutePath))
        assertTrue(exception.message!!.contains(secondCatalog.absolutePath))
        assertTrue(exception.message!!.contains("catalogClassNames"))
    }

    @Test
    fun `should reject invalid explicit catalog class name`() {
        val catalogFile = File(tempDir, "custom.toml").apply { writeText("[versions]") }
        task.get().tomlFiles.setFrom(catalogFile)
        task.get().catalogClassNames.put("custom.toml", "invalid-name")

        val exception = assertThrows<GradleException> {
            task.get().executeTask()
        }

        assertTrue(exception.message!!.contains("Invalid generated catalog class name"))
        assertTrue(exception.message!!.contains("invalid-name"))
    }

    @Test
    fun `should reject class name configured for file outside toml files`() {
        val catalogFile = File(tempDir, "custom.toml").apply { writeText("[versions]") }
        task.get().tomlFiles.setFrom(catalogFile)
        task.get().catalogClassNames.put("another.toml", "AnotherVersions")

        val exception = assertThrows<GradleException> {
            task.get().executeTask()
        }

        assertTrue(exception.message!!.contains("not in tomlFiles"))
        assertTrue(exception.message!!.contains("another.toml"))
    }

    @Test
    fun `should delete stale generated files listed in manifest`() {
        val staleFile = File(tempDir, "com/example/catalog/StaleCatalog.kt").apply {
            parentFile.mkdirs()
            writeText("class StaleCatalog")
        }
        val manifest = File(
            tempDir,
            PortableVersionCatalogGeneratorPluginTask.GENERATED_FILES_MANIFEST
        ).apply {
            writeText("com/example/catalog/StaleCatalog.kt\n")
        }

        task.get().executeTask()

        assertFalse(staleFile.exists())
        assertFalse(manifest.exists())
        assertFalse(File(tempDir, "com").exists())
    }

    @Test
    fun `should skip file creation if content is empty`() {
        val validTomlFile = File(tempDir, "TestEmpty.toml").apply { writeText("[versions]") }

        task.get().tomlFiles.setFrom(validTomlFile)

        mockkObject(TomlParserUtils)
        mockkObject(PortableVersionCatalogClassGenerator)

        every { TomlParserUtils.toCamelCase("TestEmpty") } returns "TestEmpty"
        every {
            PortableVersionCatalogClassGenerator.generateClass(
                any(),
                any(),
                any(),
                any()
            )
        } returns "   "

        task.get().executeTask()

        val generatedFile = File(tempDir, "com/example/catalog/TestEmptyCatalog.kt")
        assertFalse(generatedFile.exists())
        assertFalse(
            File(
                tempDir,
                PortableVersionCatalogGeneratorPluginTask.GENERATED_FILES_MANIFEST
            ).exists()
        )
    }
}
