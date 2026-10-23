# Sky Window for Minecraft Java 1.21.8

Ceiling blocks reveal the live sky from underneath and supply real SKY light underground. This experimental build requires Minecraft Java **1.21.8**, Java **21 or newer**, Fabric Loader **0.19.5 or newer**, and Fabric API **0.136.1+1.21.8**.

## Install

1. Install Fabric for Minecraft 1.21.8.
2. Put `sky-window-0.3.0-alpha.1+mc1.21.8.jar` built from this directory and Fabric API 0.136.1+1.21.8 in the client and server `mods` folders.
3. Start the game and place Sky Window blocks in the ceiling. The block is in the Functional Blocks creative tab. Six quartz, two glass blocks, and one ender pearl craft eight Sky Windows.

## Configure a ceiling

Hold **Left Shift** and right-click a Sky Window. Rebind **Sky Window settings modifier** in Minecraft's Controls menu to change the modifier; Minecraft's Use Item/Place Block binding controls the click.

Use **Follow world** to display the real time and weather. Turn it off to select a fixed time with the slider and choose Sunny, Rainy, Thunderstorm, or Snowy. **Apply** updates every block connected by shared faces, including across loaded chunk boundaries. Blocks touching only at an edge or corner remain separate.

Settings are saved with each block entity and synchronized to other players. Applying settings leaves real world time, weather, and skylight unchanged. Natural light continues to follow the real world. The entire ceiling and neighboring chunks must be loaded, every block must permit editing, and the component must contain at most 16,384 blocks.

## Rendering compatibility

Live mode preserves the native sky, sun, moon, stars, and cloud rendering. Manual presets use the active resource pack's celestial, cloud, rain, and snow textures. Manual presets do not reproduce arbitrary shader-specific atmosphere or volumetric clouds.

The top and sides remain ordinary solid block faces. Opaque foreground geometry and the nearest Sky Window determine visibility. Transparent foreground geometry, particles, alternative dimension skies, other sky-rendering mods, and every possible shader pack are not universally verified.

## Verification

The production JAR builds and all 23 mod server GameTests pass (24 total including Fabric's test). The 22-stage vanilla visual harness completed and was inspected; current-artifact association and separate pack/movement checks are recorded in the [compatibility matrix](../../docs/compatibility.md). See [verification evidence](docs/verification.md) for completed runtime and visual checks.

## Build from source

Build with JDK 25 and Gradle 9.7: Loom 1.18.2 requires Java 25. The mod itself targets Java 21; its client runtime is verified separately.

```sh
./gradlew build --console plain --no-daemon
```

Run the server tests:

```sh
./gradlew runGameTest --console plain --no-daemon
```

Run the 22-stage visual harness:

```sh
./gradlew runClientSmoke --console plain --no-daemon
```

Add `-PsmokeJavaHome=/absolute/path/to/java21` to run the client with Java 21 while Gradle uses Java 25.

The prepared workspace supports `-PirisSmoke` with matching Iris/Sodium and Complementary Reimagined r5.9.3, and `-PresourceSmoke` after running `python3 scripts/create-sky-test-pack.py`. Captures are stored in `build/render-test`, `build/render-test-iris`, and `build/render-test-resources`.

Run the separate four-scenario walking check:

```sh
./gradlew runClientSmoke -PmovementSmoke -PsmokeJavaHome=/absolute/path/to/java21 --console plain --no-daemon
```

It uses an isolated movement instance, walks across a ceiling chunk boundary, switches view bobbing off and on, and rebuilds nearby terrain while walking. Add `-PirisDisabledSmoke` to load Iris/Sodium with its shader pack disabled, or `-PirisSmoke` for the prepared shader pack. Use `-PsmokeOutput=build/my-run` to retain a separate result. Vanilla and shader-disabled profiles require at least 99.5% clear sky pixels in the central sample; shader and resource profiles require visual review.
