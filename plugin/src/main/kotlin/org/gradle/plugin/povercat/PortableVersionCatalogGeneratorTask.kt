/*
 *  Copyright 2025
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.gradle.plugin.povercat

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.impldep.org.tomlj.Toml
import org.gradle.internal.impldep.org.tomlj.TomlParseResult
import java.io.File
import java.util.*

abstract class PortableVersionCatalogGeneratorTask : DefaultTask() {

    @get:Input
    abstract val catalogPackage: Property<String>

    @get:SkipWhenEmpty
    @get:InputFiles
    abstract val tomlFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun executeTask() {
        val outputDirectory = outputDir.get().asFile

        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }

        val tomlFiles = tomlFiles.files.map { filePath ->
            val tomlFile = project.file(filePath)
            if (!tomlFile.exists()) {
                throw IllegalStateException("Version catalog file not found: ${tomlFile.absolutePath}")
            }

            tomlFile
        }

        tomlFiles.forEach { tomlFile ->
            val className = tomlFile.nameWithoutExtension.toCamelCase()
            val tomlContent = tomlFile.readText()
            val toml = Toml.parse(tomlContent)

            val versions = toml.toMap("versions")
            val libraries = toml.toMap("libraries")
            val plugins = toml.toMap("plugins")
            val bundles = toml.toMap("bundles")

            val parsedVersions = HashMap<String, TomlParserUtils.TomlVersion>()
            //val parsedLibraries = HashMap<String>

            val classContent = buildString {
                appendLine("package $catalogPackage")
                appendLine()
                appendImports(this)
                appendLine()
                appendLine("class $className private constructor() {")
                appendLine()
                appendLine("    object Versions {")

                versions.forEach { (key, value) ->
                    val parsedVersion = TomlParserUtils.parseVersion(value.toString())
                    parsedVersions[key] = parsedVersion

                    appendLine("        val ${key.toCamelCase()}: String = \"${TomlParserUtils.getVersion(parsedVersion)}\"")
                }

                appendLine("    }")
                appendLine()
                appendLine("    object Libraries {")

                libraries.forEach { (key, value) ->
                    val libData = value as Map<*, *>
                    val group = libData["group"]
                    val name = libData["name"]
                    val versionRef = libData["version.ref"]
                }

                appendLine("    }")
                appendLine("}")
            }

            val outputFile = File(outputDirectory, "$className.kt")
            outputFile.writeText(classContent)
        }
    }

    private fun appendImports(stringBuilder: StringBuilder) {
        with(stringBuilder) {
            appendLine("import org.gradle.api.artifacts.ExternalModuleDependencyBundle")
            appendLine("import org.gradle.api.artifacts.MinimalExternalModuleDependency")
            appendLine("import org.gradle.api.internal.artifacts.DefaultModuleIdentifier")
            appendLine("import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint")
            appendLine("import org.gradle.api.internal.artifacts.dependencies.DefaultMinimalDependency")
            appendLine("import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint")
            appendLine("import org.gradle.api.internal.artifacts.dependencies.DefaultPluginDependency")
            appendLine("import org.gradle.api.internal.catalog.DefaultExternalModuleDependencyBundle")
            appendLine("import org.gradle.api.internal.provider.DefaultProvider")
            appendLine("import org.gradle.plugin.use.PluginDependency")
        }
    }

    private fun TomlParseResult.toMap(key: String): Map<String, Any> {
        return getTable(key)?.toMap() ?: emptyMap()
    }

    private fun String.toCamelCase(): String {
        return split("-", "_", ".")
            .joinToString("") {
                replaceFirstChar {
                    if (it.isLowerCase())
                        it.titlecase(Locale.getDefault())
                    else it.toString()
                }
            }
            .replaceFirstChar {
                lowercase(Locale.getDefault())
            }
    }
}