plugins {
    `java-library`
    id("net.neoforged.gradle.userdev")
}

subsystems {
    conventions {
        runs {
            shouldDefaultRunsBeCreated(false)
        }
    }
}

dependencies {
    implementation("net.neoforged:neoforge:${rootProject.property("neo_version")}")
    implementation(project(":StardustIndustry-Core"))

    compileOnly(
        "mezz.jei:jei-${rootProject.property("minecraft_version")}-common-api:${rootProject.property("jei_version")}",
    )
    compileOnly(
        "mezz.jei:jei-${rootProject.property("minecraft_version")}-neoforge-api:${rootProject.property("jei_version")}",
    )
    compileOnly(files(rootProject.file("libs/jade-1.21.1-neoforge-15.10.6.jar")))
    compileOnly(files(rootProject.file("libs/wthit-1.21.1-neo-12.10.2.jar")))
    compileOnly(files(rootProject.file("libs/theoneprobe-1.21-neo-12.0.8.jar")))
}

tasks.named("compileJava") {
    dependsOn(rootProject.tasks.named("fetchCompatLibs"))
}
