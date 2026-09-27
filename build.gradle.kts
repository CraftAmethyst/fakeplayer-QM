import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("com.gradleup.shadow") version "9.6.1" apply false
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21" apply false
}

val revisionValue = providers.gradleProperty("revision").orElse("0.4.0").get()

data class BuildVersions(
    val paperApi: String,
    val lombok: String,
    val devtools: String,
    val authlib: String,
    val commandApi: String,
    val brigadier: String,
    val nettyTransport: String,
    val commonsIo: String,
)

val versions = BuildVersions(
    paperApi = "1.21.7-R0.1-SNAPSHOT",
    lombok = "1.18.46",
    devtools = providers.gradleProperty("devtoolsVersion").orElse("0.1.7-SNAPSHOT").get(),
    authlib = "4.0.43",
    commandApi = "12.0.0",
    brigadier = "1.1.8",
    nettyTransport = "4.1.82.Final",
    commonsIo = "2.15.1",
)

fun Project.useJava(toolchainVersion: Int, releaseVersion: Int) {
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(toolchainVersion))
        sourceCompatibility = JavaVersion.toVersion(toolchainVersion)
        targetCompatibility = JavaVersion.toVersion(toolchainVersion)
    }
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(releaseVersion)
    }
}

data class NmsConfig(
    val javaVersion: Int,
    val nmsVersion: String,
    val lombok: Boolean,
    val coreProvided: Boolean,
    val toolchainVersion: Int = javaVersion,
    val dependsOn: String? = null,
    val transitive: Boolean = true,
    val remappedMojang: Boolean = true,
)

fun Project.configureNms(config: NmsConfig) {
    useJava(config.toolchainVersion, config.javaVersion)
    dependencies {
        add("compileOnly", "com.github.tanyaofei.devtools:devtools-core:${versions.devtools}")
        if (config.coreProvided) {
            add("compileOnly", project(":fakeplayer-core"))
        } else {
            add("implementation", project(":fakeplayer-core"))
        }
        add("compileOnly", project(":fakeplayer-api"))

        config.dependsOn?.let { dependencyPath ->
            val dependency = if (config.transitive) {
                project(dependencyPath)
            } else {
                project(mapOf("path" to dependencyPath, "transitive" to false))
            }
            add("compileOnly", dependency)
        }

        val mcVersion = config.nmsVersion.substringBefore("-R")
        val spigotCandidates = if (config.remappedMojang) {
            listOf(
                "${rootDir}/lib/spigot-${config.nmsVersion}-remapped-mojang.jar",
                "${rootDir}/lib/spigot-${mcVersion}-remapped-mojang.jar",
                "${rootDir}/lib/spigot-${config.nmsVersion}.jar",
                "${rootDir}/lib/spigot-${mcVersion}.jar",
            )
        } else {
            listOf(
                "${rootDir}/lib/spigot-${config.nmsVersion}.jar",
                "${rootDir}/lib/spigot-${mcVersion}.jar",
            )
        }
        val spigotJar = spigotCandidates.map(::file).firstOrNull { it.exists() }
        when {
            spigotJar != null -> add("compileOnly", files(spigotJar))
            config.remappedMojang -> add("compileOnly", "org.spigotmc:spigot:${config.nmsVersion}:remapped-mojang")
            else -> add("compileOnly", "org.spigotmc:spigot:${config.nmsVersion}")
        }

        if (config.lombok) {
            add("compileOnly", "org.projectlombok:lombok:${versions.lombok}")
            add("annotationProcessor", "org.projectlombok:lombok:${versions.lombok}")
        }
    }
}

allprojects {
    group = "io.github.hello09x.fakeplayer"
    version = revisionValue

    repositories {
        flatDir { dirs("${rootDir}/lib") }
        maven { url = uri("https://libraries.minecraft.net/") }
        maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") }
        maven { url = uri("https://hub.spigotmc.org/nexus/content/repositories/releases/") }
        maven { url = uri("https://maven.elmakers.com/repository/") }
        maven { url = uri("https://repo.extendedclip.com/content/repositories/placeholderapi/") }
        maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
        maven { url = uri("https://oss.sonatype.org/content/groups/public/") }
        maven { url = uri("https://repo.dmulloy2.net/repository/public/") }
        maven { url = uri("https://jitpack.io") }
        mavenCentral()
    }

    dependencies {
        components {
            withModule("org.spigotmc:spigot") {
                allVariants {
                    withCapabilities {
                        removeCapability("org.spigotmc", "spigot-api")
                    }
                }
            }
        }
    }
}

subprojects {
    apply(plugin = "java-library")

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    configurations.configureEach {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }

    dependencies {
        add("compileOnly", "org.jetbrains:annotations:24.1.0")
    }
}

project(":fakeplayer-api") {
    useJava(21, 17)
    dependencies {
        add("compileOnly", "io.papermc.paper:paper-api:${versions.paperApi}")
        add("compileOnly", "org.projectlombok:lombok:${versions.lombok}")
        add("annotationProcessor", "org.projectlombok:lombok:${versions.lombok}")
    }
}

project(":fakeplayer-core") {
    useJava(21, 17)
    dependencies {
        add("compileOnly", "io.papermc.paper:paper-api:${versions.paperApi}")
        add("compileOnly", "org.projectlombok:lombok:${versions.lombok}")
        add("annotationProcessor", "org.projectlombok:lombok:${versions.lombok}")
        add("compileOnly", project(":fakeplayer-api"))
        add("implementation", "com.github.tanyaofei.devtools:devtools-core:${versions.devtools}")
        add("implementation", "com.github.tanyaofei.devtools:devtools-command:${versions.devtools}")
        add("implementation", "com.github.tanyaofei.devtools:devtools-database:${versions.devtools}")
        add("compileOnly", "com.mojang:authlib:${versions.authlib}")
        add("compileOnly", "dev.jorel:commandapi-paper-core:${versions.commandApi}")
        add("compileOnly", "com.mojang:brigadier:${versions.brigadier}")
        add("compileOnly", "io.netty:netty-transport:${versions.nettyTransport}")
        add("compileOnly", "commons-io:commons-io:${versions.commonsIo}")
        add("compileOnly", "com.github.jikoo:OpenInv:5.3.0")
        add("compileOnly", "me.clip:placeholderapi:2.11.7")
    }

    tasks.named<ProcessResources>("processResources") {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(mapOf("revision" to revisionValue))
        }
    }
}

project(":fakeplayer-dist") {
    apply(plugin = "com.gradleup.shadow")
    useJava(21, 21)

    dependencies {
        listOf(
            ":fakeplayer-core",
            ":fakeplayer-api",
            ":fakeplayer-v1_20_1",
            ":fakeplayer-v1_20_2",
            ":fakeplayer-v1_20_3",
            ":fakeplayer-v1_20_4",
            ":fakeplayer-v1_20_5",
            ":fakeplayer-v1_20_6",
            ":fakeplayer-v1_21",
            ":fakeplayer-v1_21_1",
            ":fakeplayer-v1_21_3",
            ":fakeplayer-v1_21_4",
            ":fakeplayer-v1_21_5",
            ":fakeplayer-v1_21_6",
            ":fakeplayer-v1_21_7",
            ":fakeplayer-v1_21_8",
            ":fakeplayer-v1_21_9",
            ":fakeplayer-v1_21_10",
            ":fakeplayer-v1_21_11",
            ":fakeplayer-v26_1",
            ":fakeplayer-v26_2",
        ).forEach { add("implementation", project(it)) }
    }

    layout.buildDirectory.set(file("${rootDir}/target"))

    tasks.named<ShadowJar>("shadowJar") {
        archiveBaseName.set("fakeplayer")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")
    }
    tasks.named<Jar>("jar") { enabled = false }
    tasks.named("build") { dependsOn(tasks.named("shadowJar")) }

    val serverVersions = listOf(
        "1.20.1", "1.20.2", "1.20.6", "1.21", "1.21.1", "1.21.3", "1.21.4",
        "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
        "26.1", "26.1.1", "26.1.2", "26.2",
    )
    tasks.register<Copy>("copyToServers") {
        dependsOn(tasks.named("shadowJar"))
        val jarFile = tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }
        serverVersions.forEach { serverVersion ->
            into(file("${rootDir}/server-${serverVersion}/plugins"))
            from(jarFile) { rename { "fakeplayer.jar" } }
        }
    }
}

configureNms(project(":fakeplayer-v1_20_1"), NmsConfig(17, "1.20.1-R0.1-SNAPSHOT", true, false, 21))
configureNms(project(":fakeplayer-v1_20_2"), NmsConfig(17, "1.20.2-R0.1-SNAPSHOT", true, true, 21))
configureNms(project(":fakeplayer-v1_20_3"), NmsConfig(17, "1.20.4-R0.1-SNAPSHOT", false, true, 21, ":fakeplayer-v1_20_4"))
configureNms(project(":fakeplayer-v1_20_4"), NmsConfig(17, "1.20.4-R0.1-SNAPSHOT", true, true, 21))
configureNms(project(":fakeplayer-v1_20_5"), NmsConfig(17, "1.20.6-R0.1-SNAPSHOT", false, true, 21, ":fakeplayer-v1_20_6"))
configureNms(project(":fakeplayer-v1_20_6"), NmsConfig(17, "1.20.6-R0.1-SNAPSHOT", true, true, 21))
configureNms(project(":fakeplayer-v1_21"), NmsConfig(21, "1.21-R0.1-SNAPSHOT", true, true))
configureNms(project(":fakeplayer-v1_21_1"), NmsConfig(21, "1.21.1-R0.1-SNAPSHOT", false, true, dependsOn = ":fakeplayer-v1_21", transitive = false))
configureNms(project(":fakeplayer-v1_21_3"), NmsConfig(21, "1.21.3-R0.1-SNAPSHOT", true, true))
configureNms(project(":fakeplayer-v1_21_4"), NmsConfig(21, "1.21.4-R0.1-SNAPSHOT", true, true))
configureNms(project(":fakeplayer-v1_21_5"), NmsConfig(21, "1.21.5-R0.1-SNAPSHOT", true, true))
configureNms(project(":fakeplayer-v1_21_6"), NmsConfig(21, "1.21.6-R0.1-SNAPSHOT", true, true))
configureNms(project(":fakeplayer-v1_21_7"), NmsConfig(21, "1.21.7-R0.1-SNAPSHOT", false, true, dependsOn = ":fakeplayer-v1_21_6", transitive = false))
configureNms(project(":fakeplayer-v1_21_8"), NmsConfig(21, "1.21.8-R0.1-SNAPSHOT", false, true, dependsOn = ":fakeplayer-v1_21_6", transitive = false))
configureNms(project(":fakeplayer-v1_21_9"), NmsConfig(21, "1.21.10-R0.1-SNAPSHOT", true, true))
configureNms(project(":fakeplayer-v1_21_10"), NmsConfig(21, "1.21.10-R0.1-SNAPSHOT", false, true, dependsOn = ":fakeplayer-v1_21_9", transitive = false))
configureNms(project(":fakeplayer-v1_21_11"), NmsConfig(21, "1.21.11-R0.1-SNAPSHOT", false, true, dependsOn = ":fakeplayer-v1_21_9", transitive = false))
configureNms(project(":fakeplayer-v26_1"), NmsConfig(21, "26.1.2-R0.1-SNAPSHOT", true, true, 25, remappedMojang = false))

project(":fakeplayer-v26_2") {
    apply(plugin = "io.papermc.paperweight.userdev")
    useJava(25, 21)
    configurations.named("compileClasspath") {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
    dependencies {
        add("compileOnly", project(":fakeplayer-core"))
        add("compileOnly", project(":fakeplayer-api"))
        add("compileOnly", "org.projectlombok:lombok:${versions.lombok}")
        add("annotationProcessor", "org.projectlombok:lombok:${versions.lombok}")
    }
}
