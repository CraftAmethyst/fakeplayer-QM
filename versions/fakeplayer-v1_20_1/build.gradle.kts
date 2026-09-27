import io.github.hello09x.buildlogic.NmsConventionExtension

plugins {
    id("fakeplayer.nms-conventions")
}

extensions.configure<NmsConventionExtension>("fakeplayerNms") {
    javaVersion.set(17)
    toolchainVersion.set(21)
    nmsVersion.set("1.20.1-R0.1-SNAPSHOT")
    lombok.set(true)
    coreProvided.set(false)
}

dependencies {
    api(project(":fakeplayer-core"))
}

description = "fakeplayer-v1_20_1"
