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

import org.gradle.internal.impldep.com.google.gson.JsonParser


/**
 * Utilities for parsing .toml file.
 *
 * @author ssidorov
 */
class TomlParserUtils {

    companion object {
        fun parseVersion(versionExpression: String): TomlVersion {
            val versionJson = JsonParser.parseString(versionExpression)

            if (versionJson.isJsonPrimitive) {
                return TomlVersion(versionJson.asString, "", "", listOf())
            } else {
                val versionJsonObject = versionJson.asJsonObject
                val rejectAll = versionJsonObject.get("rejectAll")?.asBoolean ?: false

                if (rejectAll) {
                    return TomlVersion("", "", "", listOf("+"))
                }

                val require = versionJsonObject.get("require")?.asString ?: ""
                val strictly = versionJsonObject.get("strictly")?.asString ?: ""
                val prefer = versionJsonObject.get("prefer")?.asString ?: ""
                val reject = versionJsonObject.getAsJsonArray("reject")
                    ?.map { it.asString }
                    ?.toList() ?: listOf()

                return TomlVersion(
                    require,
                    strictly,
                    prefer,
                    reject
                )
            }
        }

        fun getVersion(version: TomlVersion): String {
            if (version.requiredVersion.isNotBlank()) {
                return version.requiredVersion
            }

            if (version.strictVersion.isNotBlank()) {
                return version.strictVersion
            }

            return version.preferredVersion
        }
    }

    data class TomlVersion(
        val requiredVersion: String,
        val strictVersion: String,
        val preferredVersion: String,
        val rejectedVersions: List<String>
    )
}