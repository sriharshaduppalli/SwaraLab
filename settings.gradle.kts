pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SwaraLab"

include(
    ":app",
    ":core:domain",
    ":core:session",
    ":core:ui",
    ":engine:audio",
    ":engine:render",
    ":feature:library",
    ":feature:studio",
    ":feature:publish",
    ":feature:account",
)
