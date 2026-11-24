pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "IGNGApp"

// Shared domain-agnostic foundation
include(
    ":core:model",
    ":core:designsystem",
    ":core:network",
    ":core:auth",
    ":core:data",
    ":core:markdown",
    ":core:updater",
)

// IGNG BBS domain modules (strictly isolated from MC / future Ink)
include(
    ":features:bbs:model",
    ":features:bbs:data",
    ":features:bbs:common",
    ":features:bbs:feed",
    ":features:bbs:column",
    ":features:bbs:post",
    ":features:bbs:inbox",
    ":features:bbs:creator",
    ":features:bbs:profile",
    ":features:bbs:admin",
)

// IGNGmc domain modules (strictly isolated from BBS / future Ink)
include(
    ":features:mc:model",
    ":features:mc:data",
    ":features:mc:common",
    ":features:mc:explore",
    ":features:mc:status",
    ":features:mc:map",
    ":features:mc:chat",
    ":features:mc:wiki",
    ":features:mc:player",
    ":features:mc:ticket",
    ":features:mc:admin",
)

// Independent Android application shells
include(
    ":apps:bbs",
    ":apps:mc",
)
