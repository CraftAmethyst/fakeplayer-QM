package io.github.hello09x.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

abstract class DownloadSpigotJar : DefaultTask() {
    @get:Input
    abstract val minecraftVersion: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    abstract val javaExecutable: Property<String>

    @TaskAction
    fun download() {
        val version = minecraftVersion.get()
        val apiUrl = "https://mcjars.app/api/v3/builds/types/SPIGOT/versions/$version/latest?fields=installation"
        val metadata = URI(apiUrl).toURL().openConnection() as HttpURLConnection
        metadata.requestMethod = "GET"
        metadata.setRequestProperty("Accept", "application/json")
        metadata.connectTimeout = 30_000
        metadata.readTimeout = 30_000

        val body = try {
            check(metadata.responseCode in 200..299) {
                "MCJars API returned HTTP ${metadata.responseCode} for Minecraft $version"
            }
            metadata.inputStream.bufferedReader().use { it.readText() }
        } finally {
            metadata.disconnect()
        }
        val downloadUrl = Regex("\"type\"\\s*:\\s*\"download\"\\s*,\\s*\"url\"\\s*:\\s*\"([^\"]+)\"")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: throw GradleException("MCJars returned no Spigot download for Minecraft $version")

        val output = outputFile.get().asFile.toPath()
        Files.createDirectories(output.parent)
        val launcher = output.resolveSibling("launcher.jar")
        val temporary = launcher.resolveSibling("${launcher.fileName}.part")
        val temporaryOutput = output.resolveSibling("${output.fileName}.part")
        try {
            val download = URI(downloadUrl).toURL().openConnection() as HttpURLConnection
            download.connectTimeout = 30_000
            download.readTimeout = 120_000
            try {
                check(download.responseCode in 200..299) {
                    "MCJars download returned HTTP ${download.responseCode} for Minecraft $version"
                }
                download.inputStream.use { input ->
                    Files.newOutputStream(temporary).use { outputStream -> input.copyTo(outputStream) }
                }
            } finally {
                download.disconnect()
            }
            try {
                Files.move(temporary, launcher, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, launcher, StandardCopyOption.REPLACE_EXISTING)
            }

            val process = ProcessBuilder(javaExecutable.get(), "-jar", launcher.toString(), "--version")
                .directory(output.parent.toFile())
                .redirectErrorStream(true)
                .start()
            val processOutput = process.inputStream.bufferedReader().use { it.readText() }
            check(process.waitFor() == 0) {
                "Failed to extract Spigot $version from MCJars launcher: $processOutput"
            }

            val extracted = output.parent.resolve("versions/$version/spigot-$version.jar")
            check(Files.isRegularFile(extracted)) {
                "MCJars Spigot $version launcher produced no $extracted: $processOutput"
            }
            Files.copy(extracted, temporaryOutput, StandardCopyOption.REPLACE_EXISTING)
            try {
                Files.move(temporaryOutput, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporaryOutput, output, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
            Files.deleteIfExists(temporaryOutput)
            Files.deleteIfExists(launcher)
        }
        logger.lifecycle("Downloaded Spigot $version from MCJars: $output")
    }
}
