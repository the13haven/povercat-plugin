# Gradle POVERCAT Generator Plugin v0.0.1

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

## Installation



## Usage

#### Apply the plugin
```kotlin
plugins {
    id("com.l13.plugin.povercat") version "<latest-version>"
}
```

#### Configure the plugin (Optional)

By default, the plugin looks for the libs.versions.toml file in the gradle directory, which is the standard location for the version catalog. However, you can override this behavior or specify multiple sources—each of which will be transformed into a separate Java class.

The default package for the generated classes is org.gradle.version.catalog. This too can be customized via plugin configuration.

By default, the generated source files are placed under build/generated/sources. You can also override this output directory if needed.

Below is an example of how to override the default settings:

```kotlin
portableVersionCatalog {
    tomlFiles.setFrom("${projectDir.absolutePath}/catalog/libs-main.versions.toml")
    catalogPackage.set("com.example.catalog")
    outputDir.set(file("build/generated/sources"))
}
```

### Run the Task

By default, the plugin is executed automatically before the **_compileKotlin_** task. However, you can also trigger it manually using the **_generatePortableVersionCatalog_** task:

```shell
./gradlew generatePortableVersionCatalog
```

### Contributing

We welcome contributions!

___
## License

This project is licensed under the Apache License 2.0. See the [LICENSE](./LICENSE) file for details.
