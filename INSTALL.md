# Install Sky Window

This repository contains the current source snapshot. Build the project for your exact Minecraft version; a finished compatibility ZIP is not included.

1. Select your exact Minecraft Java version and required Java runtime from the table below.
2. Open `ports/mcVERSION/` and run `./gradlew build` on macOS/Linux or `gradlew.bat build` on Windows. The root project is a separate 1.21.1 development baseline.
3. Install Fabric Loader and Fabric API for that exact Minecraft version. Use the dependency versions listed in `docs/compatibility.md`.
4. Copy exactly one matching `sky-window-*.jar` from the target project's `build/libs/` into your Minecraft instance's `mods` folder. Copy the matching Fabric API JAR there too. Keep other Sky Window version JARs out of that instance.
5. Install the same matching Sky Window JAR and Fabric API on the server when playing multiplayer, then launch the Fabric profile.

If a compatibility ZIP is packaged separately, select the JAR from its `mods/VERSION/` directory instead of building from source.

For source builds, launch Gradle with JDK 17 for 1.20.1–1.20.4, JDK 21 for 1.20.5–1.21.4, and JDK 25 for 1.21.5–26.3. The 1.21.5–1.21.11 builds still produce Java 21 bytecode for the game runtime.

| Minecraft | Java runtime |
| --- | --- |
| 1.20.1 through 1.20.4 | 17 |
| 1.20.5 through 1.21.11 | 21 |
| 26.1 through 26.3 | 25 |

Find Sky Window in the Functional Blocks creative tab, or use `/give @s sky_window:sky_window 64`. Place the blocks in a ceiling and look up from below. Hold Shift and right-click a block to open its settings. Change the modifier in Minecraft's Controls settings. Apply updates all connected Sky Window blocks while leaving the real world's time and weather unchanged. Room skylight follows the real time and weather even when the ceiling displays a manual preset.

Connected ceilings join through shared block faces. All connected blocks and their neighboring chunks must be loaded. Apply rejects the whole edit if a block is protected or the ceiling exceeds 16,384 blocks.

Iris and Sodium are optional client mods and are not included. For shaders, install the matching pair listed in `docs/compatibility.md`, put your shader pack in the instance's `shaderpacks` folder, and enable it in Iris. Install resource packs through Minecraft's Resource Packs screen.

`docs/compatibility.md` distinguishes completed captures, image review, walking checks, and association with the current release JAR. Follow world mode uses the native sky and clouds. Manual time/weather mode uses resource pack textures with the mod's atmosphere; it does not run shader pack sky or volumetric-cloud programs.

`compatibility.json` records the previously audited local artifacts and their SHA256 values. A fresh source build has its own hash. Evidence paths refer to the development checkout; ignored build outputs and runtime data are not part of the source snapshot. A separately packaged compatibility ZIP includes a SHA256 sidecar.
