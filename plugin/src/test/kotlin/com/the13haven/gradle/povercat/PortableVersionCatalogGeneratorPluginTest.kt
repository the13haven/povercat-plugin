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
    fun `generated catalog exposes public Gradle types and preserves rich versions`() {
        writeBuildFile()
        writeTomlFile()

        val producerResult = runner("jar").build()
        assertEquals(TaskOutcome.SUCCESS, producerResult.task(":jar")?.outcome)

        val generatedCatalog = projectDir.resolve(
            "build/generated/sources/com/example/catalog/VersionsCatalog.kt"
        ).readText()
        assertTrue(
            generatedCatalog.contains(
                "val libWithVersionAsObject: MinimalExternalModuleDependency"
            )
        )
        assertTrue(
            generatedCatalog.contains(
                "fun testBundleSimple(objectFactory: ObjectFactory): " +
                    "Provider<ExternalModuleDependencyBundle>"
            )
        )
        assertTrue(generatedCatalog.contains("val pluginVersionAsObject: PluginDependency"))
        assertTrue(generatedCatalog.contains("val versionAsObject: VersionConstraint"))

        val producerJar = projectDir.resolve("build/libs")
            .listFiles { file -> file.extension == "jar" }
            ?.single()
            ?: error("Expected exactly one producer JAR")
        val consumerDir = projectDir.resolve("consumer").apply { mkdirs() }
        writeConsumerBuild(consumerDir, producerJar)
        writeJavaConsumer(consumerDir)

        val consumerResult = GradleRunner.create()
            .withProjectDir(consumerDir)
            .withArguments("compileJava", "verifyCatalog")
            .build()

        assertEquals(TaskOutcome.SUCCESS, consumerResult.task(":compileJava")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, consumerResult.task(":verifyCatalog")?.outcome)
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
    fun `fail on class name collision and allow resolving it with explicit names`() {
        val firstCatalog = projectDir.resolve("catalog/team-a/libs-main.toml")
        val secondCatalog = projectDir.resolve("catalog/team-b/libs_main.toml")
        firstCatalog.parentFile.mkdirs()
        secondCatalog.parentFile.mkdirs()
        writeMinimalTomlFile(firstCatalog)
        writeMinimalTomlFile(secondCatalog)
        writeBuildFile(tomlFiles = listOf(firstCatalog, secondCatalog))

        val failedResult = runner("generatePortableVersionCatalog").buildAndFail()

        assertTrue(failedResult.output.contains("Multiple version catalogs"))
        assertTrue(failedResult.output.contains(firstCatalog.absolutePath))
        assertTrue(failedResult.output.contains(secondCatalog.absolutePath))
        assertTrue(failedResult.output.contains("catalogClassNames"))

        writeBuildFile(
            tomlFiles = listOf(firstCatalog, secondCatalog),
            catalogClassNames = mapOf(
                "catalog/team-a/libs-main.toml" to "TeamAVersions",
                "catalog/team-b/libs_main.toml" to "TeamBVersions"
            )
        )

        val successfulResult = runner(
            "compileKotlin",
            "--configuration-cache",
            "--configuration-cache-problems=fail"
        ).build()
        assertEquals(
            TaskOutcome.SUCCESS,
            successfulResult.task(":generatePortableVersionCatalog")?.outcome
        )

        val generatedPackage = projectDir.resolve("build/generated/sources/com/example/catalog")
        assertTrue(
            generatedPackage.resolve("TeamAVersionsCatalog.kt")
                .readText()
                .contains("class TeamAVersions private constructor()")
        )
        assertTrue(
            generatedPackage.resolve("TeamBVersionsCatalog.kt")
                .readText()
                .contains("class TeamBVersions private constructor()")
        )
        assertTrue(
            projectDir.resolve(
                "build/classes/kotlin/main/com/example/catalog/TeamAVersions.class"
            ).exists()
        )
        assertTrue(
            projectDir.resolve(
                "build/classes/kotlin/main/com/example/catalog/TeamBVersions.class"
            ).exists()
        )

        writeBuildFile(
            tomlFiles = listOf(firstCatalog, secondCatalog),
            catalogClassNames = mapOf(
                "catalog/team-a/libs-main.toml" to "RenamedTeamAVersions",
                "catalog/team-b/libs_main.toml" to "TeamBVersions"
            )
        )
        runner(
            "generatePortableVersionCatalog",
            "--configuration-cache",
            "--configuration-cache-problems=fail"
        ).build()

        assertFalse(generatedPackage.resolve("TeamAVersionsCatalog.kt").exists())
        assertTrue(generatedPackage.resolve("RenamedTeamAVersionsCatalog.kt").exists())
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
        tomlFiles: List<File>? = listOf(projectDir.resolve(versionsFileName)),
        catalogClassNames: Map<String, String> = emptyMap()
    ) {
        val tomlFilesConfiguration = when {
            tomlFiles == null -> ""
            tomlFiles.isEmpty() -> "tomlFiles.setFrom(emptyList<Any>())"
            else -> tomlFiles.joinToString(
                prefix = "tomlFiles.setFrom(listOf(",
                postfix = "))"
            ) { "\"${it.absolutePath}\"" }
        }
        val catalogClassNamesConfiguration = catalogClassNames.entries
            .joinToString("\n") { (catalogPath, className) ->
                """catalogClassNames.put("$catalogPath", "$className")"""
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
                $catalogClassNamesConfiguration
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

    private fun writeConsumerBuild(consumerDir: File, producerJar: File) {
        consumerDir.resolve("settings.gradle.kts").writeText(
            """rootProject.name = "catalog-consumer""""
        )
        consumerDir.resolve("build.gradle.kts").writeText(
            """
            import com.example.catalog.Versions
            import org.gradle.api.artifacts.ExternalModuleDependencyBundle
            import org.gradle.api.artifacts.MinimalExternalModuleDependency
            import org.gradle.api.artifacts.VersionConstraint
            import org.gradle.api.provider.Provider
            import org.gradle.plugin.use.PluginDependency

            buildscript {
                dependencies {
                    classpath(files("${producerJar.invariantSeparatorsPath}"))
                }
            }

            plugins {
                java
            }

            dependencies {
                implementation(gradleApi())
                implementation(files("${producerJar.invariantSeparatorsPath}"))
            }

            val richVersion: VersionConstraint = Versions.Versions.versionAsObject
            val richLibrary: MinimalExternalModuleDependency =
                Versions.Libraries.libWithVersionAsObject
            val richPlugin: PluginDependency = Versions.Plugins.pluginVersionAsObject
            val bundle: Provider<ExternalModuleDependencyBundle> =
                Versions.Bundles.testBundleSimple(objects)

            val catalogVerification = configurations.create("catalogVerification") {
                isCanBeResolved = false
                isCanBeConsumed = false
            }
            dependencies.add(catalogVerification.name, richLibrary)
            dependencies.addProvider(catalogVerification.name, bundle)

            tasks.register("verifyCatalog") {
                doLast {
                    check(richVersion.requiredVersion == "1.1.1")
                    check(richVersion.strictVersion == "1.1.1")
                    check(richVersion.preferredVersion == "1.0.0")
                    check(richVersion.rejectedVersions == listOf("0.0.1", "0.0.2"))
                    check(Versions.Versions.versionSimple.requiredVersion == "1.2.3")
                    check(Versions.Versions.versionRejectAll.rejectedVersions == listOf("+"))

                    check(richLibrary.group == "lib.test.version.as.object")
                    check(richLibrary.name == "version-as-object")
                    check(richLibrary.versionConstraint.requiredVersion == "1.1.1")
                    check(richLibrary.versionConstraint.strictVersion == "1.1.1")
                    check(richLibrary.versionConstraint.preferredVersion == "1.0.0")
                    check(
                        richLibrary.versionConstraint.rejectedVersions ==
                            listOf("0.0.1", "0.0.2")
                    )
                    check(
                        Versions.Libraries.libWithVersionRef
                            .versionConstraint
                            .requiredVersion == "1.2.3"
                    )

                    check(richPlugin.pluginId == "com.test.version-as-object")
                    check(richPlugin.version.requiredVersion == "1.1.1")
                    check(richPlugin.version.strictVersion == "1.1.1")
                    check(richPlugin.version.preferredVersion == "1.0.0")
                    check(richPlugin.version.rejectedVersions == listOf("0.0.1", "0.0.2"))
                    check(Versions.Plugins.pluginVersionRef.version.requiredVersion == "1.2.3")

                    check(bundle.get().size == 2)
                }
            }
            """.trimIndent()
        )
    }

    private fun writeJavaConsumer(consumerDir: File) {
        val javaSource = consumerDir.resolve("src/main/java/JavaCatalogUsage.java")
        javaSource.parentFile.mkdirs()
        javaSource.writeText(
            """
            import com.example.catalog.Versions;
            import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
            import org.gradle.api.artifacts.MinimalExternalModuleDependency;
            import org.gradle.api.artifacts.VersionConstraint;
            import org.gradle.api.model.ObjectFactory;
            import org.gradle.api.provider.Provider;
            import org.gradle.plugin.use.PluginDependency;

            public final class JavaCatalogUsage {
                public static VersionConstraint version() {
                    return Versions.Versions.getVersionAsObject();
                }

                public static MinimalExternalModuleDependency library() {
                    return Versions.Libraries.getLibWithVersionAsObject();
                }

                public static Provider<ExternalModuleDependencyBundle> bundle(
                    ObjectFactory objectFactory
                ) {
                    return Versions.Bundles.testBundleSimple(objectFactory);
                }

                public static PluginDependency plugin() {
                    return Versions.Plugins.getPluginVersionAsObject();
                }
            }
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
