pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "StardustIndustry"

include(
    "StardustIndustry-Core",
    "StardustIndustry-UtilsEx",
    "StardustIndustry-MekanismEx",
    "StardustIndustry-AE2Ex",
)
