plugins {
    id("buildlogic.java-conventions")
    id("io.papermc.paperweight.userdev")
}

paperweight {
    paperDevBundle("26.2.build.111-stable")
}

dependencies {
    compileOnly(project(":fakeplayer-core"))
    compileOnly(project(":fakeplayer-api"))
}

description = "fakeplayer-v26_2"
