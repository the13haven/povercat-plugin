rootProject.name = "gradle-povercat-plugin"

include("plugin")

val useMavenLocal = providers.gradleProperty("useMavenLocal")
    .map { it.toBooleanStrict() }
    .getOrElse(false)

dependencyResolutionManagement {

    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        if (useMavenLocal) {
            mavenLocal()
        }
        mavenCentral()
    }

    pluginManagement {
        repositories {
            if (useMavenLocal) {
                mavenLocal()
            }
            mavenCentral()
            gradlePluginPortal()
        }
    }
}
