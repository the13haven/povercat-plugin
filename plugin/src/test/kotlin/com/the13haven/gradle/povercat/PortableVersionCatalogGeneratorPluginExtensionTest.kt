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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test cases for PoVerCat Plugin Extension.
 *
 * @author ssidorov@the13haven.com
 */
class PortableVersionCatalogGeneratorPluginExtensionTest {

    private val project: Project = ProjectBuilder.builder().build()
    private val extension = project.extensions.create(
        "extension",
        PortableVersionCatalogGeneratorPluginExtension::class.java
    )

    @Test
    fun `should have default catalogPackage value`() {
        assertEquals("org.gradle.version.catalog", extension.catalogPackage.get())
    }

    @Test
    fun `should allow setting custom catalogPackage`() {
        extension.catalogPackage.set("com.example.mycatalog")
        assertEquals("com.example.mycatalog", extension.catalogPackage.get())
    }

    @Test
    fun `should have default tomlFiles value`() {
        assertFalse(extension.tomlFiles.files.isEmpty())
    }

    @Test
    fun `should allow adding toml files`() {
        val file = project.file("custom.versions.toml")
        extension.tomlFiles.setFrom(file)
        assertTrue(extension.tomlFiles.files.contains(file))
    }

    @Test
    fun `should have default outputDir value`() {
        val expectedDir = project.layout.buildDirectory.dir("build/generated/sources").get().asFile
        assertEquals(expectedDir, extension.outputDir.get().asFile)
    }

    @Test
    fun `should allow setting custom outputDir`() {
        val customDir = project.layout.projectDirectory.dir("custom/output").asFile
        extension.outputDir.set(customDir)
        assertEquals(customDir, extension.outputDir.get().asFile)
    }
}