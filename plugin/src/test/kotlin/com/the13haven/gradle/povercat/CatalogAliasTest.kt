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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CatalogAliasTest {

    @Test
    fun `normalizes Gradle alias separators to dots`() {
        assertEquals("foo.bar", CatalogAlias.normalize("foo-bar"))
        assertEquals("foo.bar", CatalogAlias.normalize("foo_bar"))
        assertEquals("foo.bar", CatalogAlias.normalize("foo.bar"))
        assertEquals("foo..bar", CatalogAlias.normalize("foo-_bar"))
    }

    @Test
    fun `generates one flat camel-case accessor for every separator`() {
        assertEquals("fooBar", CatalogAlias.toAccessorName("foo-bar"))
        assertEquals("fooBar", CatalogAlias.toAccessorName("foo_bar"))
        assertEquals("fooBar", CatalogAlias.toAccessorName("foo.bar"))
        assertEquals("fooBar", CatalogAlias.toAccessorName("foo-_bar"))
    }

    @Test
    fun `validates Gradle-compatible alias characters`() {
        assertTrue(CatalogAlias.isValid("foo-bar_2.version"))
        assertFalse(CatalogAlias.isValid("foo*_bar"))
        assertFalse(CatalogAlias.isValid("Foo"))
        assertFalse(CatalogAlias.isValid("f"))
    }

    @Test
    fun `recognizes Kotlin keywords that cannot be generated as properties`() {
        assertTrue(CatalogAlias.isReservedAccessor("when"))
        assertTrue(CatalogAlias.isReservedAccessor("object"))
        assertFalse(CatalogAlias.isReservedAccessor("fooWhen"))
    }
}
