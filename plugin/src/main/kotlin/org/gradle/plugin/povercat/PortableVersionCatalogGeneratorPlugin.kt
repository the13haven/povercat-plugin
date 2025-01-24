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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.register

class PortableVersionCatalogGeneratorPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        // register extension
        val extension = project.extensions.create(
            "versionCatalog",
            PortableVersionCatalogGeneratorPluginExtension::class.java,
            project
        )

        // register task
        project.tasks.register<PortableVersionCatalogGeneratorTask>("generateVersionCatalogClasses") {
            catalogPackage.set(extension.catalogPackage)
            tomlFiles.setFrom(extension.tomlFiles)
            outputDir.set(extension.outputDir)
        }

        // arrange tasks in a sequential chain
        project.tasks.named("compileKotlin") {
            dependsOn("generateVersionCatalogClasses")
        }

        project.extensions
            .getByType(org.gradle.api.tasks.SourceSetContainer::class.java)["main"]
            .java {
                srcDir(extension.outputDir)
            }
    }
}