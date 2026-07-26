# Gradle PoVerCat Generator Plugin

![PoVerCat banner](https://repository-images.githubusercontent.com/915818616/e467186d-18a8-4010-8d98-2ab41de9137b)

[![Release](https://img.shields.io/github/v/release/the13haven/povercat-plugin?sort=semver&display_name=release&style=flat-square&label=Release&logo=github)](https://github.com/the13haven/povercat-plugin/releases)
[![License](https://img.shields.io/badge/License-Apache_2.0-green.svg?style=flat-square&logo=github)](https://github.com/the13haven/povercat-plugin/blob/main/LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/the13haven/povercat-plugin/ci-auto-build.yml?branch=main&style=flat-square&label=Build&logo=githubactions)](https://github.com/the13haven/povercat-plugin/actions/workflows/ci-auto-build.yml)
[![CodeQL](https://img.shields.io/github/actions/workflow/status/the13haven/povercat-plugin/codeql.yml?branch=main&style=flat-square&label=CodeQL&logo=github)](https://github.com/the13haven/povercat-plugin/actions/workflows/codeql.yml)
[![Dependabot](https://img.shields.io/github/issues-search/the13haven/povercat-plugin?query=is%3Aopen%20author%3Adependabot&style=flat-square&logo=dependabot&label=Dependabot)](https://github.com/the13haven/povercat-plugin/pulls?q=is%3Apr+author%3Aapp%2Fdependabot)
[![Codecov](https://img.shields.io/codecov/c/gh/the13haven/povercat-plugin?token=DXGDRYHFAH&style=flat-square&logo=codecov&label=Coverage)](https://codecov.io/gh/the13haven/povercat-plugin)

[![FOSSA license status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fthe13haven%2Fpovercat-plugin.svg?type=shield&issueType=license)](https://app.fossa.com/projects/git%2Bgithub.com%2Fthe13haven%2Fpovercat-plugin?ref=badge_shield&issueType=license)
[![FOSSA security status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fthe13haven%2Fpovercat-plugin.svg?type=shield&issueType=security)](https://app.fossa.com/projects/git%2Bgithub.com%2Fthe13haven%2Fpovercat-plugin?ref=badge_shield&issueType=security)

## Overview

**PoVerCat** stands for **Po**rtable **Ver**sion **Cat**alog. It generates Kotlin
classes from TOML version catalogs so that a catalog can be compiled, published as
a regular dependency, and reused by both Kotlin and Java projects.

PoVerCat separates catalog generation from catalog consumption:

1. A **catalog producer** applies PoVerCat and a supported Kotlin plugin. PoVerCat
   validates the configured TOML files, generates Kotlin sources, adds them to the
   main source set, and runs generation before `compileKotlin`.
2. The producer publishes its normal JVM artifact containing the compiled catalog
   classes.
3. A **catalog consumer** adds that artifact as a dependency and uses the generated
   Gradle-native types. Consumers do not apply PoVerCat and do not need a Kotlin
   plugin.

### Features

- Generates a class for each configured TOML catalog.
- Supports version, library, bundle, and plugin entries, including rich versions.
- Exposes Gradle interfaces instead of PoVerCat-specific public DTOs.
- Produces an API that is callable from Kotlin and Java.
- Supports explicit class names and custom packages and output directories.
- Validates catalog structure, aliases, references, and generated-name collisions.
- Removes stale generated sources when catalogs are removed or renamed.
- Supports Gradle configuration cache reuse; generation outputs are deliberately
  excluded from the build cache because they contain the execution year.

## Requirements

The catalog producer must:

- run Gradle on **Java 21 or newer**;
- apply either the Gradle `kotlin-dsl` plugin or `org.jetbrains.kotlin.jvm`;
- provide every configured TOML file at generation time.

PoVerCat supports the latest stable Gradle version used by the current project
release. Older Gradle versions are not supported by default. If compatibility with
an older version is required, open a support request in the project's issue tracker.

The Kotlin plugin and PoVerCat may be declared in either order. PoVerCat waits for a
supported Kotlin plugin before configuring source sets and compilation tasks.

Consumer requirements are determined by the JVM target used when the producer
compiles and publishes its artifact. A consumer does not need PoVerCat or a Kotlin
plugin merely to use the compiled catalog classes, but those classes expose Gradle
interfaces and therefore must be used from code whose compile/runtime classpath
already contains the Gradle API, such as convention-plugin or other build-logic code.

## Quick start

PoVerCat is published to the Gradle Plugin Portal. The portal is available by
default in most builds. If your build declares plugin repositories explicitly,
include it in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}
```

Apply PoVerCat in the catalog producer's `build.gradle.kts`:

```kotlin
plugins {
    `kotlin-dsl`
    id("com.the13haven.povercat") version "0.1.0"
}
```

With no additional configuration, PoVerCat reads
`gradle/libs.versions.toml`, generates `LibsVersions` in the
`org.gradle.version.catalog` package, writes the source under
`build/generated/sources/povercat`, and adds that directory to the main source set.

Run generation directly when needed:

```shell
./gradlew generatePortableVersionCatalog
```

Normal Kotlin compilation runs the generation task automatically.

## Configuration

All settings are optional:

| Property | Type | Default | Purpose |
| --- | --- | --- | --- |
| `tomlFiles` | `ConfigurableFileCollection` | `gradle/libs.versions.toml` | Catalog files to generate from. Each TOML file produces one class. |
| `catalogPackage` | `Property<String>` | `org.gradle.version.catalog` | Package of all generated classes. |
| `outputDir` | `DirectoryProperty` | `build/generated/sources/povercat` | Generated-source root; PoVerCat registers it with the main source set. |
| `catalogClassNames` | `MapProperty<String, String>` | empty | Explicit class names keyed by project-relative or absolute TOML paths. |

Example:

```kotlin
portableVersionCatalog {
    tomlFiles.setFrom(
        "catalog/platform.versions.toml",
        "catalog/tooling.versions.toml"
    )
    catalogPackage.set("com.example.catalog")
    outputDir.set(layout.buildDirectory.dir("generated/sources/custom-catalogs"))
    catalogClassNames.put("catalog/platform.versions.toml", "PlatformVersions")
    catalogClassNames.put("catalog/tooling.versions.toml", "ToolingVersions")
}
```

### Input and task behavior

The default catalog is treated like any explicitly configured file: if it does not
exist, generation fails with a clear error. To intentionally disable generation,
configure an empty file collection:

```kotlin
portableVersionCatalog {
    tomlFiles.setFrom(emptyList<Any>())
}
```

When the collection is empty, the task is skipped if there are no previous outputs
to clean up. PoVerCat records its outputs in
`build/generated/sources/povercat/.povercat-generated-files` (or the configured
`outputDir`) and uses that manifest to remove obsolete generated classes. Do not
edit the manifest manually.

PoVerCat validates every catalog before source generation. This is important for
custom files that Gradle does not import as settings-level version catalogs.
Validation reports the catalog path, entry, and source position for problems such
as invalid TOML, malformed library or rich-version declarations, unknown
`version.ref` values, and unknown library aliases in bundles. Versionless libraries
and plugins remain supported.

PoVerCat intentionally does not generate accessors for empty bundles or
semantically empty rich-version declarations. Although Gradle can accept some such
entries, they contain no dependency or version data that portable build-logic code
can consume. Remove the entry or define at least one bundled library or effective
version constraint.

### Alias and class-name rules

For aliases and references, PoVerCat follows Gradle normalization rules: `-`, `_`,
and `.` are equivalent separators. Generated accessors are flat camel-case names,
so `foo-bar`, `foo_bar`, and `foo.bar` all map to `fooBar`. A version alias therefore
produces one property in the `Versions` object rather than nested accessors. `*` is
not a valid Gradle alias separator and is rejected rather than normalized.

PoVerCat rejects:

- aliases that become duplicates after Gradle normalization;
- distinct aliases that produce the same flat accessor;
- invalid alias characters and reserved Gradle, Java, or Kotlin names;
- multiple TOML files that resolve to the same generated class name.

By default, a class name is derived from the TOML file name. For example,
`libs-main.versions.toml` produces `LibsMainVersions`. Use `catalogClassNames` for a
stable explicit public name or to resolve a collision. Map keys must refer to files
present in `tomlFiles`; project-relative paths are recommended. Values must be valid
Kotlin and Java class names.

## Publishing and consuming a generated catalog

The generated sources belong to the producer's main source set, so its normal JVM
artifact contains the compiled catalog classes. Publish that artifact using the
producer's usual mechanism, such as Maven Publish.

PoVerCat supports two primary consumption scenarios.

### Scenario 1: published catalog conventions

A dedicated catalog-conventions project owns the TOML catalog and publishes it in
two forms:

1. a standard Gradle version-catalog artifact for normal catalog imports;
2. a JVM artifact containing the PoVerCat-generated portable class for compiled
   convention-plugin code.

The producer can build both artifacts from the same TOML file:

```kotlin
plugins {
    `kotlin-dsl`
    `version-catalog`
    `maven-publish`
    id("com.the13haven.povercat") version "0.1.0"
}

group = "com.haven"
version = "1.0.0"

repositories {
    mavenCentral()
}

catalog {
    versionCatalog {
        from(files("gradle/libs.versions.toml"))
    }
}

publishing {
    publications {
        create<MavenPublication>("gradleVersionCatalog") {
            artifactId = "company-version-catalog"
            from(components["versionCatalog"])
        }
        create<MavenPublication>("portableVersionCatalog") {
            artifactId = "company-version-catalog-portable"
            from(components["java"])
        }
    }
}
```

A consuming build imports the standard catalog in its `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    versionCatalogs {
        create("companyLibs") {
            from("com.haven:company-version-catalog:1.0.0")
        }
    }
}
```

When that consumer also has compiled convention plugins, its `build-logic` build
adds the portable artifact:

```kotlin
dependencies {
    implementation("com.haven:company-version-catalog-portable:1.0.0")
}
```

The generated class is then available when convention-plugin code needs catalog
versions while configuring other plugins programmatically.

### Scenario 2: local catalog and build logic in one repository

A repository can generate the portable catalog locally without publishing it. Keep
the catalog producer and convention plugins as separate included builds:

```text
.
├── settings.gradle.kts
├── version-catalog/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   └── gradle/libs.versions.toml
└── build-logic/
    ├── settings.gradle.kts
    ├── build.gradle.kts
    └── src/main/kotlin/
```

Give the catalog producer stable dependency coordinates in
`version-catalog/settings.gradle.kts` and `version-catalog/build.gradle.kts`:

```kotlin
// version-catalog/settings.gradle.kts
rootProject.name = "company-version-catalog-portable"

// version-catalog/build.gradle.kts
plugins {
    `kotlin-dsl`
    id("com.the13haven.povercat") version "0.1.0"
}

group = "com.haven"
version = "1.0.0"

repositories {
    mavenCentral()
}
```

Include the producer from `build-logic/settings.gradle.kts`:

```kotlin
includeBuild("../version-catalog")
```

Depend on its matching external coordinates from `build-logic/build.gradle.kts`.
Gradle composite-build dependency substitution replaces them with the local
included project:

```kotlin
dependencies {
    implementation("com.haven:company-version-catalog-portable:1.0.0")
}
```

Finally, include the convention-plugin build from the root `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("build-logic")
}
```

The convention-plugin source can now use catalog versions during programmatic
configuration. For example:

```kotlin
import org.gradle.version.catalog.LibsVersions

spotless {
    java {
        palantirJavaFormat(LibsVersions.Libraries.palantir.version!!)
            .formatJavadoc(true)
            .style("PALANTIR")

        importOrder("java", "", "com.haven")
        targetExclude("build/**")
    }
}
```

In both scenarios, the portable artifact is an implementation dependency of
compiled build logic. Adding it to a regular project's `implementation`
configuration does not make it available while compiling that same project's
`build.gradle.kts`.

The generated public API uses Gradle types:

| Catalog section | Generated type |
| --- | --- |
| `versions` | `VersionConstraint` |
| `libraries` | `MinimalExternalModuleDependency` |
| `bundles` | `Provider<ExternalModuleDependencyBundle>` |
| `plugins` | `PluginDependency` |

Rich versions preserve `require`, `strictly`, `prefer`, `reject`, and `rejectAll`.
Conversion follows Gradle's `MutableVersionConstraint` semantics: when both
`require` and `strictly` are present, applying `strictly` makes the strict value the
exposed required version while preferred and rejected versions remain available.

### Kotlin usage

From compiled build logic, library values can be passed directly to dependency
configurations. Bundle methods accept the project's `ObjectFactory` and return a
typed provider:

```kotlin
dependencies {
    implementation(LibsVersions.Libraries.jacocoTool)
    implementation(LibsVersions.Bundles.testing(objects))
}
```

Version values can also be read when configuring convention plugins or extensions:

```kotlin
fun configureExtensions(extensions: ExtensionContainer) {
    extensions.configure<JacocoPluginExtension> {
        toolVersion = LibsVersions.Libraries.jacocoTool.version!!
    }
}
```

### Java usage

The same compiled catalog is accessible from Java through the generated static
accessors:

```java
String jacocoVersion = LibsVersions.Libraries.getJacocoTool().getVersion();
```

## Contributing

Contributions are welcome. Run the complete verification suite before submitting a
change:

```shell
./gradlew clean build
```

Gradle's dependency cache is used for released artifacts. Maven Local is disabled
as a resolution repository by default so that locally published artifacts cannot
silently shadow released dependencies or plugins. Enable it only while testing
local publications:

```shell
./gradlew check -PuseMavenLocal=true
```

Update the Gradle Wrapper through the project script so that the distribution
checksum and all Wrapper files are refreshed and verified together:

```shell
./scripts/update-gradle-wrapper.sh 9.6.1
```

## License

This project is licensed under the Apache License 2.0. See [LICENSE](./LICENSE) for
details.
