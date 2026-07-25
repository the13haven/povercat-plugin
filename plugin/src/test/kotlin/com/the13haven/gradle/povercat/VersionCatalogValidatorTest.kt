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

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.tomlj.Toml
import java.io.File

class VersionCatalogValidatorTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `rejects invalid TOML syntax with file and position`() {
        val catalog = catalogFile(
            """
            [versions
            kotlin = "2.3.0"
            """
        )

        val exception = validateAndCapture(catalog)

        assertTrue(exception.message!!.contains(catalog.absolutePath))
        assertTrue(exception.message!!.contains("line 1, column"))
    }

    @Test
    fun `rejects unknown version references in libraries and plugins`() {
        val catalog = catalogFile(
            """
            [versions]
            kotlin = "2.3.0"

            [libraries]
            kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect", version.ref = "missing-library-version" }

            [plugins]
            kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "missing-plugin-version" }
            """
        )

        val exception = validateAndCapture(catalog)

        assertTrue(exception.message!!.contains("[libraries].kotlin-reflect"))
        assertTrue(
            exception.message!!.contains(
                "references unknown version alias 'missing-library-version'"
            )
        )
        assertTrue(exception.message!!.contains("[plugins].kotlin-jvm"))
        assertTrue(
            exception.message!!.contains(
                "references unknown version alias 'missing-plugin-version'"
            )
        )
    }

    @Test
    fun `rejects unknown library references in bundles`() {
        val catalog = catalogFile(
            """
            [libraries]
            kotlin-reflect = "org.jetbrains.kotlin:kotlin-reflect:2.3.0"

            [bundles]
            kotlin = ["kotlin-reflect", "missing-library"]
            """
        )

        val exception = validateAndCapture(catalog)

        assertTrue(exception.message!!.contains("[bundles].kotlin"))
        assertTrue(
            exception.message!!.contains(
                "references unknown library alias 'missing-library'"
            )
        )
    }

    @Test
    fun `rejects malformed library declarations with actionable messages`() {
        val catalog = catalogFile(
            """
            [libraries]
            invalid-short-notation = "only-group"
            invalid-module = { module = "group-only" }
            incomplete-library = { group = "com.example" }
            conflicting-library = { module = "com.example:module", group = "com.example", name = "module" }
            """
        )

        val exception = validateAndCapture(catalog)

        assertTrue(
            exception.message!!.contains(
                "[libraries].invalid-short-notation"
            )
        )
        assertTrue(exception.message!!.contains("non-empty 'group:name:version' notation"))
        assertTrue(exception.message!!.contains("[libraries].invalid-module"))
        assertTrue(exception.message!!.contains("non-empty 'group:name' notation"))
        assertTrue(exception.message!!.contains("[libraries].incomplete-library"))
        assertTrue(exception.message!!.contains(".name must be a non-empty string"))
        assertTrue(exception.message!!.contains("[libraries].conflicting-library"))
        assertTrue(exception.message!!.contains("not both"))
    }

    @Test
    fun `rejects unsupported and invalid rich-version fields`() {
        val catalog = catalogFile(
            """
            [versions]
            invalid = { requires = "1.0", reject = "1.1", rejectAll = "true" }
            """
        )

        val exception = validateAndCapture(catalog)

        assertTrue(exception.message!!.contains("unsupported key 'requires'"))
        assertTrue(exception.message!!.contains(".reject must be an array"))
        assertTrue(exception.message!!.contains(".rejectAll must be a boolean"))
    }

    @Test
    fun `allows versionless libraries and plugins`() {
        val catalog = catalogFile(
            """
            [libraries]
            kotlin-reflect = { module = "org.jetbrains.kotlin:kotlin-reflect" }

            [plugins]
            java-library.id = "java-library"
            """
        )

        VersionCatalogValidator.validate(catalog, Toml.parse(catalog.toPath()))
    }

    @Test
    fun `allows Gradle-normalized version and bundle references`() {
        val catalog = catalogResource("normalized-references.versions.toml")

        VersionCatalogValidator.validate(catalog, Toml.parse(catalog.toPath()))
    }

    @Test
    fun `rejects aliases that are duplicates after Gradle normalization`() {
        val catalog = catalogResource("normalized-duplicates.versions.toml")

        val exception = validateAndCapture(catalog)

        listOf("versions", "libraries", "bundles", "plugins").forEach { section ->
            assertTrue(
                exception.message!!.contains(
                    "[$section] aliases"
                )
            )
        }
        assertTrue(
            exception.message!!.contains(
                "normalize to the same Gradle alias 'foo.bar'"
            )
        )
        assertTrue(exception.message!!.contains("Rename one alias"))
    }

    @Test
    fun `rejects distinct aliases that generate the same flat accessor`() {
        val catalog = catalogResource("accessor-collisions.versions.toml")

        val exception = validateAndCapture(catalog)

        assertTrue(
            exception.message!!.contains(
                "[versions] aliases 'some-alias'"
            )
        )
        assertTrue(exception.message!!.contains("same accessor 'someAlias'"))
        assertTrue(exception.message!!.contains("same accessor 'libraryAlias'"))
        assertTrue(exception.message!!.contains("same accessor 'bundleAlias'"))
        assertTrue(exception.message!!.contains("same accessor 'pluginAlias'"))
    }

    @Test
    fun `rejects invalid and reserved aliases before source generation`() {
        val catalog = catalogResource("invalid-and-reserved.versions.toml")

        val exception = validateAndCapture(catalog)

        assertTrue(exception.message!!.contains("[versions].foo*_bar"))
        assertTrue(exception.message!!.contains("must match Gradle alias pattern"))
        assertTrue(exception.message!!.contains("reserved Kotlin accessor name 'when'"))
        assertTrue(exception.message!!.contains("reserved Gradle alias name 'extensions'"))
        assertTrue(exception.message!!.contains("reserved Java name 'class'"))
        assertTrue(exception.message!!.contains("reserved library prefix 'versions'"))
    }

    @Test
    fun `rejects invalid aliases in version and bundle references`() {
        val catalog = catalogResource("invalid-references.versions.toml")

        val exception = validateAndCapture(catalog)

        assertTrue(
            exception.message!!.contains(
                "[libraries].invalid-version-reference"
            )
        )
        assertTrue(
            exception.message!!.contains(
                ".version.ref must match Gradle alias pattern"
            )
        )
        assertTrue(
            exception.message!!.contains(
                "[bundles].invalid-bundle-reference"
            )
        )
        assertTrue(exception.message!!.contains("[0] must match Gradle alias pattern"))
    }

    private fun catalogFile(content: String): File =
        tempDir.resolve("libs.versions.toml").apply {
            writeText(content.trimIndent())
        }

    private fun catalogResource(resourceName: String): File {
        val catalog = tempDir.resolve(resourceName)
        val resource = requireNotNull(
            javaClass.getResourceAsStream("/aliases/$resourceName")
        ) {
            "Test version catalog resource '/aliases/$resourceName' was not found"
        }

        resource.use { input ->
            catalog.outputStream().use(input::copyTo)
        }
        return catalog
    }

    private fun validateAndCapture(catalog: File): GradleException =
        assertThrows {
            VersionCatalogValidator.validate(catalog, Toml.parse(catalog.toPath()))
        }
}
