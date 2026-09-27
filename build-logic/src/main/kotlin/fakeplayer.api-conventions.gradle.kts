import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
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
}
