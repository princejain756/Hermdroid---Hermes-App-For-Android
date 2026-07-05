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

rootProject.name = "Hermroid"
include(":app", ":core:model", ":core:network", ":core:security", ":core:design", ":core:automation", ":feature:onboarding", ":feature:chat")
