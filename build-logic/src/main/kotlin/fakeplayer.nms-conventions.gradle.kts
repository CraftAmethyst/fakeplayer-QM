import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import io.github.hello09x.buildlogic.BuildConstants
import io.github.hello09x.buildlogic.NmsConventionExtension

plugins {
    id("fakeplayer.java-conventions")
}

val fakeplayerNms = extensions.create<NmsConventionExtension>("fakeplayerNms")

afterEvaluate {
    val javaVersion = fakeplayerNms.javaVersion.get()
    val toolchainVersion = fakeplayerNms.toolchainVersion.orElse(javaVersion).get()
    extensions.configure<JavaPluginExtension> {
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
        val candidates = if (fakeplayerNms.remappedMojang.get()) {
            listOf(
                "${rootDir}/lib/spigot-${nmsVersion}-remapped-mojang.jar",
                "${rootDir}/lib/spigot-${mcVersion}-remapped-mojang.jar",
                "${rootDir}/lib/spigot-${nmsVersion}.jar",
                "${rootDir}/lib/spigot-${mcVersion}.jar",
            )
        } else {
            listOf("${rootDir}/lib/spigot-${nmsVersion}.jar", "${rootDir}/lib/spigot-${mcVersion}.jar")
        }
        val spigotJar = candidates.map(::file).firstOrNull { it.exists() }
        when {
            spigotJar != null -> add("compileOnly", files(spigotJar))
            fakeplayerNms.remappedMojang.get() -> add("compileOnly", "org.spigotmc:spigot:${nmsVersion}:remapped-mojang")
            else -> add("compileOnly", "org.spigotmc:spigot:${nmsVersion}")
        }

        if (fakeplayerNms.lombok.get()) {
            add("compileOnly", "org.projectlombok:lombok:${BuildConstants.lombok}")
            add("annotationProcessor", "org.projectlombok:lombok:${BuildConstants.lombok}")
        }
    }
}
