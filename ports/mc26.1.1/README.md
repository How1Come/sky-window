# Sky Window for Minecraft Java 26.1.1

A solid ceiling block that displays the live sky from underneath, supplies real SKY light underground, and supports independent time and weather for connected ceilings.

This experimental JAR targets **Minecraft Java 26.1.1 only**. Install Java **25**, Fabric Loader **0.19.5 or newer**, Fabric API **0.145.4+26.1.1**, and `sky-window-0.3.0-alpha.1+mc26.1.1.jar` built from this directory. Install the mod and Fabric API on the server and every client for multiplayer.

## Use

Find Sky Window in the Functional Blocks creative tab, or run `/give @s sky_window:sky_window 64`. Craft eight windows using six quartz, two glass, and one ender pearl in this pattern:

```text
Quartz  Quartz       Quartz
Glass   Ender pearl  Glass
Quartz  Quartz       Quartz
```

Hold **Left Shift** and right-click any window within reach to open its settings. Rebind **Settings modifier** under Options → Controls → Key Binds → Sky Window; the click follows Minecraft's Use Item/Place Block binding.

Turn Follow world off, drag the time slider, choose Sunny, Rainy, Thunderstorm, or Snowy, and click Apply. Every window connected through shared faces receives the settings. Edge and corner contact does not connect blocks. Disconnected ceilings remain independent.

Custom settings change the ceiling view only. Actual world time, weather, and natural lighting stay unchanged. Follow world restores the real sky. Cancel or Escape discards the draft. Settings persist and synchronize to other players. Newly placed windows start in Follow world mode.

The component and neighboring chunks must be loaded, every member must permit editing, and the component must contain at most 16,384 blocks. The server validates reach and applies the change atomically.

## Rendering

Live mode retains the native sky and clouds. The top and sides remain solid quartz decoration. The underside forms a continuous sky view across adjacent windows and preserves nearer opaque geometry. The nearest ceiling determines visibility where windows overlap.

The SKY light channel supplies natural light with ordinary sideways falloff and obstruction. Brightness follows real day/night and weather. The Nether and End use the decorative fallback.

Manual presets read active resource-pack sun, moon, cloud, rain, and snow textures, with a fixed clock and animated weather. Snow is visual and creates no snow layers. Thunder creates visual flashes without real lightning or sounds. The moon phase follows the real world.

With Iris, live mode preserves shader sky effects. Manual presets use the mod's own atmosphere and do not reproduce arbitrary shader-pack skies or volumetric clouds. Shader fog, exposure, shadows, and room lighting still use the real location and roof geometry. Transparent foreground geometry and particles do not have the same occlusion guarantee as opaque blocks.

See the [compatibility and verification matrix](../../docs/compatibility.md) for the exact tested versions and optional shader combinations. OpenGL is the tested backend; Vulkan has not been verified.

## Build and verify

Use Java 25. The wrapper pins Gradle 9.7.0 with Fabric Loom 1.18.2.

```sh
./gradlew build --console plain --no-daemon
./gradlew runGameTest --console plain --no-daemon
./gradlew runClientSmoke --console plain --no-daemon
```

The production JAR excludes all test mods and optional Iris/Sodium dependencies. The client harness creates an isolated world, operates the settings screen and Apply networking path, captures 22 scenarios, and exits. Screenshots and measurements are saved in `build/render-test`.

Prepared workspace shader dependencies are enabled with `-PirisSmoke`; the matching Iris/Sodium and Complementary Reimagined r5.9.3 inputs are optional and not bundled. Shader captures use `build/render-test-iris`.

Generate the replacement-sun resource test with `python3 scripts/create-sky-test-pack.py`, then run the client harness with `-PresourceSmoke`. Resource captures use `build/render-test-resources`.

Licensed under the [MIT License](LICENSE).
