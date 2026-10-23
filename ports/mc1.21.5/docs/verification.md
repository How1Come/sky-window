# Minecraft Java 1.21.5 verification

The exact-version production JAR builds and passes all 23 mod server GameTests (14 lighting and 9 settings), plus Fabric's baseline test. Builds used Java 25, Gradle 9.7, and Fabric Loom 1.18.2. The JAR's classes target Java 21 (major version 65), and its Minecraft dependency is exactly 1.21.5. Test mods are excluded; JSON resources parse successfully. The final build serializes window index mutations and copies positions before world queries or GPU work.

Artifact: `build/libs/sky-window-0.3.0-alpha.1+mc1.21.5.jar`, 67,804 bytes.

```text
SHA-256 643f139c08aac71b84d2aa55045cdd2a7eb460a96025465af794ce25ef5b4a1b
```

[Build log](verification-artifacts/build.log), [server test log](verification-artifacts/server-tests.log).

All 22 vanilla capture scenarios completed on Java 25. Representative day, settings, snow, independent skies, foreground, and stacked ceiling PNGs were inspected. These static captures predate the index guard and are not a Java 21 runtime check. Guarded runtime checks are recorded separately.

Iris/Sodium dependencies and a replacement-sun pack are prepared for exact-version checks. Prepared dependencies do not establish shader or resource compatibility; completed pack checks are listed separately in the compatibility matrix. Live mode retains the native sky path. Manual mode reads pack textures but does not reproduce arbitrary shader-specific skies or volumetric clouds.

Run the client on Java 21 while Gradle uses Java 25:

```sh
./gradlew runClientSmoke -PsmokeJavaHome=/absolute/path/to/java21 --console plain --no-daemon
```

The runtime-only property changes the game's Java executable. Fabric Loom's build process still requires Java 25.

Durable visual examples: [day](images/vanilla-day.png), [settings](images/settings.png), [snow](images/custom-snow.png), [stacked live](images/stacked-live.png), [stacked manual](images/stacked-custom.png).
