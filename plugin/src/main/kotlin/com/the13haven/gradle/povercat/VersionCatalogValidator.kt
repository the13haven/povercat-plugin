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
import org.tomlj.TomlArray
import org.tomlj.TomlParseResult
import org.tomlj.TomlPosition
import org.tomlj.TomlTable
import java.io.File

/**
 * Validates the subset of the Gradle version-catalog format supported by PoVerCat.
 */
internal object VersionCatalogValidator {

    private val versionKeys = setOf("require", "strictly", "prefer", "reject", "rejectAll")
    private val libraryKeys = setOf("group", "name", "module", "version")
    private val pluginKeys = setOf("id", "version")

    fun validate(catalogFile: File, catalog: TomlParseResult) {
        if (catalog.hasErrors()) {
            val syntaxErrors = catalog.errors().joinToString(separator = "\n") { error ->
                "${error.position().render()}: ${error.message}"
            }
            throw invalidCatalog(catalogFile, syntaxErrors)
        }

        val errors = mutableListOf<String>()
        val versions = catalog.section("versions", errors)
        val libraries = catalog.section("libraries", errors)
        val plugins = catalog.section("plugins", errors)
        val bundles = catalog.section("bundles", errors)

        val versionAliases = versions
            ?.validateAliases("versions", errors)
            .orEmpty()
        val libraryAliases = libraries
            ?.validateAliases("libraries", errors)
            .orEmpty()
        plugins?.validateAliases("plugins", errors)
        bundles?.validateAliases("bundles", errors)

        versions?.validateVersions(errors)
        libraries?.validateLibraries(versionAliases, errors)
        plugins?.validatePlugins(versionAliases, errors)
        bundles?.validateBundles(libraryAliases, errors)

        if (errors.isNotEmpty()) {
            throw invalidCatalog(
                catalogFile,
                errors.joinToString(separator = "\n") { error -> "- $error" }
            )
        }
    }

    private fun TomlParseResult.section(
        sectionName: String,
        errors: MutableList<String>
    ): TomlTable? =
        when (val section = get(sectionName)) {
            null -> null
            is TomlTable -> section
            else -> {
                errors += "[$sectionName] must be a TOML table."
                null
            }
        }

    private fun TomlTable.validateAliases(
        sectionName: String,
        errors: MutableList<String>
    ): Set<String> {
        val aliases = keySet().sorted()
        val validAliases = aliases.filter { alias ->
            if (CatalogAlias.isValid(alias)) {
                true
            } else {
                errors +=
                    "${aliasLocation(sectionName, alias)} must match Gradle alias pattern " +
                        "'[a-z][a-zA-Z0-9_.-]+'."
                false
            }
        }

        validAliases.forEach { alias ->
            validateReservedAlias(sectionName, alias, errors)
        }

        validAliases
            .groupBy(CatalogAlias::normalize)
            .filterValues { matchingAliases -> matchingAliases.size > 1 }
            .forEach { (normalizedAlias, matchingAliases) ->
                errors +=
                    "[$sectionName] aliases ${matchingAliases.renderAliases(this)} " +
                        "normalize to the same Gradle alias '$normalizedAlias'. Rename one alias."
            }

        validAliases
            .groupBy(CatalogAlias::toAccessorName)
            .filterValues { matchingAliases ->
                matchingAliases
                    .map(CatalogAlias::normalize)
                    .distinct()
                    .size > 1
            }
            .forEach { (accessorName, matchingAliases) ->
                errors +=
                    "[$sectionName] aliases ${matchingAliases.renderAliases(this)} " +
                        "generate the same accessor '$accessorName'. Rename one alias."
            }

        return validAliases
            .map(CatalogAlias::normalize)
            .toSet()
    }

    private fun TomlTable.validateReservedAlias(
        sectionName: String,
        alias: String,
        errors: MutableList<String>
    ) {
        val normalizedAlias = CatalogAlias.normalize(alias)
        val segments = normalizedAlias.split('.')
        val location = aliasLocation(sectionName, alias)

        when {
            normalizedAlias in CatalogAlias.reservedAliasNames ->
                errors += "$location uses a reserved Gradle alias name '$normalizedAlias'."

            segments.any { segment -> segment in CatalogAlias.reservedJavaSegments } ->
                errors += "$location contains reserved Java name 'class'."

            sectionName == "libraries" &&
                segments.first() in CatalogAlias.forbiddenLibraryPrefixes ->
                errors +=
                    "$location starts with reserved library prefix '${segments.first()}'."
        }

        val accessorName = CatalogAlias.toAccessorName(alias)
        if (CatalogAlias.isReservedAccessor(accessorName)) {
            errors +=
                "$location generates reserved Kotlin accessor name '$accessorName'. " +
                    "Rename the alias."
        }
    }

    private fun TomlTable.aliasLocation(sectionName: String, alias: String): String =
        "[$sectionName].$alias (${inputPositionOf(listOf(alias)).render()})"

    private fun List<String>.renderAliases(table: TomlTable): String =
        sorted().joinToString(separator = ", ") { alias ->
            "'$alias' (${table.inputPositionOf(listOf(alias)).render()})"
        }

    private fun TomlTable.validateVersions(errors: MutableList<String>) {
        forEachEntry("versions") { alias, value, location ->
            when (value) {
                is String -> {
                    if (value.isBlank()) {
                        errors += "$location must not contain an empty version."
                    }
                }

                is TomlTable -> value.validateRichVersion(location, errors)
                else -> errors += "$location must be a string or a rich-version table."
            }
        }
    }

    private fun TomlTable.validateLibraries(
        versionAliases: Set<String>,
        errors: MutableList<String>
    ) {
        forEachEntry("libraries") { _, value, location ->
            when (value) {
                is String -> validateLibraryNotation(value, location, expectedParts = 3, errors)
                is TomlTable -> value.validateLibrary(location, versionAliases, errors)
                else -> errors += "$location must be a string or a library table."
            }
        }
    }

    private fun TomlTable.validateLibrary(
        location: String,
        versionAliases: Set<String>,
        errors: MutableList<String>
    ) {
        validateKnownKeys(libraryKeys, location, errors)

        val module = get("module")
        val group = get("group")
        val name = get("name")

        if (module != null) {
            if (group != null || name != null) {
                errors += "$location must use either 'module' or 'group'/'name', not both."
            }
            if (module is String) {
                validateLibraryNotation(module, "$location.module", expectedParts = 2, errors)
            } else {
                errors += "$location.module must be a string in 'group:name' format."
            }
        } else {
            validateRequiredString(group, "$location.group", errors)
            validateRequiredString(name, "$location.name", errors)
        }

        validateVersionReference(get("version"), location, versionAliases, errors)
    }

    private fun TomlTable.validatePlugins(
        versionAliases: Set<String>,
        errors: MutableList<String>
    ) {
        forEachEntry("plugins") { _, value, location ->
            if (value !is TomlTable) {
                errors += "$location must be a plugin table."
                return@forEachEntry
            }

            value.validateKnownKeys(pluginKeys, location, errors)
            validateRequiredString(value.get("id"), "$location.id", errors)
            validateVersionReference(value.get("version"), location, versionAliases, errors)
        }
    }

    private fun TomlTable.validateBundles(
        libraryAliases: Set<String>,
        errors: MutableList<String>
    ) {
        forEachEntry("bundles") { _, value, location ->
            if (value !is TomlArray) {
                errors += "$location must be an array of library aliases."
                return@forEachEntry
            }

            value.toList().forEachIndexed { index, libraryAlias ->
                when {
                    libraryAlias !is String ->
                        errors += "$location[$index] must be a library alias string."

                    !CatalogAlias.isValid(libraryAlias) ->
                        errors +=
                            "$location[$index] must match Gradle alias pattern " +
                                "'[a-z][a-zA-Z0-9_.-]+'."

                    CatalogAlias.normalize(libraryAlias) !in libraryAliases ->
                        errors += "$location references unknown library alias '$libraryAlias'."
                }
            }
        }
    }

    private fun validateVersionReference(
        versionValue: Any?,
        ownerLocation: String,
        versionAliases: Set<String>,
        errors: MutableList<String>
    ) {
        when (versionValue) {
            null -> Unit
            is String -> {
                if (versionValue.isBlank()) {
                    errors += "$ownerLocation.version must not be empty."
                }
            }

            is TomlTable -> {
                val reference = versionValue.get("ref")
                if (reference != null) {
                    versionValue.validateKnownKeys(setOf("ref"), "$ownerLocation.version", errors)
                    when {
                        reference !is String || reference.isBlank() ->
                            errors += "$ownerLocation.version.ref must be a non-empty string."

                        !CatalogAlias.isValid(reference) ->
                            errors +=
                                "$ownerLocation.version.ref must match Gradle alias pattern " +
                                    "'[a-z][a-zA-Z0-9_.-]+'."

                        CatalogAlias.normalize(reference) !in versionAliases ->
                            errors +=
                                "$ownerLocation references unknown version alias '$reference'."
                    }
                } else {
                    versionValue.validateRichVersion("$ownerLocation.version", errors)
                }
            }

            else -> errors += "$ownerLocation.version must be a string or a version table."
        }
    }

    private fun TomlTable.validateRichVersion(
        location: String,
        errors: MutableList<String>
    ) {
        validateKnownKeys(versionKeys, location, errors)

        listOf("require", "strictly", "prefer").forEach { key ->
            val value: Any? = get(key)
            if (value != null) {
                if (value !is String || value.isBlank()) {
                    errors += "$location.$key must be a non-empty string."
                }
            }
        }

        val rejectedVersions: Any? = get("reject")
        if (rejectedVersions != null) {
            when {
                rejectedVersions !is TomlArray ->
                    errors += "$location.reject must be an array of version strings."

                rejectedVersions.toList().any { it !is String || it.isBlank() } ->
                    errors += "$location.reject must contain only non-empty version strings."
            }
        }

        val rejectAll: Any? = get("rejectAll")
        if (rejectAll != null) {
            if (rejectAll !is Boolean) {
                errors += "$location.rejectAll must be a boolean."
            }
        }

        if (keySet().isEmpty()) {
            errors += "$location must define at least one version constraint."
        }
    }

    private fun TomlTable.validateKnownKeys(
        supportedKeys: Set<String>,
        location: String,
        errors: MutableList<String>
    ) {
        (keySet() - supportedKeys).sorted().forEach { unknownKey ->
            errors += "$location contains unsupported key '$unknownKey'."
        }
    }

    private fun validateLibraryNotation(
        notation: String,
        location: String,
        expectedParts: Int,
        errors: MutableList<String>
    ) {
        val parts = notation.split(':')
        if (parts.size != expectedParts || parts.any(String::isBlank)) {
            val format = if (expectedParts == 2) "group:name" else "group:name:version"
            errors += "$location must use non-empty '$format' notation."
        }
    }

    private fun validateRequiredString(
        value: Any?,
        location: String,
        errors: MutableList<String>
    ) {
        if (value !is String || value.isBlank()) {
            errors += "$location must be a non-empty string."
        }
    }

    private inline fun TomlTable.forEachEntry(
        sectionName: String,
        action: (alias: String, value: Any, location: String) -> Unit
    ) {
        entrySet().forEach { (alias, value) ->
            val position = inputPositionOf(listOf(alias))
            action(alias, value, "[$sectionName].$alias (${position.render()})")
        }
    }

    private fun TomlPosition?.render(): String =
        if (this == null) {
            "unknown position"
        } else {
            "line ${line()}, column ${column()}"
        }

    private fun invalidCatalog(catalogFile: File, details: String): GradleException =
        GradleException(
            "Invalid version catalog '${catalogFile.absolutePath}':\n$details"
        )
}
