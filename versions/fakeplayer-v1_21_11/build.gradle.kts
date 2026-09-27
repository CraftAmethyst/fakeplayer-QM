import io.github.hello09x.buildlogic.NmsConventionExtension

plugins {
    id("fakeplayer.nms-conventions")
}

extensions.configure<NmsConventionExtension>("fakeplayerNms") {
    javaVersion.set(21)
    toolchainVersion.set(21)
    nmsVersion.set("1.21.11-R0.1-SNAPSHOT")
    lombok.set(false)
    coreProvided.set(true)
    dependsOn.set(":fakeplayer-v1_21_9")
    transitive.set(false)
}

dependencies {
    compileOnly(libs.io.papermc.paper.paper.api)
}

description = "fakeplayer-v1_21_11"
