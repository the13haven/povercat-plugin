import pl.allegro.tech.build.axion.release.domain.hooks.HookContext

plugins {
    `kotlin-dsl`
    `maven-publish`
    alias(libs.plugins.axion.release.plugin)
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

group = "org.gradle.plugin.povercat"
project.version = scmVersion.version

gradlePlugin {
    val greeting by plugins.creating {
        id = "org.gradle.povercat"
        implementationClass = "org.gradle.plugin.povercat.PortableVersionCatalogGeneratorPlugin"
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

scmVersion {
    localOnly.set(false)
    useHighestVersion.set(true)

    tag {
        prefix.set("v")
        initialVersion { _, _ -> "0.0.0" }
        releaseOnlyOnReleaseBranches = true
    }

    repository {
        type.set("git")
    }

    nextVersion {
        suffix.set("RC")
        separator.set("-")
    }

    checks {
        uncommittedChanges.set(true)
        aheadOfRemote.set(true)
        snapshotDependencies.set(true)
    }

    hooks {
        // workaround, will be replaced with preRelease hook and fileUpdate
        pre(
            "fileUpdate",
            mapOf(
                "encoding" to "utf-8",
                "file" to file("README.md"),
                "pattern" to KotlinClosure2({ previousVersion: String, _: HookContext -> "v$previousVersion" }),
                "replacement" to KotlinClosure2({ currentVersion: String, _: HookContext -> "v$currentVersion" })
            )
        )

        pre("commit")
    }
}
