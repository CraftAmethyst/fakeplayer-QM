plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

includeBuild("build-logic")

rootProject.name = "fakeplayer-parent"

include(
    "fakeplayer-api",
    "fakeplayer-core",
    "fakeplayer-dist",
    "fakeplayer-v1_20_1",
    "fakeplayer-v1_20_2",
    "fakeplayer-v1_20_3",
    "fakeplayer-v1_20_4",
    "fakeplayer-v1_20_5",
    "fakeplayer-v1_20_6",
    "fakeplayer-v1_21",
    "fakeplayer-v1_21_1",
    "fakeplayer-v1_21_3",
    "fakeplayer-v1_21_4",
    "fakeplayer-v1_21_5",
    "fakeplayer-v1_21_6",
    "fakeplayer-v1_21_7",
    "fakeplayer-v1_21_8",
    "fakeplayer-v1_21_9",
    "fakeplayer-v1_21_10",
    "fakeplayer-v1_21_11",
    "fakeplayer-v26_1",
    "fakeplayer-v26_2",
)

file("versions").listFiles()
    ?.filter { it.isDirectory && it.name.startsWith("fakeplayer-v") }
    ?.forEach { versionProject ->
        project(":${versionProject.name}").projectDir = versionProject
    }
