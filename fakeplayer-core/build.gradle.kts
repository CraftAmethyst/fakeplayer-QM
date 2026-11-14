plugins {
    id("fakeplayer.core-conventions")
}

dependencies {
    api(libs.com.github.tanyaofei.devtools.devtools.core)
    api(libs.com.github.tanyaofei.devtools.devtools.command)
    api(libs.com.github.tanyaofei.devtools.devtools.database)
}

description = "fakeplayer-core"


dependencies {
    testImplementation(project(":fakeplayer-api"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockito:mockito-core:5.15.2")
    testImplementation("io.papermc.paper:paper-api:1.21.7-R0.1-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test { useJUnitPlatform() }
