# Gradle POVERCAT Generator Plugin

![](https://repository-images.githubusercontent.com/915818616/e467186d-18a8-4010-8d98-2ab41de9137b)

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

<span style="color:orange;font-weight:900;">POVERCAT</span> is an abbreviation of the words <span style="color:orange;font-weight:900;">PO</span>rtable <span style="color:orange;font-weight:900;">VER</span>sion <span style="color:orange;font-weight:900;">CAT</span>alog

PoVerCat is a Gradle plugin that generates Kotlin classes from TOML-based version
catalogs in a catalog producer project. The producer publishes the compiled classes
as a regular dependency, which can then be used from both Kotlin and Java projects.

The catalog producer must apply the Gradle `kotlin-dsl` plugin or the Kotlin JVM
plugin because PoVerCat generates Kotlin source code. Consumer projects do not need
to use Kotlin: they consume the already compiled artifact.

## Features

* Automatic class generation from multiple TOML files
* Portable and reusable version catalog class
* Generated catalog artifacts are usable from both Kotlin and Java
* Seamless integration with Gradle projects
* Minimal settings for default case

## Compatibility model

PoVerCat distinguishes between the project that generates the catalog and the
projects that consume it:

* **Catalog producer** — a Gradle project that applies `kotlin-dsl` or
  `org.jetbrains.kotlin.jvm`. PoVerCat generates Kotlin source code, adds it to the
  main source set, and runs generation before `compileKotlin`.
* **Catalog consumer** — a Kotlin or Java project that declares a dependency on the
  artifact published by the producer. Consumers use compiled classes and therefore
  do not need to apply PoVerCat or the Kotlin plugin.

## Plugin usage

### Apply the plugin

To share a version catalog across multiple projects, you need to apply and configure the Povercat plugin in the project that defines the catalog.

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
    `kotlin-dsl`
    id("com.the13haven.povercat") version "0.1.0"
}
```

The Kotlin plugin and PoVerCat may be declared in either order. PoVerCat waits for a
supported Kotlin plugin before configuring source sets and compilation tasks.

### Configure the plugin (Optional)

By default, the plugin looks for `gradle/libs.versions.toml`, the standard version
catalog location. If the default file, or any explicitly configured catalog file,
does not exist, generation fails with an error. You can override the default or
specify multiple sources—each of which will be transformed into a separate Kotlin
class.

To intentionally disable generation, explicitly configure an empty file collection:

```kotlin
portableVersionCatalog {
    tomlFiles.setFrom(emptyList<Any>())
}
```

With an empty collection, the generation task is skipped when there are no outputs
to clean up. PoVerCat tracks the files it owns in
`build/generated/sources/.povercat-generated-files` and removes obsolete generated
classes when a configured catalog is removed or renamed. Do not edit this manifest
manually.

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

#### Configure generated class names

By default, each generated Kotlin class name is derived from its TOML file name.
For example, `libs-main.versions.toml` produces `LibsMainVersions`.

Use `catalogClassNames` when you need a stable, explicit public class name:

```kotlin
portableVersionCatalog {
    tomlFiles.setFrom(
        "catalog/team-a/libs-main.toml",
        "catalog/team-b/libs_main.toml"
    )

    catalogClassNames.put(
        "catalog/team-a/libs-main.toml",
        "TeamAVersions"
    )
    catalogClassNames.put(
        "catalog/team-b/libs_main.toml",
        "TeamBVersions"
    )
}
```

Keys may be project-relative paths (recommended) or absolute paths and must refer
to files present in `tomlFiles`. Values are the exact generated Kotlin class names
and must be valid Kotlin and Java identifiers.

If multiple TOML file names resolve to the same generated class name, generation
fails instead of silently overwriting a source file. Resolve the collision by
renaming the files or assigning unique names through `catalogClassNames`.

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

The generated public API uses Gradle types rather than PoVerCat-specific DTOs:

| Catalog section | Generated type |
| --- | --- |
| `versions` | `VersionConstraint` |
| `libraries` | `MinimalExternalModuleDependency` |
| `bundles` | `Provider<ExternalModuleDependencyBundle>` |
| `plugins` | `PluginDependency` |

This means library and bundle accessors can be passed to Gradle dependency
configurations. Bundle accessors accept an `ObjectFactory`; Gradle uses it to
create a typed provider with the same dependency-notation behavior as a standard
version-catalog bundle:

```kotlin
dependencies {
    implementation(LibsVersions.Libraries.jacocoTool)
    implementation(LibsVersions.Bundles.testing(objects))
}
```

PoVerCat keeps the parsed catalog definition private and converts it to the Gradle
types above. Rich versions retain `require`, `strictly`, `prefer`, `reject`, and
`rejectAll` data. Conversion follows Gradle's native `MutableVersionConstraint`
semantics: when both `require` and `strictly` are present, applying `strictly`
makes the strict value the exposed required version; the preferred and rejected
versions remain available through the same constraint.

Once the dependency is in place, Kotlin consumers can use the version values directly:

```kotlin
fun configureExtensions(extensions: ExtensionContainer, project: Project) {
    extensions.configure<JacocoPluginExtension> {
        toolVersion = LibsVersions.Libraries.jacocoTool.version!!
    }
}
```

The same compiled catalog can be used from Java:

```java
String jacocoVersion = LibsVersions.Libraries.getJacocoTool().getVersion();
```

This approach allows you to build convention plugins with preconfigured tools using centralized version definitions that are consistent across your project ecosystem.

## Contributing

We welcome contributions!

___
## License

This project is licensed under the Apache License 2.0. See the [LICENSE](./LICENSE) file for details.
