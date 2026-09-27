import io.github.hello09x.buildlogic.NmsConventionExtension

plugins {
    id("fakeplayer.nms-conventions")
}

extensions.configure<NmsConventionExtension>("fakeplayerNms") {
    javaVersion.set(17)
    toolchainVersion.set(21)
    nmsVersion.set("1.20.6-R0.1-SNAPSHOT")
    lombok.set(false)
    coreProvided.set(true)
    dependsOn.set(":fakeplayer-v1_20_6")
}

description = "fakeplayer-v1_20_5"
