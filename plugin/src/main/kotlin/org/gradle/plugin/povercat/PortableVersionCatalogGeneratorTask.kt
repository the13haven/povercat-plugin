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
import org.gradle.internal.impldep.org.tomlj.TomlTable
import java.io.File
import java.time.LocalDate
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

        val outputDirectory = File(
            outputDir.get().asFile.path +
                    File.separator +
                    catalogPackage.get()
                        .split(".")
                        .joinToString(File.separator)
        )

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
            val className = TomlParserUtils.toCamelCase(tomlFile.nameWithoutExtension)
                .replaceFirstChar { it.uppercase(Locale.getDefault()) }

            val tomlContent = tomlFile.readText()
            val toml = Toml.parse(tomlContent)

            val versions = toml.toMap("versions")
            val libraries = toml.toMap("libraries")
            val plugins = toml.toMap("plugins")
            val bundles = toml.toMap("bundles")

            val parsedVersions = HashMap<String, TomlParserUtils.TomlVersion>()
            //val parsedLibraries = HashMap<String>

            val classContent = buildString {
                appendCopyright(this)
                appendLine()
                appendLine("package ${catalogPackage.get()}")
                appendLine()
                appendImports(this)
                appendLine()
                appendLine("class $className private constructor() {")
                appendLine()

                if (versions.isNotEmpty()) {
                    appendLine("    object Versions {")

                    versions.forEach { (key, value) ->
                        val parsedVersion = TomlParserUtils.parseVersion(value)
                        parsedVersions[key] = parsedVersion

                        appendLine()
                        appendLine("        @JvmStatic")
                        appendLine("        val ${TomlParserUtils.toCamelCase(key)}: VersionConstraint = DefaultImmutableVersionConstraint(")
                        appendLine("            \"${parsedVersion.preferredVersion}\",")
                        appendLine("            \"${parsedVersion.requiredVersion}\",")
                        appendLine("            \"${parsedVersion.strictVersion}\",")
                        if (parsedVersion.rejectedVersions.isNotEmpty()) {
                            appendLine("            listOf(${parsedVersion.rejectedVersions.joinToString(", ") { "\"${it}\"" }}),")
                        } else {
                            appendLine("            emptyList<String>(),")
                        }
                        appendLine("            null")
                        appendLine("        )")
                    }

                    appendLine("    }")
                    appendLine()
                }

                if (libraries.isNotEmpty()) {
                    appendLine("    object Libraries {")

                    libraries.forEach { (key, value) ->
                        val libData = value as TomlTable
                        val group = libData["group"]
                        val name = libData["name"]

                        // check 2 variants: 1/ version.ref 2/ version with object as value
                        val versionRef = libData["version.ref"]
                        val version = if (versionRef != null) {
                            parsedVersions[versionRef] ?: TomlParserUtils.emptyVersion
                        } else {
                            TomlParserUtils.parseVersion(libData["version"] ?: "")
                        }

                        appendLine()
                        appendLine("        @JvmStatic")
                        appendLine("        val ${TomlParserUtils.toCamelCase(key)}: MinimalExternalModuleDependency = DefaultMinimalDependency(")
                        appendLine("            DefaultModuleIdentifier.newId(\"${group}\", \"${name}\"),")
                        appendLine("            DefaultMutableVersionConstraint(")
                        appendLine("                DefaultImmutableVersionConstraint(")
                        appendLine("                    \"${version.preferredVersion}\",")
                        appendLine("                    \"${version.requiredVersion}\",")
                        appendLine("                    \"${version.strictVersion}\",")
                        if (version.rejectedVersions.isNotEmpty()) {
                            appendLine("                    listOf(${version.rejectedVersions.joinToString(", ") { "\"${it}\"" }}),")
                        } else {
                            appendLine("                    emptyList<String>(),")
                        }
                        appendLine("                    null")
                        appendLine("                )")
                        appendLine("            )")
                        appendLine("        )")
                    }

                    appendLine("    }")
                    appendLine()
                }

                if (plugins.isNotEmpty()) {
                    appendLine("    object Plugins {")

                    // plugins

                    appendLine("    }")
                    appendLine()
                }

                if (bundles.isNotEmpty()) {
                    appendLine("    object Bundles {")

                    // bundles

                    appendLine("    }")
                    appendLine()
                }

                appendLine("}")
            }

            val outputFile = File(outputDirectory, "$className.kt")
            outputFile.writeText(classContent)
        }
    }

    private fun appendCopyright(stringBuilder: StringBuilder) {
        with(stringBuilder) {
            appendLine("/*")
            appendLine(" * Copyright ${LocalDate.now().year}")
            appendLine(" *")
            appendLine(" * Licensed under the Apache License, Version 2.0 (the \"License\");")
            appendLine(" * you may not use this file except in compliance with the License.")
            appendLine(" * You may obtain a copy of the License at")
            appendLine(" *")
            appendLine(" * http://www.apache.org/licenses/LICENSE-2.0")
            appendLine(" *")
            appendLine(" * Unless required by applicable law or agreed to in writing, software distributed")
            appendLine(" * under the License is distributed on an \"AS IS\" BASIS, WITHOUT WARRANTIES")
            appendLine(" * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the")
            appendLine(" * specific language governing permissions and limitations under the License.")
            appendLine(" */")
        }
    }

    private fun appendImports(stringBuilder: StringBuilder) {
        with(stringBuilder) {
            appendLine("import org.gradle.api.artifacts.MinimalExternalModuleDependency")
            appendLine("import org.gradle.api.artifacts.VersionConstraint")
            appendLine("import org.gradle.api.internal.artifacts.DefaultModuleIdentifier")
            appendLine("import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint")
            appendLine("import org.gradle.api.internal.artifacts.dependencies.DefaultMinimalDependency")
            appendLine("import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint")
        }
    }

    private fun TomlParseResult.toMap(key: String): Map<String, Any> {
        return getTable(key)?.toMap() ?: emptyMap()
    }
}