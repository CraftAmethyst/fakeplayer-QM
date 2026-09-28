# Build / Dev Environment

## Prerequisites

- JDK 21 (required to run Gradle for this project)
- JDK 25 (required as a Gradle toolchain for Minecraft 26.1.x, 26.2, and 26.3 support)
- Git
- Internet access for Gradle dependency resolution

> Most modules compile with JDK 21. The 26.1.x, 26.2, and 26.3 NMS modules use a JDK 25 toolchain while still emitting Java 21 bytecode. Minecraft 26.2 and 26.3 servers themselves require Java 25.

## Setup

1) Clone the repo and open it in your IDE as a Gradle project.  
2) Set the Gradle JVM to JDK 21 (IntelliJ: Settings -> Build Tools -> Gradle -> Gradle JVM).  
3) If you build from CLI, ensure `JAVA_HOME` points to JDK 21 or newer and that a JDK 25 toolchain is installed.

The wrapper uses Gradle 9.4.1. Minecraft 26.2 is compiled against the Paper `26.2.build.111-stable` development bundle, and Minecraft 26.3 against `26.3.build.49-alpha`, through Paperweight.

## Project layout

- `fakeplayer-api`, `fakeplayer-core`, and `fakeplayer-dist` are the shared API, implementation, and distribution modules.
- Minecraft version implementations live under `versions/` while keeping their existing Gradle project paths.
- Shared Kotlin DSL convention plugins live under `build-logic/`.

## Build

Windows:
```
gradlew.bat build
```

macOS / Linux:
```
./gradlew build
```

### Build the plugin jar

```
./gradlew :fakeplayer-dist:shadowJar
```

Output:
- `target/libs/fakeplayer-<version>.jar`

## Spigot dependencies

Spigot NMS modules that use server-mapped classes resolve the latest Spigot build through the MCJars API. The launcher is downloaded, its embedded server jar is extracted, and the result is cached under the module's Gradle `build/` directory. Modules that require Mojang-mapped classes continue to use the `remapped-mojang` development artifact because the MCJars server download is a runtime Spigot jar, not a Mojang-mapped compile jar.

The API endpoint is:

```text
https://mcjars.app/api/v3/builds/types/SPIGOT/versions/<mcVersion>/latest?fields=installation
```

Paper 26.2 and 26.3 modules continue to use their Paperweight development bundles and do not download a Spigot jar.

## Optional: copy to local test servers

```
./gradlew :fakeplayer-dist:copyToServers
```

This copies the built jar to `server-<version>/plugins/fakeplayer.jar` (folders are created if missing).
