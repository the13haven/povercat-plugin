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

import org.tomlj.TomlArray
import org.tomlj.TomlTable


/**
 * Utilities for parsing .toml file.
 *
 * @author ssidorov@the13haven.com
 */
internal object TomlParserUtils {
    private const val EMPTY_STRING = ""

    private const val VERSION_KEY = "version"
    private const val REF_KEY = "ref"
    private const val REJECT_ALL_KEY = "rejectAll"
    private const val REQUIRE_KEY = "require"
    private const val STRICTLY_KEY = "strictly"
    private const val PREFER_KEY = "prefer"
    private const val REJECT_KEY = "reject"
    private const val MODULE_KEY = "module"
    private const val GROUP_KEY = "group"
    private const val NAME_KEY = "name"
    private const val ID_KEY = "id"

    private val EMPTY_VERSION = TomlVersion()

    private val EMPTY_PLUGIN = TomlPlugin()

    private val REJECT_ALL_VERSION = TomlVersion(EMPTY_STRING, EMPTY_STRING, EMPTY_STRING, listOf("+"))

    fun toCamelCase(value: String): String =
        CatalogAlias.toAccessorName(value)

    fun parseVersion(versionValue: Any): TomlVersion {
        if (versionValue !is TomlTable) {
            return TomlVersion(versionValue.toString())
        }

        val rejectAll = versionValue.getBoolean(REJECT_ALL_KEY) { false }

        if (rejectAll) {
            return REJECT_ALL_VERSION
        }

        val require = versionValue.getString(REQUIRE_KEY) { EMPTY_STRING }
        val strictly = versionValue.getString(STRICTLY_KEY) { EMPTY_STRING }
        val prefer = versionValue.getString(PREFER_KEY) { EMPTY_STRING }
        val reject = versionValue.getArrayOrEmpty(REJECT_KEY)
            .toList()
            .map { it.toString() }

        return TomlVersion(
            require,
            strictly,
            prefer,
            reject
        )
    }

    fun parseLibrary(libraryValue: Any, versions: Map<String, TomlVersion>): TomlLibrary {
        if (libraryValue !is TomlTable) {
            return parseLibrarySimple(libraryValue.toString())
        }

        val version = parseInlineVersion(libraryValue.get(VERSION_KEY), versions)
        val module = libraryValue.getString(MODULE_KEY) { EMPTY_STRING }

        return if (module.isBlank()) {
            val group = libraryValue.getString(GROUP_KEY) { EMPTY_STRING }
            val name = libraryValue.getString(NAME_KEY) { EMPTY_STRING }

            TomlLibrary(group, name, version)
        } else {
            parseLibrarySimple(module, version)
        }
    }

    fun parseBundle(bundleValue: Any): List<String> =
        if (bundleValue is TomlArray) {
            bundleValue.toList()
                .map { toCamelCase(it.toString()) }
        } else {
            emptyList()
        }

    fun parsePlugin(pluginValue: Any, versions: Map<String, TomlVersion>): TomlPlugin =
        if (pluginValue is TomlTable) {
            val id = pluginValue.getString(ID_KEY) { EMPTY_STRING }
            val version = parseInlineVersion(pluginValue.get(VERSION_KEY), versions)

            TomlPlugin(id, version)
        } else {
            EMPTY_PLUGIN
        }

    private fun parseInlineVersion(versionPart: Any?, versions: Map<String, TomlVersion>): TomlVersion =
        when (versionPart) {
            null -> {
                // case 1: no version
                EMPTY_VERSION
            }

            is TomlTable -> {
                val refVersionKey = versionPart.getString(REF_KEY) { EMPTY_STRING }

                if (refVersionKey.isBlank()) {
                    // case 2: version as object
                    parseVersion(versionPart)
                } else {
                    // case 3: ref to version
                    versions.getOrDefault(
                        CatalogAlias.normalize(refVersionKey),
                        EMPTY_VERSION
                    )
                }
            }

            else -> {
                // case 4: simple version
                TomlVersion(versionPart.toString())
            }
        }

    private fun parseLibrarySimple(libraryExpression: String, defaultVersion: TomlVersion? = null): TomlLibrary {
        val libraryParts = libraryExpression.split(":")

        val version = defaultVersion
            ?: if (libraryParts.size == 3) {
                TomlVersion(libraryParts[2])
            } else {
                EMPTY_VERSION
            }

        return TomlLibrary(libraryParts[0], libraryParts[1], version)
    }

    data class TomlVersion(
        val requiredVersion: String,
        val strictVersion: String,
        val preferredVersion: String,
        val rejectedVersions: List<String>
    ) {
        constructor(requiredVersion: String) : this(requiredVersion, "", "", listOf())

        constructor() : this("")

        fun isEmpty(): Boolean =
            requiredVersion.isBlank()
                && strictVersion.isBlank()
                && preferredVersion.isBlank()
                && rejectedVersions.isEmpty()

        fun isNotEmpty(): Boolean = !isEmpty()
    }

    data class TomlLibrary(
        val group: String,
        val name: String,
        val version: TomlVersion
    ) {
        fun isEmpty(): Boolean = group.isBlank() || name.isBlank()

        fun isNotEmpty(): Boolean = !isEmpty()
    }

    data class TomlPlugin(
        val id: String,
        val version: TomlVersion
    ) {
        constructor() : this("", EMPTY_VERSION)

        fun isEmpty(): Boolean = id.isBlank() && version.isEmpty()

        fun isNotEmpty(): Boolean = !isEmpty()
    }
}
