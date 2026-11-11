# ORBIT

**MUSIC. SPACE. ATMOSPHERE.**

ORBIT is a client-side Fabric mod project for Minecraft 1.21.11. It is being developed in phases toward a local music player and an atmospheric visual experience.

## Current status

This branch establishes the Phase 1 project foundation only:

- ORBIT mod identity (`orbit`) and `dev.orbit` Java package
- Common and client Fabric initializers
- Java 21 toolchain and official Mojang mappings
- Split common/client source sets

The music player, custom interface, lyrics, HUD, and world sky renderer are **not implemented yet**. They should not be considered available until built and tested in later phases.

## Development setup

- Minecraft 1.21.11
- Java 21 JDK
- Fabric Loader 0.18.1
- Fabric API 0.141.6+1.21.11
- Fabric Loom 1.14.7
- Gradle wrapper 8.14.3

Clone the repository and build with:

```sh
./gradlew build
```

On Windows, use `gradlew.bat build`.

To launch the development client after resolving the Gradle dependencies:

```sh
./gradlew runClient
```

Install the resulting `orbit-0.1.0.jar` from `build/libs` into the `mods` folder of a Fabric 1.21.11 installation, together with Fabric API.

## Controls

No ORBIT controls are registered in Phase 1. Key mappings will be introduced with the user interface phase.

## License

The inherited project license is CC0-1.0. See `LICENSE`.
