import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources
import io.github.hello09x.buildlogic.BuildConstants

plugins {
    id("fakeplayer.java-conventions")
}

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}
tasks.withType<JavaCompile>().configureEach { options.release.set(17) }

dependencies {
    add("compileOnly", "io.papermc.paper:paper-api:${BuildConstants.paperApi}")
    add("compileOnly", "org.projectlombok:lombok:${BuildConstants.lombok}")
    add("annotationProcessor", "org.projectlombok:lombok:${BuildConstants.lombok}")
    add("compileOnly", project(":fakeplayer-api"))
    val devtoolsVersion = providers.gradleProperty("devtoolsVersion").orElse("0.1.7-SNAPSHOT").get()
    add("implementation", "com.github.tanyaofei.devtools:devtools-core:$devtoolsVersion")
    add("implementation", "com.github.tanyaofei.devtools:devtools-command:$devtoolsVersion")
    add("implementation", "com.github.tanyaofei.devtools:devtools-database:$devtoolsVersion")
    add("compileOnly", "com.mojang:authlib:${BuildConstants.authlib}")
    add("compileOnly", "dev.jorel:commandapi-paper-core:${BuildConstants.commandApi}")
    add("compileOnly", "com.mojang:brigadier:${BuildConstants.brigadier}")
    add("compileOnly", "io.netty:netty-transport:${BuildConstants.nettyTransport}")
    add("compileOnly", "commons-io:commons-io:${BuildConstants.commonsIo}")
    add("compileOnly", "com.github.jikoo:OpenInv:${BuildConstants.openInv}")
    add("compileOnly", "me.clip:placeholderapi:${BuildConstants.placeholderApi}")
}

tasks.named<ProcessResources>("processResources") {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(mapOf("revision" to project.version.toString()))
    }
}
