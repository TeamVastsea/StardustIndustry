import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    base
    id("net.neoforged.gradle.userdev") version "7.1.39" apply false
}

data class ModModule(
    val id: String,
    val displayName: String,
    val archiveName: String,
)

val modules = mapOf(
    ":StardustIndustry-Core" to ModModule(
        id = "stardustindustry",
        displayName = "Stardust Industry",
        archiveName = "stardustindustry-core",
    ),
    ":StardustIndustry-UtilsEx" to ModModule(
        id = "stardustindustry_utilsex",
        displayName = "Stardust Industry: Utilities Extension",
        archiveName = "stardustindustry-utilsex",
    ),
    ":StardustIndustry-MekanismEx" to ModModule(
        id = "stardustindustry_mekanismex",
        displayName = "Stardust Industry: Mekanism Extension",
        archiveName = "stardustindustry-mekanismex",
    ),
    ":StardustIndustry-AE2Ex" to ModModule(
        id = "stardustindustry_ae2ex",
        displayName = "Stardust Industry: AE2 Extension",
        archiveName = "stardustindustry-ae2ex",
    ),
)

allprojects {
    group = rootProject.property("mod_group_id").toString()
    version = rootProject.property("mod_version").toString()
}

subprojects {
    val module = requireNotNull(modules[path]) { "Missing module metadata for $path" }

    repositories {
        mavenCentral()
        maven {
            name = "BlameJared"
            url = uri("https://maven.blamejared.com")
        }
        flatDir {
            name = "localCompatLibs"
            dirs(rootProject.file("libs"))
        }
    }

    layout.buildDirectory.set(
        file("${System.getProperty("java.io.tmpdir")}/stardustindustry-build/$name")
    )

    plugins.withId("java") {
        extensions.configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        }
        extensions.configure<BasePluginExtension> {
            archivesName.set(module.archiveName)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:deprecation")
    }

    tasks.withType<ProcessResources>().configureEach {
        val replaceProperties = mapOf(
            "minecraft_version" to rootProject.property("minecraft_version"),
            "minecraft_version_range" to rootProject.property("minecraft_version_range"),
            "neo_version" to rootProject.property("neo_version"),
            "loader_version_range" to rootProject.property("loader_version_range"),
            "mod_id" to module.id,
            "mod_name" to module.displayName,
            "mod_license" to rootProject.property("mod_license"),
            "mod_version" to rootProject.property("mod_version"),
        )
        inputs.properties(replaceProperties)
        filesMatching("META-INF/neoforge.mods.toml") {
            expand(replaceProperties)
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    tasks.configureEach {
        if (name.startsWith("run") || name.contains("BootstrapLauncher")) {
            notCompatibleWithConfigurationCache("NeoForge dev launch uses Task.project at execution time")
        }
    }

}

val fetchCompatLibs by tasks.registering(Exec::class) {
    group = "stardust industry"
    description = "Downloads optional mod APIs used to compile and run the extension modules."
    workingDir(rootDir)
    if (System.getProperty("os.name").lowercase().contains("windows")) {
        commandLine(
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            rootProject.file("scripts/fetch-hud-libs.ps1"),
        )
    } else {
        commandLine("bash", rootProject.file("scripts/fetch-hud-libs.sh"))
    }
}

val modulePaths = modules.keys.toList()
val extensionPaths = modulePaths.filterNot { it == ":StardustIndustry-Core" }
val localBuildRoot = file("${System.getProperty("java.io.tmpdir")}/stardustindustry-build")

val collectModJars by tasks.registering(Sync::class) {
    group = "build"
    description = "Collects every independently built Stardust Industry mod jar."
    dependsOn(modulePaths.map { "$it:jar" })
    modules.keys.forEach { modulePath ->
        from(File(localBuildRoot, "${modulePath.removePrefix(":")}/libs")) {
            include("*.jar")
            exclude("*-sources.jar")
        }
    }
    into(layout.buildDirectory.dir("libs"))
}

val cleanRunExtensionMods by tasks.registering(Delete::class) {
    delete(fileTree(rootProject.file("run/client/mods")) {
        include(
            "stardustindustry-utilsex-*.jar",
            "stardustindustry-mekanismex-*.jar",
            "stardustindustry-ae2ex-*.jar",
        )
    })
}

val prepareRunClientMods by tasks.registering(Copy::class) {
    group = "stardust industry"
    description = "Copies locally built extension jars into the shared client run directory."
    dependsOn(cleanRunExtensionMods)
    dependsOn(extensionPaths.map { "$it:jar" })
    extensionPaths.forEach { modulePath ->
        from(File(localBuildRoot, "${modulePath.removePrefix(":")}/libs")) {
            include("*.jar")
            exclude("*-sources.jar")
        }
    }
    into(rootProject.file("run/client/mods"))
}

tasks.named("assemble") {
    dependsOn(collectModJars)
}

tasks.register("runClient") {
    group = "stardust industry"
    description = "Runs the client with Core and every extension module loaded."
    dependsOn(":StardustIndustry-Core:runClient")
}
