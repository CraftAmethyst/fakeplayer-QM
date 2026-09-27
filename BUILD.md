# Build / Dev Environment

## Prerequisites

- JDK 21 (required to run Gradle for this project)
- JDK 25 (required as a Gradle toolchain for Minecraft 26.1.x and 26.2 support)
- Git
- Internet access for Gradle dependency resolution

> Most modules compile with JDK 21. The 26.1.x and 26.2 NMS modules use a JDK 25 toolchain while still emitting Java 21 bytecode. Minecraft 26.2 servers themselves require Java 25.

## Setup

1) Clone the repo and open it in your IDE as a Gradle project.  
2) Set the Gradle JVM to JDK 21 (IntelliJ: Settings -> Build Tools -> Gradle -> Gradle JVM).  
3) If you build from CLI, ensure `JAVA_HOME` points to JDK 21 or newer and that a JDK 25 toolchain is installed.

The wrapper uses Gradle 9.4.1. Minecraft 26.2 is compiled against the Paper `26.2.build.111-stable` development bundle through Paperweight.

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

## Optional: local Spigot remapped jar

By default, Gradle resolves Spigot from its configured repositories. If you want to use a local jar instead:
1) Run BuildTools for the target version.
2) Put one of the following into `lib/`:
   - `spigot-<mcVersion>-remapped-mojang.jar`
   - `spigot-<mcVersion>.jar`

Gradle will automatically pick it up if present.

For Minecraft 26.1.x, run BuildTools with Java 25 for `26.1.2`; Spigot no longer publishes or needs a `remapped-mojang` classifier for this version line. Minecraft 26.2 uses the Paper development bundle and does not require a local Spigot jar.

## Optional: copy to local test servers

```
./gradlew :fakeplayer-dist:copyToServers
```

This copies the built jar to `server-<version>/plugins/fakeplayer.jar` (folders are created if missing).
