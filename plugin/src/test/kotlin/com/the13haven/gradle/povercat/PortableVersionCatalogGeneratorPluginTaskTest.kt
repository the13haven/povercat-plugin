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
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
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
        task.get().outputDir.set(tempDir)
    }

    @Test
    fun `should create output directory if not exists`() {
        val mockFile = File(tempDir, "com/example/catalog")
        assertTrue(!mockFile.exists())

        task.get().executeTask()

        assertTrue(mockFile.exists())
    }

    @Test
    fun `should skip creating output directory if exist`() {
        val mockFile = File(tempDir, "com/example/catalog")
        mockFile.mkdirs()

        assertTrue(mockFile.exists())

        task.get().executeTask()

        assertTrue(mockFile.exists())
    }

    @Test
    fun `should throw exception when toml file does not exist`() {
        val missingFile = File(tempDir, "missing.toml")

        task.get().tomlFiles.setFrom(missingFile)

        val exception = assertThrows<IllegalStateException> {
            task.get().executeTask()
        }

        assertTrue(exception.message!!.contains("Version catalog file not found"))
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

        val generatedFile = File(tempDir, "com/example/catalog/TestEmpty.kt")
        assertFalse(generatedFile.exists())
    }
}