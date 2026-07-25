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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KotlinSourceEscapingTest {

    @Test
    fun `wraps plain text as a Kotlin string literal`() {
        assertEquals(
            "\"plain version 1.2.3\"",
            KotlinSourceEscaping.stringLiteral("plain version 1.2.3")
        )
    }

    @Test
    fun `escapes Kotlin string syntax without changing its value`() {
        val value = "quote\" backslash\\ dollar\$"

        assertEquals(
            "\"quote\\\" backslash\\\\ dollar\\${'$'}\"",
            KotlinSourceEscaping.stringLiteral(value)
        )
    }

    @Test
    fun `escapes whitespace and control characters`() {
        val value = "\b\t\n\r\u0000\u000c\u007f\u0085\u2028\u2029"

        assertEquals(
            "\"\\b\\t\\n\\r\\u0000\\u000c\\u007f\\u0085\\u2028\\u2029\"",
            KotlinSourceEscaping.stringLiteral(value)
        )
    }

    @Test
    fun `preserves printable Unicode including supplementary characters`() {
        assertEquals(
            "\"Zażółć gęślą jaźń 😀\"",
            KotlinSourceEscaping.stringLiteral("Zażółć gęślą jaźń 😀")
        )
    }

    @Test
    fun `prevents project version from breaking generated KDoc`() {
        assertEquals(
            "1.0*&#47; next  end tail",
            KotlinSourceEscaping.kDocText("1.0*/\nnext\t\u0000end\u2028tail")
        )
    }
}
