# Copilot instructions

## Build and validation

- Use Java 21 and the Gradle wrapper.
- `./gradlew build` builds the Fabric mod; CI runs `./gradlew clean build`.
- `./gradlew runClient` and `./gradlew runServer` launch Loom development instances.
- No test classes or dedicated lint task are currently configured. For a test class added later, run `./gradlew test --tests 'dev.orbit.SomeTest'`.

## Architecture

- The project targets Minecraft 1.21.11 and uses Loom's split source sets: shared/server code belongs in `src/main`, client-only UI and networking in `src/client`.
- `fabric.mod.json` declares the `dev.orbit.OrbitMod` common entrypoint and `dev.orbit.OrbitClient` client entrypoint; Gradle expands the mod version from `gradle.properties`.
- `OrbitMod` initializes server party commands and registers the S2C party-state payload. `PartySync` owns in-memory server party membership and authoritative playback state; it broadcasts only to party members.
- `OrbitClient` registers the ORBIT key mapping and client-side party receiver. `PartySyncClient` retains the latest synchronized state and estimated position.
- Party track IDs are shared identifiers, not local file paths. Every listener must have the corresponding track locally; the current `AudioEngine` is only a state placeholder and does not decode or output audio.
- `OrbitScreen` and `OrbitTheme` are client-side UI code. Keep references to Minecraft client classes out of `src/main`.

## Repository-specific conventions

- Keep common Fabric commands, payload definitions, and party state under `dev.orbit` in `src/main/java`; put key bindings, screens, and client packet handlers under `src/client/java`.
- A custom payload type must be registered before handlers use it, and its codec should bound incoming strings.
- Party state is server-authoritative; clients receive snapshots and should not independently mutate shared party membership or playback state.
- Keep the Minecraft, Loader, Fabric API, Loom, and Java versions aligned with `gradle.properties`, `fabric.mod.json`, and the GitHub Actions workflow.
