# Build / Dev Environment

**Write by GPT**

## Prerequisites

- JDK 21 (required for Gradle toolchain)
- Git
- Internet access for Gradle dependency resolution

> The project compiles with `--release 17`, but the Gradle toolchain is set to JDK 21.

## Setup

1) Clone the repo and open it in your IDE as a Gradle project.  
2) Set the Gradle JVM to JDK 21 (IntelliJ: Settings -> Build Tools -> Gradle -> Gradle JVM).  
3) If you build from CLI, ensure `JAVA_HOME` points to JDK 21.

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
- `target/fakeplayer-<version>.jar`

## Optional: local Spigot remapped jar

By default, Gradle will resolve Spigot from Maven. If you want to use a local jar instead:
1) Run BuildTools for the target version.
2) Put one of the following into `lib/`:
   - `spigot-<mcVersion>-remapped-mojang.jar`
   - `spigot-<mcVersion>.jar`

Gradle will automatically pick it up if present.

## Optional: copy to local test servers

```
./gradlew :fakeplayer-dist:copyToServers
```

This copies the built jar to `server-<version>/plugins/fakeplayer.jar` (folders are created if missing).
