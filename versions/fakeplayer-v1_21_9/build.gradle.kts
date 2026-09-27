plugins {
    id("buildlogic.java-conventions")
}

dependencies {
    compileOnly(libs.org.spigotmc.spigot.x1)
    compileOnly(libs.org.projectlombok.lombok)
    compileOnly(project(":fakeplayer-core"))
    compileOnly(project(":fakeplayer-api"))
}

description = "fakeplayer-v1_21_9"
