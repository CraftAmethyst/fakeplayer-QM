import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.compile.JavaCompile
import io.github.hello09x.buildlogic.BuildConstants

plugins {
    `java-library`
}

group = "io.github.hello09x.fakeplayer"
version = providers.gradleProperty("revision").orElse("0.4.0").get()

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

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

configurations.configureEach {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
}

dependencies {
    add("compileOnly", "org.jetbrains:annotations:24.1.0")
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
