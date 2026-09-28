import io.github.hello09x.buildlogic.NmsConventionExtension

plugins {
    id("fakeplayer.nms-conventions")
}

extensions.configure<NmsConventionExtension>("fakeplayerNms") {
    javaVersion.set(21)
    toolchainVersion.set(25)
    nmsVersion.set("26.1.2-R0.1-SNAPSHOT")
    lombok.set(true)
    coreProvided.set(true)
    remappedMojang.set(false)
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:26.1.2-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-transport:4.2.7.Final")
}

description = "fakeplayer-v26_1"
