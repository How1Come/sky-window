# Minecraft Java 1.21.8 verification

The exact-version production JAR builds and passes all 23 mod server GameTests (14 lighting and 9 settings), plus Fabric's baseline test. Builds used Java 25, Gradle 9.7, and Fabric Loom 1.18.2. The JAR's classes target Java 21 (major version 65), and its Minecraft dependency is exactly 1.21.8. Test mods are excluded; JSON resources parse successfully. The final build serializes window index mutations and copies positions before world queries or GPU work.

Artifact: `build/libs/sky-window-0.3.0-alpha.1+mc1.21.8.jar`, 68,288 bytes.

```text
SHA-256 571c265ef7fbe6469af3ab0ef3a9bc27d6cac8cf1d4e9191cb7175101f43eef6
```

[Build log](verification-artifacts/build.log), [server test log](verification-artifacts/server-tests.log).

The 22-stage visual harness completed a vanilla GUI run that was visually reviewed. The [compatibility matrix](../../../docs/compatibility.md) records whether its evidence matches the current artifact, together with the separate walking and pack-check status.

Iris/Sodium dependencies and a replacement-sun pack are prepared for exact-version checks. Prepared dependencies do not establish shader or resource compatibility; completed pack checks are listed separately in the compatibility matrix. Live mode retains the native sky path. Manual mode reads pack textures but does not reproduce arbitrary shader-specific skies or volumetric clouds.

Run the client on Java 21 while Gradle uses Java 25:

```sh
./gradlew runClientSmoke -PsmokeJavaHome=/absolute/path/to/java21 --console plain --no-daemon
```

The runtime-only property changes the game's Java executable. Fabric Loom's build process still requires Java 25.
