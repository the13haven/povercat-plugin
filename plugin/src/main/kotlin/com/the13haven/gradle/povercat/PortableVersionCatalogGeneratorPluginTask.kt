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
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
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
import javax.lang.model.SourceVersion

/**
 * PoVerCat Plugin Task.
 *
 * The current year is intentionally not a task input. Generated sources should receive a
 * fresh year only when another declared input changes; otherwise this task remains UP-TO-DATE.
 * For the same reason, its outputs must not be restored from a build cache across calendar years.
 *
 * @author ssidorov@the13haven.com
 */
@DisableCachingByDefault(
    because = "Generated source intentionally captures the execution year only when other inputs change"
)
abstract class PortableVersionCatalogGeneratorPluginTask : DefaultTask() {

    @get:Input
    abstract val catalogPackage: Property<String>

    @get:Input
    abstract val projectVersion: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tomlFiles: ConfigurableFileCollection

    @get:Input
    abstract val catalogClassNames: MapProperty<String, String>

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

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

        val catalogInputs = resolveCatalogInputs(filteredTomlFiles, packagePath)
        val generatedSources = catalogInputs.mapNotNull { catalogInput ->
            val classContent = PortableVersionCatalogClassGenerator.generateClass(
                catalogInput.file,
                catalogPackage.get(),
                catalogInput.className,
                projectVersion.get()
            )

            if (classContent.isNotBlank()) {
                catalogInput.relativeOutputPath to classContent
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

    private fun resolveCatalogInputs(tomlFiles: List<File>, packagePath: String): List<CatalogInput> {
        val projectRoot = normalizeFilePath(projectDirectory.get().asFile)
        val configuredFilePaths = tomlFiles.mapTo(mutableSetOf(), ::normalizeFilePath)
        val overridesByPath = resolveClassNameOverrides(projectRoot)
        val unknownOverrides = overridesByPath.keys - configuredFilePaths

        if (unknownOverrides.isNotEmpty()) {
            throw GradleException(
                unknownOverrides
                    .sortedBy(Path::toString)
                    .joinToString(
                        prefix = "Catalog class name configured for a file that is not in tomlFiles: ",
                        separator = ", "
                    )
            )
        }

        val catalogInputs = tomlFiles.map { tomlFile ->
            val className = overridesByPath[normalizeFilePath(tomlFile)]
                ?: defaultClassName(tomlFile)
            validateClassName(className, tomlFile)

            CatalogInput(
                file = tomlFile,
                className = className,
                relativeOutputPath = "$packagePath/${className}Catalog.kt"
            )
        }

        validateUniqueClassNames(catalogInputs)
        return catalogInputs
    }

    private fun resolveClassNameOverrides(projectRoot: Path): Map<Path, String> {
        val normalizedOverrides = catalogClassNames.get()
            .entries
            .groupBy { (configuredPath) ->
                val path = Path.of(configuredPath)
                normalizeFilePath(
                    (if (path.isAbsolute) path else projectRoot.resolve(path)).toFile()
                )
            }

        val duplicatePaths = normalizedOverrides.filterValues { it.size > 1 }
        if (duplicatePaths.isNotEmpty()) {
            throw GradleException(
                duplicatePaths.entries.joinToString(
                    prefix = "Multiple catalogClassNames entries refer to the same file: ",
                    separator = ", "
                ) { (path, entries) ->
                    "$path (${entries.joinToString { it.key }})"
                }
            )
        }

        return normalizedOverrides.mapValues { (_, entries) -> entries.single().value }
    }

    private fun defaultClassName(tomlFile: File): String =
        TomlParserUtils.toCamelCase(tomlFile.nameWithoutExtension)
            .replaceFirstChar { it.uppercase(Locale.ROOT) }

    private fun validateClassName(className: String, tomlFile: File) {
        if (
            !VALID_CLASS_NAME.matches(className) ||
            className in KOTLIN_KEYWORDS ||
            SourceVersion.isKeyword(className)
        ) {
            throw GradleException(
                "Invalid generated catalog class name '$className' for ${tomlFile.absolutePath}. " +
                        "Configure catalogClassNames with a valid Kotlin and Java class name."
            )
        }
    }

    private fun validateUniqueClassNames(catalogInputs: List<CatalogInput>) {
        val collisions = catalogInputs
            .groupBy { it.relativeOutputPath.lowercase(Locale.ROOT) }
            .values
            .filter { it.size > 1 }

        if (collisions.isEmpty()) {
            return
        }

        val details = collisions.joinToString(separator = "\n\n") { conflictingInputs ->
            val generatedNames = conflictingInputs
                .map(CatalogInput::className)
                .distinct()
                .joinToString()
            conflictingInputs
                .map { it.file.absolutePath }
                .sorted()
                .joinToString(
                    prefix = "Generated class '$generatedNames' conflicts for:\n",
                    separator = "\n"
                ) { "- $it" }
        }

        throw GradleException(
            "Multiple version catalogs generate conflicting class names:\n" +
                    "$details\n\n" +
                    "Rename the catalog files or configure unique names with catalogClassNames, for example:\n" +
                    "portableVersionCatalog {\n" +
                    "    catalogClassNames.put(\"path/to/catalog.toml\", \"CustomCatalogName\")\n" +
                    "}"
        )
    }

    private fun normalizeFilePath(file: File): Path =
        file.canonicalFile.toPath()

    internal fun hasConfiguredCatalogsOrPreviousOutputs(): Boolean {
        if (
            tomlFiles.files.isNotEmpty() ||
            catalogClassNames.get().isNotEmpty() ||
            manifestFile().exists()
        ) {
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
        private val VALID_CLASS_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private val KOTLIN_KEYWORDS = setOf(
            "as",
            "abstract",
            "actual",
            "annotation",
            "break",
            "by",
            "catch",
            "class",
            "companion",
            "const",
            "constructor",
            "continue",
            "crossinline",
            "data",
            "delegate",
            "do",
            "dynamic",
            "else",
            "enum",
            "expect",
            "external",
            "false",
            "field",
            "file",
            "final",
            "finally",
            "for",
            "fun",
            "get",
            "if",
            "import",
            "in",
            "infix",
            "init",
            "inline",
            "inner",
            "interface",
            "internal",
            "is",
            "lateinit",
            "noinline",
            "null",
            "object",
            "open",
            "operator",
            "out",
            "override",
            "package",
            "param",
            "private",
            "property",
            "protected",
            "public",
            "receiver",
            "reified",
            "return",
            "sealed",
            "set",
            "setparam",
            "super",
            "suspend",
            "tailrec",
            "this",
            "throw",
            "true",
            "try",
            "typealias",
            "typeof",
            "val",
            "var",
            "vararg",
            "when",
            "where",
            "while"
        )

        internal fun Project.generatePortableVersionCatalogTask(
            extension: PortableVersionCatalogGeneratorPluginExtension
        ): TaskProvider<PortableVersionCatalogGeneratorPluginTask> {
            val projectVersionProvider = provider { version.toString() }

            return tasks.register<PortableVersionCatalogGeneratorPluginTask>("generatePortableVersionCatalog") {
                catalogPackage.set(extension.catalogPackage)
                projectVersion.set(projectVersionProvider)
                tomlFiles.setFrom(extension.tomlFiles)
                catalogClassNames.set(extension.catalogClassNames)
                projectDirectory.set(layout.projectDirectory)
                outputDir.set(extension.outputDir)

                onlyIf("No version catalog files configured") {
                    hasConfiguredCatalogsOrPreviousOutputs()
                }
            }
        }
    }

    private data class CatalogInput(
        val file: File,
        val className: String,
        val relativeOutputPath: String
    )
}
