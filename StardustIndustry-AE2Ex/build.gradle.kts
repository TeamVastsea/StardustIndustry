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
    compileOnly(files(rootProject.file("libs/appliedenergistics2-19.2.18.jar")))
}

tasks.named("compileJava") {
    dependsOn(rootProject.tasks.named("fetchCompatLibs"))
}
