import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import pl.allegro.tech.build.axion.release.domain.hooks.HookContext

group = "com.l13.plugin"
project.version = scmVersion.version

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

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_22)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

gradlePlugin {
    plugins {
        register("PoVerCatPlugin") {
            displayName = "Portable Version Catalog Plugin (PoVerCat)"
            id = "com.l13.plugin.povercat"
            implementationClass = "org.gradle.plugin.povercat.PortableVersionCatalogGeneratorPlugin"
            version = project.version
        }
    }
}

publishing {
    afterEvaluate {
        publications.named<MavenPublication>("pluginMaven") {
            artifactId = "povercat"
        }
    }

    repositories {
        mavenLocal()

        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/SergiusSidorov/haven-gradle-convention")
            credentials {
                username = project.findProperty("publish.username") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("publish.token") as String? ?: System.getenv("TOKEN")
            }
        }
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
        suffix.set("SNAPSHOT")
        separator.set("-")
    }

    checks {
        uncommittedChanges.set(true)
        aheadOfRemote.set(true)
        snapshotDependencies.set(true)
    }

    hooks {
//        preRelease {
//            fileUpdate {
//                encoding = "utf-8"
//                file("README.md")
//                pattern = { previousVersion: String, _: HookContext -> "v$previousVersion" }
//                replacement = { currentVersion: String, _: HookContext -> "v$currentVersion" }
//            }
//        }

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
