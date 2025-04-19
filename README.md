# Gradle POVERCAT Generator Plugin

[![Release](https://img.shields.io/github/v/release/the13haven/povercat-plugin?sort=semver&display_name=release&style=flat-square&label=Release&logo=github)](https://github.com/the13haven/povercat-plugin/releases)
[![License](https://img.shields.io/badge/License-Apache_2.0-green.svg?style=flat-square&logo=github)](https://github.com/the13haven/povercat-plugin/blob/main/LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/the13haven/povercat-plugin/ci-auto-build.yml?branch=main&style=flat-square&label=Build&logo=githubactions)](https://github.com/the13haven/povercat-plugin/actions/workflows/ci-auto-build.yml)
[![CodeQL](https://img.shields.io/github/actions/workflow/status/the13haven/povercat-plugin/codeql.yml?branch=main&style=flat-square&label=CodeQL&logo=github)](https://github.com/the13haven/povercat-plugin/actions/workflows/codeql.yml)
[![Dependabot](https://img.shields.io/github/issues-search/the13haven/povercat-plugin?query=is%3Aopen%20author%3Adependabot&style=flat-square&logo=dependabot&label=Dependabot)](https://github.com/the13haven/povercat-plugin/pulls?q=is%3Apr+author%3Aapp%2Fdependabot)
[![Codecov](https://img.shields.io/codecov/c/gh/the13haven/povercat-plugin?token=DXGDRYHFAH&style=flat-square&logo=codecov&label=Coverage)](https://codecov.io/gh/the13haven/povercat-plugin)

[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fthe13haven%2Fpovercat-plugin.svg?type=shield&issueType=license)](https://app.fossa.com/projects/git%2Bgithub.com%2Fthe13haven%2Fpovercat-plugin?ref=badge_shield&issueType=license)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fthe13haven%2Fpovercat-plugin.svg?type=shield&issueType=security)](https://app.fossa.com/projects/git%2Bgithub.com%2Fthe13haven%2Fpovercat-plugin?ref=badge_shield&issueType=security)

---
## Overview

POVERCAT - stands for **PO**rtable **VER**sion **CAT**alog

PoVerCat is a Gradle plugin that generates a kotlin class from a TOML based version catalog and distribute it as a dependency.

## Features

* Automatic class generation from multiple TOML files
* Portable and reusable version catalog class
* Compatible with Java and Kotlin
* Seamless integration with Gradle projects
* Minimal settings for default case

## Plugin usage

### Apply the plugin

To share a version catalog across multiple projects, you need to apply and configure the PoVerCat plugin in the project that defines the catalog.

The plugin is published to the official Gradle Plugin Portal, so to use it, you must first ensure that the plugin portal repository is available in your project.

Add the following to your `settings.gradle.kts` to enable access to the Gradle Plugin Portal:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}
```

Then, apply the plugin in your `build.gradle.kts` file:

```kotlin
plugins {
    id("com.l13.plugin.povercat") version "0.1.0"
}
```

### Configure the plugin (Optional)

By default, the plugin looks for the `libs.versions.toml` file in the gradle directory, which is the standard location for the version catalog. However, you can override this behavior or specify multiple sources—each of which will be transformed into a separate Java class.

The default package for the generated classes is `org.gradle.version.catalog`. This too can be customized via plugin configuration.

By default, the generated source files are placed under `build/generated/sources`. You can also override this output directory if needed.

Below is an example of how to override the default settings in `build.gradle.kts`:

```kotlin
portableVersionCatalog {
    tomlFiles.setFrom("${projectDir.absolutePath}/catalog/libs-main.versions.toml")
    catalogPackage.set("com.example.catalog")
    outputDir.set(file("build/generated/sources"))
}
```

### Run the Task

By default, the plugin is executed automatically before the `compileKotlin` task. However, you can also trigger it manually using the `generatePortableVersionCatalog` task:

```shell
./gradlew generatePortableVersionCatalog
```

## Generated Version Catalog Usage

The generated class (classes) can be used both within the same project or in other projects. If you want to use it in a different project, you need to add a dependency on the artifact that contains the generated class.

For example, in your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("<group.name>:<artifact-name>:<version>")
}
```

Once the dependency is in place, you can use the version values directly in your code:

```kotlin
fun configureExtensions(extensions: ExtensionContainer, project: Project) {
    extensions.configure<JacocoPluginExtension> {
        toolVersion = LibsVersions.Libraries.jacocoTool.version!!
    }
}
```

This approach allows you to build convention plugins with preconfigured tools using centralized version definitions that are consistent across your project ecosystem.

## Contributing

We welcome contributions!

___
## License

This project is licensed under the Apache License 2.0. See the [LICENSE](./LICENSE) file for details.
