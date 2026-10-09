plugins {
    `java-library`
    id("net.neoforged.gradle.userdev")
}

sourceSets.main {
    resources.srcDir("src/generated/resources")
}

configurations.named("runtimeClasspath") {
    extendsFrom(configurations.named("localRuntime").get())
}

dependencies {
    implementation("net.neoforged:neoforge:${rootProject.property("neo_version")}")

    // The root runClient task launches from Core and supplies the extension
    // modules as jars. Their external hard/soft dependencies live only on this
    // development runtime; Core source code does not compile against them.
    add(
        "localRuntime",
        "mezz.jei:jei-${rootProject.property("minecraft_version")}-neoforge:${rootProject.property("jei_version")}",
    )
    add("localRuntime", files(rootProject.file("libs/theoneprobe-1.21-neo-12.0.8.jar")))
    add("localRuntime", files(rootProject.file("libs/Mekanism-1.21.1-10.7.19.85.jar")))
    add("localRuntime", files(rootProject.file("libs/appliedenergistics2-19.2.18.jar")))
    add("localRuntime", files(rootProject.file("libs/guideme-21.1.19.jar")))
}

runs {
    configureEach {
        systemProperty("forge.logging.markers", "REGISTRIES")
        systemProperty("forge.logging.console.level", "debug")
        workingDirectory.set(rootProject.layout.projectDirectory.dir("run").dir(name))
        modSource(sourceSets.main.get())
    }

    named("client") {
        systemProperty("neoforge.enabledGameTestNamespaces", "stardustindustry")
    }

    named("server") {
        systemProperty("neoforge.enabledGameTestNamespaces", "stardustindustry")
        arguments.add("--nogui")
    }

    named("gameTestServer") {
        systemProperty("neoforge.enabledGameTestNamespaces", "stardustindustry")
    }

    named("data") {
        arguments.addAll(
            "--mod",
            "stardustindustry",
            "--all",
            "--output",
            file("src/generated/resources").absolutePath,
            "--existing",
            file("src/main/resources").absolutePath,
        )
    }
}

tasks.matching { it.name == "runClient" }.configureEach {
    dependsOn(rootProject.tasks.named("fetchCompatLibs"))
    dependsOn(rootProject.tasks.named("prepareRunClientMods"))
}
