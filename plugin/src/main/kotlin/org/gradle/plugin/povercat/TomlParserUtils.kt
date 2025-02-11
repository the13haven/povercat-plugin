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

import org.gradle.internal.impldep.org.tomlj.TomlArray
import org.gradle.internal.impldep.org.tomlj.TomlTable
import java.util.*


/**
 * Utilities for parsing .toml file.
 *
 * @author ssidorov
 */
class TomlParserUtils {

    companion object {

        val emptyVersion = TomlVersion("", "", "", emptyList())

        fun toCamelCase(value: String): String {
            return value.split("-", "_", ".")
                .joinToString("") { part ->
                    part.replaceFirstChar {
                        if (it.isLowerCase())
                            it.titlecase(Locale.getDefault())
                        else
                            it.toString()
                    }
                }
                .replaceFirstChar {
                    it.lowercase(Locale.getDefault())
                }
        }

        fun parseVersion(versionExpression: Any): TomlVersion {
            if (versionExpression is TomlTable) {
                val rejectAll = versionExpression["rejectAll"]?.toString()?.toBoolean() ?: false

                if (rejectAll) {
                    return TomlVersion("", "", "", listOf("+"))
                }

                val require = versionExpression["require"]?.toString() ?: ""
                val strictly = versionExpression["strictly"]?.toString() ?: ""
                val prefer = versionExpression["prefer"]?.toString() ?: ""
                val reject = (versionExpression["reject"] as TomlArray)
                    .toList()
                    .map { it.toString() }
                    .toList()

                return TomlVersion(
                    require,
                    strictly,
                    prefer,
                    reject
                )
            } else {
                return TomlVersion(versionExpression.toString(), "", "", listOf())
            }
        }
    }

    data class TomlVersion(
        val requiredVersion: String,
        val strictVersion: String,
        val preferredVersion: String,
        val rejectedVersions: List<String>
    )
}