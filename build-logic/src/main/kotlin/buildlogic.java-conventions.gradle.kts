plugins {
    id("java-library")
    id("maven-publish")
}

repositories {
    mavenLocal()
    maven { url = uri("https://libraries.minecraft.net/") }
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://oss.sonatype.org/content/groups/public/") }
    maven { url = uri("https://repo.dmulloy2.net/repository/public/") }
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://repo.maven.apache.org/maven2/") }
}

group = "io.github.hello09x.fakeplayer"
version = "0.4.0"
java.sourceCompatibility = JavaVersion.VERSION_1_8

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"
}
