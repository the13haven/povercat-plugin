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

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/**
 * PoVerCat Plugin Extension.
 *
 * @author ssidorov@the13haven.com
 */
open class PortableVersionCatalogGeneratorPluginExtension(project: Project) {

    val catalogPackage: Property<String> = project.objects
        .property(String::class.java)
        .convention("org.gradle.version.catalog")

    val tomlFiles: ConfigurableFileCollection = project.objects
        .fileCollection()
        .convention("gradle/libs.versions.toml")

    val outputDir: DirectoryProperty = project.objects
        .directoryProperty()
        .convention(
            project.objects
                .directoryProperty()
                .fileValue(
                    project.layout
                        .buildDirectory.dir("build/generated/sources")
                        .get()
                        .asFile
                )
        )

    companion object {
        fun Project.portableVersionCatalog(): PortableVersionCatalogGeneratorPluginExtension =
            extensions.create(
                "portableVersionCatalog",
                PortableVersionCatalogGeneratorPluginExtension::class.java,
                project
            )
    }
}