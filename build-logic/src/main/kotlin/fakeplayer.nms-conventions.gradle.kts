import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaToolchainService
import io.github.hello09x.buildlogic.BuildConstants
import io.github.hello09x.buildlogic.DownloadSpigotJar
import io.github.hello09x.buildlogic.NmsConventionExtension

plugins {
    id("fakeplayer.java-conventions")
}

val fakeplayerNms = extensions.create<NmsConventionExtension>("fakeplayerNms")

afterEvaluate {
    val javaVersion = fakeplayerNms.javaVersion.get()
    val toolchainVersion = fakeplayerNms.toolchainVersion.orElse(javaVersion).get()
    val javaExtension = extensions.getByType<JavaPluginExtension>()
    javaExtension.apply {
        toolchain.languageVersion.set(JavaLanguageVersion.of(toolchainVersion))
        sourceCompatibility = JavaVersion.toVersion(toolchainVersion)
        targetCompatibility = JavaVersion.toVersion(toolchainVersion)
    }
    tasks.withType<JavaCompile>().configureEach { options.release.set(javaVersion) }

    dependencies {
        val devtoolsVersion = providers.gradleProperty("devtoolsVersion").orElse("0.1.7-SNAPSHOT").get()
        add("compileOnly", "com.github.tanyaofei.devtools:devtools-core:$devtoolsVersion")
        if (fakeplayerNms.coreProvided.get()) {
            add("compileOnly", project(":fakeplayer-core"))
        } else {
            add("implementation", project(":fakeplayer-core"))
        }
        add("compileOnly", project(":fakeplayer-api"))

        fakeplayerNms.dependsOn.orNull?.let { dependencyPath ->
            val dependency = if (fakeplayerNms.transitive.get()) {
                project(dependencyPath)
            } else {
                project(mapOf("path" to dependencyPath, "transitive" to false))
            }
            add("compileOnly", dependency)
        }

        val nmsVersion = fakeplayerNms.nmsVersion.get()
        val mcVersion = nmsVersion.substringBefore("-R")
        if (fakeplayerNms.remappedMojang.get()) {
            add("compileOnly", "org.spigotmc:spigot:${nmsVersion}:remapped-mojang")
        } else {
            val downloadSpigot = tasks.register<DownloadSpigotJar>("downloadSpigot") {
                minecraftVersion.set(mcVersion)
                outputFile.set(layout.buildDirectory.file("spigot/$mcVersion/spigot-$mcVersion.jar"))
                val toolchainService = project.extensions.getByType<JavaToolchainService>()
                javaExecutable.set(
                    toolchainService.launcherFor(javaExtension.toolchain)
                        .map { it.executablePath.asFile.absolutePath }
                )
                outputs.dir(layout.buildDirectory.dir("spigot/$mcVersion/libraries"))
            }
            add("compileOnly", files(downloadSpigot))
            // The downloaded Spigot launcher extracts the server's external dependencies
            // alongside the server jar. They are required when javac resolves signatures
            // from the server classes (for example DataFixerUpper and Authlib types).
            add(
                "compileOnly",
                fileTree(layout.buildDirectory.dir("spigot/$mcVersion/libraries")) {
                    include("*.jar")
                }.builtBy(downloadSpigot)
            )
        }

        if (fakeplayerNms.lombok.get()) {
            add("compileOnly", "org.projectlombok:lombok:${BuildConstants.lombok}")
            add("annotationProcessor", "org.projectlombok:lombok:${BuildConstants.lombok}")
        }
    }
}
