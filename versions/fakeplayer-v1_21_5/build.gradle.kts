import io.github.hello09x.buildlogic.NmsConventionExtension

plugins {
    id("fakeplayer.nms-conventions")
}

extensions.configure<NmsConventionExtension>("fakeplayerNms") {
    javaVersion.set(21)
    toolchainVersion.set(21)
    nmsVersion.set("1.21.5-R0.1-SNAPSHOT")
    lombok.set(true)
    coreProvided.set(true)
}

description = "fakeplayer-v1_21_5"
