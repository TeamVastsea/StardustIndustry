import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.options.Option
import org.gradle.language.jvm.tasks.ProcessResources
import java.security.MessageDigest
import java.util.Base64

plugins {
    base
    id("net.neoforged.gradle.userdev") version "7.1.39" apply false
}

data class ModModule(
    val id: String,
    val displayName: String,
    val archiveName: String,
)

abstract class WriteTextArtifact : DefaultTask() {
    @get:Input
    abstract val contents: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeArtifact() {
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(contents.get(), Charsets.UTF_8)
    }
}

abstract class VerifyJarSet : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val jarDirectory: DirectoryProperty

    @get:Input
    abstract val expectedNames: ListProperty<String>

    @TaskAction
    fun verify() {
        val actual = jarDirectory.get().asFile
            .listFiles { file -> file.isFile && file.extension == "jar" }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        val expected = expectedNames.get().sorted()
        check(actual == expected) {
            "Expected exactly ${expected.joinToString()}, staged ${actual.joinToString()}"
        }
    }
}

abstract class Sha256Artifact : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun writeChecksum() {
        val input = inputFile.get().asFile
        val digest = MessageDigest.getInstance("SHA-256")
        input.inputStream().buffered().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val checksum = digest.digest().joinToString("") { "%02x".format(it) }
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText("$checksum  ${input.name}\n", Charsets.US_ASCII)
    }
}

abstract class ForwardingRunClient : DefaultTask() {
    @get:Input
    @get:Optional
    abstract val clientArguments: Property<String>

    @Option(
        option = "args",
        description = "Command line arguments passed to the Minecraft client.",
    )
    fun setCommandLineArguments(arguments: String) {
        clientArguments.set(arguments)
    }

    @TaskAction
    fun prepareArguments() {
        val encoded = clientArguments.orNull
            ?.let(::splitCommandLine)
            ?.joinToString(",") { argument ->
                Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(argument.toByteArray(Charsets.UTF_8))
            }
            .orEmpty()
        System.setProperty("stardustindustry.forwardedClientArguments", encoded)
    }

    private fun splitCommandLine(commandLine: String): List<String> {
        val arguments = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false

        fun finishArgument() {
            if (current.isNotEmpty()) {
                arguments += current.toString()
                current.setLength(0)
            }
        }

        for (character in commandLine) {
            when {
                escaped -> {
                    current.append(character)
                    escaped = false
                }
                character == '\\' -> escaped = true
                quote != null && character == quote -> quote = null
                quote == null && (character == '\'' || character == '"') -> quote = character
                quote == null && character.isWhitespace() -> finishArgument()
                else -> current.append(character)
            }
        }
        check(quote == null) { "Unterminated quote in --args: $commandLine" }
        if (escaped) current.append('\\')
        finishArgument()
        return arguments
    }
}

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

// A NeoForge jar was once extracted into the repository root. Those directories
// are kept on disk as vanilla/NeoForge reference sources, but they are not part
// of the build: excluding them from every source set keeps Gradle from walking
// ~6k unrelated files on each configuration/compilation pass.
val unpackedReferenceDirs = listOf(
    "assets", "com", "data", "net", "mcp", "META-INF", "reports",
)

subprojects {
    val module = requireNotNull(modules[path]) { "Missing module metadata for $path" }

    pluginManager.withPlugin("java") {
        val rootAbs = rootDir.toPath().toAbsolutePath().normalize()
        val excludedRoots = unpackedReferenceDirs
            .map { rootAbs.resolve(it) }
            .filter { it.toFile().isDirectory }
        extensions.configure<SourceSetContainer> {
            configureEach {
                // Drop only files that actually sit under a stray root-level dump.
                // A plain exclude("com") also matched the module's OWN
                // src/main/java/com/... because source-set excludes work on paths
                // relative to each source root - that silently emptied every module
                // once (compileJava went NO-SOURCE while still reporting success).
                // Filtering on normalised absolute paths cannot make that mistake.
                java.filter.exclude { details ->
                    val file = details.file.toPath().toAbsolutePath().normalize()
                    excludedRoots.any { file.startsWith(it) }
                }
                resources.filter.exclude { details ->
                    val file = details.file.toPath().toAbsolutePath().normalize()
                    excludedRoots.any { file.startsWith(it) }
                }
            }
        }
    }

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
val modVersion = providers.gradleProperty("mod_version").get()
val minecraftVersion = providers.gradleProperty("minecraft_version").get()
val neoForgeVersion = providers.gradleProperty("neo_version").get()
val firstPartyBundleName = "StardustIndustry-$modVersion-mc$minecraftVersion-first-party"
val firstPartyJarNames = modules.values.map { "${it.archiveName}-$modVersion.jar" }.sorted()

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

val firstPartyDependencyNotes = layout.buildDirectory.file("generated/distribution/DEPENDENCIES.txt")
val generateFirstPartyDependencyNotes by tasks.registering(WriteTextArtifact::class) {
    group = "distribution"
    description = "Writes installation and external dependency notes for the first-party bundle."
    outputFile.set(firstPartyDependencyNotes)
    contents.set(
        """
        Stardust Industry $modVersion for Minecraft $minecraftVersion
        =============================================================

        This archive contains exactly four first-party jars:
        ${firstPartyJarNames.joinToString("\n        ") { "- mods/$it" }}

        Runtime baseline:
        - Minecraft $minecraftVersion
        - NeoForge $neoForgeVersion or newer in the same compatible line

        Module dependencies:
        - stardustindustry-core: required by every extension.
        - stardustindustry-utilsex: optional client integrations; JEI, Jade, WTHIT and
          The One Probe are optional and are not included in this archive.
        - stardustindustry-mekanismex: requires Mekanism 10.7.19 or newer.
        - stardustindustry-ae2ex: requires Applied Energistics 2 19.2.18 or newer
          (and the GuideME version required by that AE2 installation).

        Install only extension jars whose external dependencies are present. The SHA-256
        file beside the zip verifies the archive itself; it is not a mod jar.
        """.trimIndent() + "\n",
    )
}

val firstPartyStageDirectory = layout.buildDirectory.dir("staging/$firstPartyBundleName")
val stageFirstPartyDistribution by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Stages the four first-party jars and their dependency notes."
    dependsOn(collectModJars, generateFirstPartyDependencyNotes)
    from(layout.buildDirectory.dir("libs")) {
        include(firstPartyJarNames)
        into("mods")
    }
    from(firstPartyDependencyNotes)
    into(firstPartyStageDirectory)
}

val verifyFirstPartyDistribution by tasks.registering(VerifyJarSet::class) {
    group = "verification"
    description = "Verifies that the distribution contains exactly the four first-party jars."
    dependsOn(stageFirstPartyDistribution)
    jarDirectory.set(firstPartyStageDirectory.map { it.dir("mods") })
    expectedNames.set(firstPartyJarNames)
}

val firstPartyDistributionZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Builds a versioned zip containing all four first-party mod jars."
    dependsOn(verifyFirstPartyDistribution)
    archiveFileName.set("$firstPartyBundleName.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    into(firstPartyBundleName) {
        from(firstPartyStageDirectory)
    }
}

val firstPartyChecksumFile = layout.buildDirectory.file(
    "distributions/$firstPartyBundleName.zip.sha256",
)
val firstPartyDistributionChecksum by tasks.registering(Sha256Artifact::class) {
    group = "distribution"
    description = "Writes the SHA-256 checksum for the first-party distribution zip."
    dependsOn(firstPartyDistributionZip)
    inputFile.set(firstPartyDistributionZip.flatMap { it.archiveFile })
    outputFile.set(firstPartyChecksumFile)
}

tasks.register("packageFirstPartyMods") {
    group = "distribution"
    description = "Builds the versioned four-jar zip, dependency notes and SHA-256 checksum."
    dependsOn(firstPartyDistributionChecksum)
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

val runClient by tasks.registering(ForwardingRunClient::class) {
    group = "stardust industry"
    description = "Runs the client with Core and every extension module loaded."
    finalizedBy(":StardustIndustry-Core:runClient")
}

tasks.named("assemble") {
    dependsOn(collectModJars)
}
