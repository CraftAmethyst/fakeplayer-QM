plugins {
    id("buildlogic.java-conventions")
}

dependencies {
    compileOnly(libs.io.papermc.paper.paper.api)
    compileOnly(libs.org.projectlombok.lombok)
}

description = "fakeplayer-api"
