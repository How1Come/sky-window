# Sky Window

A solid Minecraft ceiling block that reveals the sky from underneath, supplies natural skylight underground, and lets connected ceilings display their own time and weather.

Created by **How1Come**. This repository is an **experimental source snapshot**. Separate Fabric projects target the 23 stable Minecraft Java releases from **1.20.1 through 26.3**. Compatibility verification is still in progress; no finished compatibility bundle is published in this snapshot.

![Live sky through a solid underground ceiling](ports/mc26.3/docs/images/iris-day.png)

The image shows the tested Complementary shader configuration. See the [26.3 guide](ports/mc26.3/README.md), [verification report](ports/mc26.3/docs/verification.md), and [compatibility matrix](docs/compatibility.md) for versioned evidence and limitations.

## Current stage

- **Builds:** All 23 exact-version production JARs passed artifact audits and server GameTests: 23 mod tests per target, plus one Fabric baseline test on newer targets.
- **Client checks:** Stationary GUI runs completed on 22 targets; 1.20.6 remains pending. Some inspected runs predate the current artifact; the matrix separates capture completion, visual review, and artifact association.
- **26.3:** The current candidate is `0.3.0-alpha.5+mc26.3`. Its custom-sky walking check with enabled Iris shaders passed visual review; current client logs also record clean exits for the stationary and movement profiles. Older alpha results remain historical evidence.
- **Distribution:** Build the matching source project below. Generated JARs, test instances, and local verification outputs are excluded from Git.

## Versions and requirements

| Minecraft targets | Game Java | Build JDK | Source project |
| --- | --- | --- | --- |
| 1.20.1, 1.20.2, 1.20.3, 1.20.4 | 17 | 17 | `ports/mcVERSION` |
| 1.20.5, 1.20.6, 1.21, 1.21.1–1.21.4 | 21 | 21 | `ports/mcVERSION` |
| 1.21.5–1.21.11 | 21 | 25 | `ports/mcVERSION` |
| 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | 25 | 25 | `ports/mcVERSION` |

Install Fabric Loader and the matching Fabric API listed in the [matrix](docs/compatibility.md). Use one Sky Window JAR for your exact Minecraft version. Snapshots, prereleases, Forge, and NeoForge are outside this target set.

## Install and use

1. Build the matching project, or use a release artifact when one is published.
2. Put its production JAR and the matching Fabric API in the instance's `mods` folder.
3. Install Sky Window and Fabric API on the server and every joining client for multiplayer.
4. Replace the ceiling with Sky Window blocks from the Functional Blocks creative tab.

```mcfunction
/give @s sky_window:sky_window 64
```

Craft eight windows with six quartz, two glass, and one ender pearl:

```text
Quartz  Quartz       Quartz
Glass   Ender pearl  Glass
Quartz  Quartz       Quartz
```

The underside shows one continuous sky across adjacent windows. The top and sides remain solid decoration. Mine the block with a pickaxe.

## Ceiling settings

Hold **Left Shift** and **right-click** a window. Turn **Follow world** off, drag the time slider, choose **Sunny**, **Rainy**, **Thunderstorm**, or **Snowy**, and click **Apply**. All windows connected through shared faces receive the setting; edge and corner contact do not connect ceilings. Disconnected ceilings remain independent.

Rebind **Settings modifier** in Minecraft's Controls menu. The click follows the normal **Use Item/Place Block** binding. Sharing Shift with Sneak works. Cancel or Escape discards the draft; Follow world and Apply restore the real sky.

Settings persist with the blocks and synchronize to clients. The server validates reach and edit permission. The complete component and neighboring chunks must be loaded, and the component must contain at most **16,384 blocks**; a rejected Apply leaves the component unchanged.

Custom settings change the ceiling view only. The real clock, weather, and room lighting stay unchanged. Custom time stays fixed while clouds and visual precipitation animate. Snow creates no snow layers; thunder creates no world lightning or sounds.

## Lighting and packs

The block supplies actual **SKY light**, with normal sideways falloff and obstruction. It emits no block light. Brightness follows real time and weather, even when the ceiling displays a custom sky. The roof remains solid.

**Follow world** reveals the native sky and clouds, including resource-pack textures and the supported Iris rendering path. **Custom mode** uses resource-pack sun, moon, cloud, rain, and snow textures with the mod's own atmosphere. Custom ceilings do not reproduce shader-pack-specific atmosphere, volumetric clouds, or final postprocessing.

The tested 26.3 shader configuration uses **Iris 1.11.6+mc26.3**, **Sodium 0.9.2+mc26.3**, and **Complementary Reimagined r5.9.3, HIGH**. The replacement-sun fixture checks resource texture substitution. These checks do not establish compatibility with arbitrary packs. Iris and Sodium are optional and are not bundled.

Shader fog, exposure, shadows, and path tracing still use the actual underground camera and roof. Transparent foreground geometry, aperture-edge temporal effects, resource reloads, remote multiplayer, and Vulkan need dedicated checks. The Nether and End use the decorative fallback.

## Build and verify

For example, build 26.3 with a Java 25 JDK:

```sh
cd ports/mc26.3
./gradlew build --console plain --no-daemon
```

On Windows, use `gradlew.bat`. The production JAR appears in that project's `build/libs`; the `-sources.jar` is for development. Each port README gives its pinned tools and optional test configuration. Minecraft 1.21.5–1.21.11 uses JDK 25 for Gradle/Loom while its mod bytecode targets Java 21.

Run the server tests or isolated client scenarios from the selected port:

```sh
./gradlew runGameTest --console plain --no-daemon
./gradlew runClientSmoke --console plain --no-daemon
```

Client captures go to `build/render-test`. Capture completion and successful process exit are separate from visual inspection. Test mods and optional shader dependencies are excluded from production JARs.

The root Gradle project is the earlier 1.21.1 development baseline, documented in [README-1.21.1.md](README-1.21.1.md). Current compatibility work lives in the version-specific ports. The [machine-readable inventory](compatibility.json) records artifact hashes, dependencies, and verification status; its local evidence paths refer to the development checkout.

Licensed under the [MIT License](LICENSE).
