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

import com.the13haven.gradle.povercat.PortableVersionCatalogGeneratorPluginExtension.Companion.portableVersionCatalog
import com.the13haven.gradle.povercat.PortableVersionCatalogGeneratorPluginTask.Companion.generatePortableVersionCatalogTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer


/**
 * PoVerCat Plugin.
 *
 * @author ssidorov@the13haven.com
 */
abstract class PortableVersionCatalogGeneratorPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        // register extension
        val extension = project.portableVersionCatalog()

        // register task
        val task = project.generatePortableVersionCatalogTask(extension)

        var kotlinIntegrationConfigured = false

        fun configureKotlinIntegration() {
            if (kotlinIntegrationConfigured) {
                return
            }

            kotlinIntegrationConfigured = true

            project.tasks.named("compileKotlin") {
                dependsOn(task)
            }

            project.extensions
                .getByType(SourceSetContainer::class.java)
                .named("main") {
                    java.srcDir(task.flatMap { it.outputDir })
                }
        }

        SUPPORTED_KOTLIN_PLUGIN_IDS.forEach { pluginId ->
            project.pluginManager.withPlugin(pluginId) {
                configureKotlinIntegration()
            }
        }

        project.afterEvaluate {
            if (!kotlinIntegrationConfigured) {
                throw GradleException(KOTLIN_PLUGIN_REQUIRED_MESSAGE)
            }
        }
    }

    companion object {
        private val SUPPORTED_KOTLIN_PLUGIN_IDS = listOf(
            "org.gradle.kotlin.kotlin-dsl",
            "org.jetbrains.kotlin.jvm"
        )

        internal const val KOTLIN_PLUGIN_REQUIRED_MESSAGE =
            "PoVerCat requires the Kotlin DSL ('kotlin-dsl') or Kotlin JVM " +
                    "('org.jetbrains.kotlin.jvm') plugin in the catalog producer project."
    }
}
