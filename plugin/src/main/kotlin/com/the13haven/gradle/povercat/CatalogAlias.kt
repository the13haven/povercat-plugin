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

/**
 * Applies Gradle-compatible alias normalization and PoVerCat accessor naming.
 */
internal object CatalogAlias {

    private val separator = Regex("[_.-]")
    private val validAlias = Regex("[a-z][a-zA-Z0-9_.-]+")

    val reservedAliasNames: Set<String> = setOf("extensions", "convention")
    val reservedJavaSegments: Set<String> = setOf("class")
    val forbiddenLibraryPrefixes: Set<String> = setOf("bundles", "versions", "plugins")

    private val kotlinKeywords = setOf(
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while"
    )

    fun normalize(alias: String): String =
        alias.replace(separator, ".")

    fun toAccessorName(alias: String): String =
        normalize(alias)
            .split('.')
            .joinToString(separator = "") { segment ->
                segment.replaceFirstChar { character ->
                    character.uppercaseChar().toString()
                }
            }
            .replaceFirstChar { character ->
                character.lowercaseChar().toString()
            }

    fun isValid(alias: String): Boolean =
        validAlias.matches(alias)

    fun isReservedAccessor(accessorName: String): Boolean =
        accessorName in kotlinKeywords
}
