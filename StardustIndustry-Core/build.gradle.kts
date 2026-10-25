import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import org.gradle.process.CommandLineArgumentProvider

plugins {
    `java-library`
    id("net.neoforged.gradle.userdev")
}

abstract class GenerateEmptyGameTestStructure : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        DataOutputStream(BufferedOutputStream(GZIPOutputStream(output.outputStream()))).use { nbt ->
            // A structure template is compressed binary NBT. It intentionally has
            // no blocks: the tests build every machine through the production APIs.
            nbt.writeByte(10) // root TAG_Compound
            nbt.writeUTF("")

            nbt.writeByte(3) // TAG_Int
            nbt.writeUTF("DataVersion")
            nbt.writeInt(3955) // Minecraft 1.21.1

            nbt.writeByte(9) // TAG_List<TAG_Int>
            nbt.writeUTF("size")
            nbt.writeByte(3)
            nbt.writeInt(3)
            nbt.writeInt(32)
            nbt.writeInt(8)
            nbt.writeInt(16)

            for (name in listOf("blocks", "palette", "entities")) {
                nbt.writeByte(9) // TAG_List<TAG_Compound>
                nbt.writeUTF(name)
                nbt.writeByte(10)
                nbt.writeInt(0)
            }

            nbt.writeByte(0) // end root compound
        }
    }
}

val generateEmptyGameTestStructure by tasks.registering(GenerateEmptyGameTestStructure::class) {
    outputFile.set(layout.buildDirectory.file(
        "generated/gametestResources/data/stardustindustry/structure/empty.nbt"
    ))
}

sourceSets.main {
    resources.srcDir("src/generated/resources")
    resources.srcDir(layout.buildDirectory.dir("generated/gametestResources"))
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(generateEmptyGameTestStructure)
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

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
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

    if (this is JavaExec) {
        argumentProviders.add(CommandLineArgumentProvider {
            System.getProperty("stardustindustry.forwardedClientArguments")
                ?.takeIf(String::isNotEmpty)
                ?.split(',')
                ?.map { argument ->
                    String(Base64.getUrlDecoder().decode(argument), Charsets.UTF_8)
                }
                ?: emptyList()
        })
    }
}

afterEvaluate {
    tasks.named<JavaExec>("runClient").configure {
        val runClientTask = this
        val generatedArguments = args.toList()
        doFirst {
            // Gradle applies the root task's --args option to this same-named
            // JavaExec too, replacing NeoGradle's required launch arguments.
            // Restore those generated arguments before appending the forwarded
            // user arguments below.
            if (!System.getProperty("stardustindustry.forwardedClientArguments").isNullOrEmpty()) {
                runClientTask.setArgs(generatedArguments)
            }
        }
    }
}

val cleanGameTestMekanismExtension by tasks.registering(Delete::class) {
    delete(fileTree(rootProject.file("run/gameTestServer/mods")) {
        include("stardustindustry-mekanismex-*.jar")
    })
}

val prepareGameTestMekanismExtension by tasks.registering(Copy::class) {
    dependsOn(cleanGameTestMekanismExtension)
    dependsOn(":StardustIndustry-MekanismEx:jar")
    from(project(":StardustIndustry-MekanismEx").layout.buildDirectory.dir("libs")) {
        include("stardustindustry-mekanismex-*.jar")
    }
    into(rootProject.file("run/gameTestServer/mods"))
}

tasks.matching { it.name == "runGameTestServer" }.configureEach {
    dependsOn(prepareGameTestMekanismExtension)
}
