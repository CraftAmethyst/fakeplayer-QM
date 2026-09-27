import org.gradle.api.JavaVersion
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import io.github.hello09x.buildlogic.BuildConstants

plugins {
    id("fakeplayer.java-conventions")
    id("io.papermc.paperweight.userdev")
}

paperweight {
    paperDevBundle("26.2.build.111-stable")
}

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
configurations.named("compileClasspath") {
    attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
}

dependencies {
    add("compileOnly", project(":fakeplayer-core"))
    add("compileOnly", project(":fakeplayer-api"))
    add("compileOnly", "org.projectlombok:lombok:${BuildConstants.lombok}")
    add("annotationProcessor", "org.projectlombok:lombok:${BuildConstants.lombok}")
}
