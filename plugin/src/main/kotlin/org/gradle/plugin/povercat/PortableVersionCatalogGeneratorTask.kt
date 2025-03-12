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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.*

abstract class PortableVersionCatalogGeneratorTask : DefaultTask() {

    @get:Input
    abstract val catalogPackage: Property<String>

    @get:SkipWhenEmpty
    @get:InputFiles
    abstract val tomlFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun executeTask() {

        val outputDirectory = File(
            outputDir.get().asFile.path +
                    File.separator +
                    catalogPackage.get()
                        .split(".")
                        .joinToString(File.separator)
        )

        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs()
        }

        val tomlFiles = tomlFiles.files
            .map { filePath ->
                val tomlFile = project.file(filePath)
                if (!tomlFile.exists()) {
                    throw IllegalStateException("Version catalog file not found: ${tomlFile.absolutePath}")
                }

                tomlFile
            }
            .filter { file -> file.extension == "toml" }

        tomlFiles.forEach { tomlFile ->
            val className = TomlParserUtils.toCamelCase(tomlFile.nameWithoutExtension)
                .replaceFirstChar { it.uppercase(Locale.getDefault()) }

            val classContent = PortableVersionCatalogClassGenerator.generateClass(
                tomlFile,
                catalogPackage.get(),
                className,
                project.version.toString()
            )

            if (classContent.isNotBlank()) {
                val outputFile = File(outputDirectory, "$className.kt")
                outputFile.writeText(classContent)
            }
        }
    }
}