plugins {
    id("buildlogic.java-conventions")
}

dependencies {
    compileOnly(project(":fakeplayer-core"))
    compileOnly(project(":fakeplayer-api"))
}

description = "fakeplayer-v26_1"
