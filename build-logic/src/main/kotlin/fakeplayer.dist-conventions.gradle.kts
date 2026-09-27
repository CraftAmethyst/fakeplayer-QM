import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("fakeplayer.java-conventions")
    id("com.gradleup.shadow")
}

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }

dependencies {
    listOf(
        ":fakeplayer-core", ":fakeplayer-api", ":fakeplayer-v1_20_1", ":fakeplayer-v1_20_2",
        ":fakeplayer-v1_20_3", ":fakeplayer-v1_20_4", ":fakeplayer-v1_20_5", ":fakeplayer-v1_20_6",
        ":fakeplayer-v1_21", ":fakeplayer-v1_21_1", ":fakeplayer-v1_21_3", ":fakeplayer-v1_21_4",
        ":fakeplayer-v1_21_5", ":fakeplayer-v1_21_6", ":fakeplayer-v1_21_7", ":fakeplayer-v1_21_8",
        ":fakeplayer-v1_21_9", ":fakeplayer-v1_21_10", ":fakeplayer-v1_21_11", ":fakeplayer-v26_1",
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
    "1.20.1", "1.20.2", "1.20.6", "1.21", "1.21.1", "1.21.3", "1.21.4", "1.21.5",
    "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2",
)
tasks.register("copyToServers") {
    dependsOn(tasks.named("shadowJar"))
    doLast {
        val jarFile = tasks.named<ShadowJar>("shadowJar").get().archiveFile.get().asFile
        serverVersions.forEach { serverVersion ->
            val destination = file("${rootDir}/server-${serverVersion}/plugins")
            destination.mkdirs()
            project.copy {
                from(jarFile)
                into(destination)
                rename { "fakeplayer.jar" }
            }
        }
    }
}
