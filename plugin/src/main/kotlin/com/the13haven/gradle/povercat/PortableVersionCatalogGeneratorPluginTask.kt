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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * PoVerCat Plugin Task.
 *
 * @author ssidorov@the13haven.com
 */
@DisableCachingByDefault(
    because = "Generated source includes the current year, which is not declared as a task input"
)
abstract class PortableVersionCatalogGeneratorPluginTask : DefaultTask() {

    @get:Input
    abstract val catalogPackage: Property<String>

    @get:Input
    abstract val projectVersion: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tomlFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun executeTask() {
        val configuredTomlFiles = tomlFiles.files

        configuredTomlFiles
            .filterNot(File::exists)
            .takeIf { it.isNotEmpty() }
            ?.let { missingFiles ->
                throw GradleException(
                    missingFiles.joinToString(
                        prefix = "Version catalog file not found: ",
                        separator = ", "
                    ) { it.absolutePath }
                )
            }

        val filteredTomlFiles = configuredTomlFiles
            .filter { file -> file.extension == "toml" }

        val outputRoot = outputDir.get().asFile
            .toPath()
            .toAbsolutePath()
            .normalize()
        val packagePath = catalogPackage.get()
            .split(".")
            .joinToString("/")

        val generatedSources = filteredTomlFiles.mapNotNull { tomlFile ->
            val className = TomlParserUtils.toCamelCase(tomlFile.nameWithoutExtension)
                .replaceFirstChar { it.uppercase(Locale.ROOT) }

            val classContent = PortableVersionCatalogClassGenerator.generateClass(
                tomlFile,
                catalogPackage.get(),
                className,
                projectVersion.get()
            )

            if (classContent.isNotBlank()) {
                val relativePath = "$packagePath/${className}Catalog.kt"
                relativePath to classContent
            } else {
                null
            }
        }.toMap()

        val previousGeneratedFiles = readPreviousGeneratedFiles(outputRoot)
        val currentGeneratedFiles = generatedSources.keys

        (previousGeneratedFiles - currentGeneratedFiles).forEach { relativePath ->
            val staleOutput = resolveOwnedOutput(outputRoot, relativePath)
            Files.deleteIfExists(staleOutput)
            deleteEmptyParentDirectories(staleOutput.parent, outputRoot)
        }

        generatedSources.forEach { (relativePath, classContent) ->
            val outputFile = resolveOwnedOutput(outputRoot, relativePath)
            Files.createDirectories(outputFile.parent)
            Files.writeString(outputFile, classContent, StandardCharsets.UTF_8)
        }

        updateManifest(outputRoot, currentGeneratedFiles)
    }

    internal fun hasConfiguredCatalogsOrPreviousOutputs(): Boolean {
        if (tomlFiles.files.isNotEmpty() || manifestFile().exists()) {
            return true
        }

        val outputRoot = outputDir.get().asFile.toPath().toAbsolutePath().normalize()
        return discoverLegacyGeneratedFiles(outputRoot).isNotEmpty()
    }

    private fun readPreviousGeneratedFiles(outputRoot: Path): Set<String> {
        val manifest = manifestFile()
        if (manifest.exists()) {
            return manifest.readLines(StandardCharsets.UTF_8)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toSet()
        }

        return discoverLegacyGeneratedFiles(outputRoot)
    }

    private fun discoverLegacyGeneratedFiles(outputRoot: Path): Set<String> {
        val outputRootFile = outputRoot.toFile()
        if (!outputRootFile.exists()) {
            return emptySet()
        }

        return outputRootFile.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension == "kt" }
            .filter { it.readText().contains(GENERATED_SOURCE_MARKER) }
            .map { generatedFile ->
                outputRoot.relativize(generatedFile.toPath().toAbsolutePath().normalize())
                    .toString()
                    .replace(File.separatorChar, '/')
            }
            .toSet()
    }

    private fun updateManifest(outputRoot: Path, generatedFiles: Set<String>) {
        val manifest = manifestFile().toPath()

        if (generatedFiles.isEmpty()) {
            Files.deleteIfExists(manifest)
            return
        }

        Files.createDirectories(outputRoot)
        Files.writeString(
            manifest,
            generatedFiles.sorted().joinToString(separator = "\n", postfix = "\n"),
            StandardCharsets.UTF_8
        )
    }

    private fun resolveOwnedOutput(outputRoot: Path, relativePath: String): Path {
        val outputFile = outputRoot.resolve(relativePath).normalize()
        if (!outputFile.startsWith(outputRoot)) {
            throw GradleException("Invalid entry in PoVerCat generated files manifest: $relativePath")
        }

        return outputFile
    }

    private fun deleteEmptyParentDirectories(startDirectory: Path?, outputRoot: Path) {
        var currentDirectory = startDirectory

        while (currentDirectory != null && currentDirectory != outputRoot) {
            val isEmpty = Files.isDirectory(currentDirectory) &&
                    Files.list(currentDirectory).use { entries -> entries.findAny().isEmpty }
            if (!isEmpty) {
                return
            }

            Files.deleteIfExists(currentDirectory)
            currentDirectory = currentDirectory.parent
        }
    }

    private fun manifestFile(): File =
        outputDir.file(GENERATED_FILES_MANIFEST).get().asFile

    companion object {
        internal const val GENERATED_FILES_MANIFEST = ".povercat-generated-files"
        private const val GENERATED_SOURCE_MARKER =
            "WARNING: This class is auto-generated by PoVerCat plugin."

        fun Project.generatePortableVersionCatalogTask(extension: PortableVersionCatalogGeneratorPluginExtension): TaskProvider<PortableVersionCatalogGeneratorPluginTask> {
            val projectVersionProvider = provider { version.toString() }

            return tasks.register<PortableVersionCatalogGeneratorPluginTask>("generatePortableVersionCatalog") {
                catalogPackage.set(extension.catalogPackage)
                projectVersion.set(projectVersionProvider)
                tomlFiles.setFrom(extension.tomlFiles)
                outputDir.set(extension.outputDir)

                onlyIf("No version catalog files configured") {
                    hasConfiguredCatalogsOrPreviousOutputs()
                }
            }
        }
    }
}
