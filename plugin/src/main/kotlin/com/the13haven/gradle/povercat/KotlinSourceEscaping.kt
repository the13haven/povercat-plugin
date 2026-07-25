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
 * Escapes external values before embedding them in generated Kotlin source.
 */
internal object KotlinSourceEscaping {

    fun stringLiteral(value: String): String =
        buildString(value.length + 2) {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '$' -> append("\\$")
                    '\b' -> append("\\b")
                    '\t' -> append("\\t")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> {
                        if (character.requiresUnicodeEscape()) {
                            append("\\u")
                            append(character.code.toString(16).padStart(4, '0'))
                        } else {
                            append(character)
                        }
                    }
                }
            }
            append('"')
        }

    fun kDocText(value: String): String =
        value
            .replace("*/", "*&#47;")
            .map { character ->
                if (
                    character == '\n' ||
                    character == '\r' ||
                    character == '\u2028' ||
                    character == '\u2029' ||
                    character.requiresUnicodeEscape()
                ) {
                    ' '
                } else {
                    character
                }
            }
            .joinToString(separator = "")

    private fun Char.requiresUnicodeEscape(): Boolean =
        code in 0x0000..0x001f ||
                code in 0x007f..0x009f ||
                this == '\u2028' ||
                this == '\u2029'
}
