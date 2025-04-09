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

import org.gradle.api.logging.Logging
import org.tomlj.Toml
import org.tomlj.TomlParseResult
import java.io.File
import java.time.LocalDate

/**
 * PoVerCat Plugin Generator.
 *
 * @author ssidorov@the13haven.com
 */
class PortableVersionCatalogClassGenerator {

    companion object {

        private val logger = Logging.getLogger(PortableVersionCatalogClassGenerator::class.java)

        @JvmStatic
        fun generateClass(file: File, catalogPackage: String, className: String, projectVersion: String): String =
            try {
                val tomlContent = file.readText()
                val toml = Toml.parse(tomlContent)

                val versions = toml.toMap("versions")
                val libraries = toml.toMap("libraries")
                val plugins = toml.toMap("plugins")
                val bundles = toml.toMap("bundles")

                val parsedVersions = java.util.HashMap<String, TomlParserUtils.TomlVersion>()

                buildString {
                    appendCopyright(this)
                    appendLine()
                    appendLine("package $catalogPackage")
                    appendLine()
                    appendImports(this)
                    appendLine()
                    appendClassJavaDoc(this, projectVersion)
                    appendLine("class $className private constructor() {")
                    appendLine()

                    generateVersionPart(this, versions, parsedVersions)

                    generateLibraryPart(this, libraries, parsedVersions)

                    generateBundlesPart(this, bundles)

                    generatePluginsPart(this, plugins, parsedVersions)

                    appendLine("}")
                }
            } catch (e: Exception) {
                logger.error("An error occurred while generation portable version catalog {}", className, e)

                throw e
            }


        private fun generateVersionPart(
            stringBuilder: StringBuilder,
            versions: Map<String, Any>,
            parsedVersions: HashMap<String, TomlParserUtils.TomlVersion>
        ) {
            if (versions.isNotEmpty()) {
                with(stringBuilder) {
                    appendLine("    object Versions {")

                    versions.forEach { (key, value) ->
                        val parsedVersion = TomlParserUtils.parseVersion(value)

                        if (parsedVersion.isNotEmpty()) {
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
                    }

                    appendLine("    }")
                    appendLine()
                }
            }
        }

        private fun generateLibraryPart(
            stringBuilder: StringBuilder,
            libraries: Map<String, Any>,
            parsedVersions: Map<String, TomlParserUtils.TomlVersion>
        ) {
            if (libraries.isNotEmpty()) {
                with(stringBuilder) {

                    appendLine("    object Libraries {")

                    libraries.forEach { (key, value) ->
                        val parsedLibrary = TomlParserUtils.parseLibrary(value, parsedVersions)

                        if (parsedLibrary.isNotEmpty()) {
                            appendLine()
                            appendLine("        @JvmStatic")
                            appendLine("        val ${TomlParserUtils.toCamelCase(key)}: MinimalExternalModuleDependency = DefaultMinimalDependency(")
                            appendLine("            DefaultModuleIdentifier.newId(\"${parsedLibrary.group}\", \"${parsedLibrary.name}\"),")
                            appendLine("            DefaultMutableVersionConstraint(")
                            appendLine("                DefaultImmutableVersionConstraint(")
                            appendLine("                    \"${parsedLibrary.version.preferredVersion}\",")
                            appendLine("                    \"${parsedLibrary.version.requiredVersion}\",")
                            appendLine("                    \"${parsedLibrary.version.strictVersion}\",")
                            if (parsedLibrary.version.rejectedVersions.isNotEmpty()) {
                                appendLine(
                                    "                    listOf(${
                                        parsedLibrary.version.rejectedVersions.joinToString(
                                            ", "
                                        ) { "\"${it}\"" }
                                    }),"
                                )
                            } else {
                                appendLine("                    emptyList<String>(),")
                            }
                            appendLine("                    null")
                            appendLine("                )")
                            appendLine("            )")
                            appendLine("        )")
                        }
                    }

                    appendLine("    }")
                    appendLine()
                }
            }
        }

        private fun generateBundlesPart(stringBuilder: StringBuilder, bundles: Map<String, Any>) {
            if (bundles.isNotEmpty()) {
                with(stringBuilder) {
                    appendLine("    object Bundles {")

                    val parsedBundles = java.util.HashMap<String, List<String>>()

                    bundles.forEach { (key, value) ->
                        val bundleLibraries = TomlParserUtils.parseBundle(value)

                        if (bundleLibraries.isNotEmpty()) {
                            val bundleName = TomlParserUtils.toCamelCase(key)
                            parsedBundles.put(bundleName, bundleLibraries)

                            appendLine()
                            appendLine("        @JvmStatic")
                            appendLine("        val $bundleName: ExternalModuleDependencyBundle = DefaultExternalModuleDependencyBundle()")
                        }
                    }

                    if (parsedBundles.isNotEmpty()) {
                        appendLine()
                        appendLine("        init {")

                        appendLine(
                            parsedBundles.map { (key, value) ->
                                value.joinToString("\n") { "            $key.add(Libraries.$it)" }
                            }
                                .joinToString("\n\n")
                        )

                        appendLine("        }")
                    }

                    appendLine("    }")
                    appendLine()
                }
            }
        }

        private fun generatePluginsPart(
            stringBuilder: StringBuilder,
            plugins: Map<String, Any>,
            parsedVersions: Map<String, TomlParserUtils.TomlVersion>
        ) {
            if (plugins.isNotEmpty()) {
                with(stringBuilder) {
                    appendLine("    object Plugins {")

                    plugins.forEach { (key, value) ->
                        val plugin = TomlParserUtils.parsePlugin(value, parsedVersions)

                        if (plugin.isNotEmpty()) {
                            appendLine()
                            appendLine("        @JvmStatic")
                            appendLine("        val ${TomlParserUtils.toCamelCase(key)}: PluginDependency = DefaultPluginDependency(")
                            appendLine("            \"${plugin.id}\",")
                            appendLine("            DefaultMutableVersionConstraint(")
                            appendLine("                DefaultImmutableVersionConstraint(")
                            appendLine("                    \"${plugin.version.preferredVersion}\",")
                            appendLine("                    \"${plugin.version.requiredVersion}\",")
                            appendLine("                    \"${plugin.version.strictVersion}\",")
                            if (plugin.version.rejectedVersions.isNotEmpty()) {
                                appendLine("                    listOf(${plugin.version.rejectedVersions.joinToString(", ") { "\"${it}\"" }}),")
                            } else {
                                appendLine("                    emptyList<String>(),")
                            }
                            appendLine("                    null")
                            appendLine("                )")
                            appendLine("            )")
                            appendLine("        )")
                        }
                    }

                    appendLine("    }")
                }
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
                appendLine("import org.gradle.api.artifacts.ExternalModuleDependencyBundle")
                appendLine("import org.gradle.api.artifacts.MinimalExternalModuleDependency")
                appendLine("import org.gradle.api.artifacts.VersionConstraint")
                appendLine("import org.gradle.api.internal.artifacts.DefaultModuleIdentifier")
                appendLine("import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint")
                appendLine("import org.gradle.api.internal.artifacts.dependencies.DefaultMinimalDependency")
                appendLine("import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint")
                appendLine("import org.gradle.api.internal.artifacts.dependencies.DefaultPluginDependency")
                appendLine("import org.gradle.api.internal.catalog.DefaultExternalModuleDependencyBundle")
                appendLine("import org.gradle.plugin.use.PluginDependency")
            }
        }

        private fun appendClassJavaDoc(stringBuilder: StringBuilder, projectVersion: String) {
            with(stringBuilder) {
                appendLine("/**")
                appendLine(" * <p><strong>WARNING: This class is auto-generated by PoVerCat plugin. Do not modify it manually.</strong></p>")
                appendLine(" *")
                appendLine(" * This class provides access to the version catalog of the Gradle project,")
                appendLine(" * which defines conventions for other projects. All dependencies and versions")
                appendLine(" * are statically embedded in this class and are immutable.")
                appendLine(" *")
                appendLine(" * <p>To update versions, modify them in the version catalog of the convention")
                appendLine(" * project and rebuild it.</p>")
                appendLine(" *")
                appendLine(" * @author PoVerCat plugin")
                appendLine(" * @version v${projectVersion}")
                appendLine(" */")
            }
        }

        private fun TomlParseResult.toMap(key: String): Map<String, Any> {
            return getTable(key)?.toMap() ?: emptyMap()
        }
    }
}