# Minecraft compatibility

Install the JAR for your exact Minecraft Java version on the client and server. Install Fabric Loader and the matching Fabric API separately. Each target has its own build; one JAR does not cover every release.

This inventory covers the 23 stable releases from 1.20.1 through 26.3. Snapshots, prereleases, Forge, and NeoForge are outside this matrix.

This repository publishes the current source snapshot. A finished compatibility ZIP is not included. The audit below records existing local build artifacts and evidence; artifact metadata and hashes describe those builds, while source metadata names How1Come as author. Rebuild the target project to obtain a JAR from the published source.

## Builds and verification

A valid JAR means its metadata, Java bytecode version, and contents passed the package audit. Server tests exercise lighting and settings. Completed captures are recorded separately from visual review and from their association with a release hash. Development clients load compiled production output; the proof records the corresponding release artifact rather than claiming the client loaded that JAR. Walking checks exercise view bobbing and a chunk rebuild; stationary captures do not establish that behavior.

| Minecraft | Java | Fabric Loader | Fabric API | JAR audit | Server tests | Vanilla static | Vanilla walking | Walking with Iris, shaders off |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 26.3 | 25 | 0.19.5 | 0.161.0+26.3 | valid | 24 passed | 22 captures; review pending; current JAR | 4 captures; reviewed; JAR binding pending | 4 captures; review pending; current JAR |
| 26.2 | 25 | 0.19.5 | 0.161.0+26.2 | valid | 24 passed | 22 captures; reviewed; JAR binding pending | Pending | 4 captures; reviewed; JAR binding pending |
| 26.1.2 | 25 | 0.19.5 | 0.155.3+26.1.2 | valid | 24 passed | 22 captures; reviewed; JAR binding pending | Pending | 4 captures; reviewed; JAR binding pending |
| 26.1.1 | 25 | 0.19.5 | 0.145.4+26.1.1 | valid | 24 passed | 22 captures; reviewed; JAR binding pending | Pending | Pending |
| 26.1 | 25 | 0.19.5 | 0.145.1+26.1 | valid | 24 passed | 22 captures; reviewed; JAR binding pending | Pending | Pending |
| 1.21.11 | 21 | 0.19.5 | 0.141.6+1.21.11 | valid | 24 passed | 22 captures; reviewed; current JAR | Pending | Pending |
| 1.21.10 | 21 | 0.19.5 | 0.138.4+1.21.10 | valid | 24 passed | 22 captures; reviewed; current JAR | Pending | Pending |
| 1.21.9 | 21 | 0.19.5 | 0.134.1+1.21.9 | valid | 24 passed | 22 captures; reviewed; current JAR | Pending | Pending |
| 1.21.8 | 21 | 0.19.5 | 0.136.1+1.21.8 | valid | 24 passed | 22 captures; reviewed; current JAR | Pending | Pending |
| 1.21.7 | 21 | 0.19.5 | 0.129.0+1.21.7 | valid | 24 passed | 22 captures; reviewed; current JAR | Pending | Pending |
| 1.21.6 | 21 | 0.19.5 | 0.128.2+1.21.6 | valid | 24 passed | 22 captures; reviewed; current JAR | Pending | Pending |
| 1.21.5 | 21 | 0.19.5 | 0.128.2+1.21.5 | valid | 24 passed | 22 captures; reviewed; JAR binding pending | Pending | Pending |
| 1.21.4 | 21 | 0.16.14 | 0.119.4+1.21.4 | valid | 23 passed | 22 captures; reviewed; JAR binding pending | Pending | Pending |
| 1.21.3 | 21 | 0.16.14 | 0.114.1+1.21.3 | valid | 23 passed | 22 captures; reviewed; JAR binding pending | Pending | Pending |
| 1.21.2 | 21 | 0.16.14 | 0.106.1+1.21.2 | valid | 23 passed | 22 captures; reviewed; current JAR | Pending | Pending |
| 1.21.1 | 21 | 0.16.14 | 0.116.17+1.21.1 | valid | 23 passed | 22 captures; reviewed; JAR binding pending | Pending | Pending |
| 1.21 | 21 | 0.16.14 | 0.102.0+1.21 | valid | 23 passed | 22 captures; reviewed; current JAR | Pending | Pending |
| 1.20.6 | 21 | 0.16.14 | 0.100.8+1.20.6 | valid | 23 passed | Pending | Pending | Pending |
| 1.20.5 | 21 | 0.16.14 | 0.97.8+1.20.5 | valid | 23 passed | 22 captures; reviewed; JAR binding pending | Pending | Pending |
| 1.20.4 | 17 | 0.16.14 | 0.97.3+1.20.4 | valid | 23 passed | 22 captures; reviewed; JAR binding pending | Pending | Pending |
| 1.20.3 | 17 | 0.16.14 | 0.91.1+1.20.3 | valid | 23 passed | 22 captures; reviewed; JAR binding pending | Pending | Pending |
| 1.20.2 | 17 | 0.16.14 | 0.91.6+1.20.2 | valid | 23 passed | 22 captures; reviewed; JAR binding pending | Pending | Pending |
| 1.20.1 | 17 | 0.16.14 | 0.92.12+1.20.1 | valid | 23 passed | 22 captures; reviewed; JAR binding pending | Pending | Pending |

## Resource packs and shader packs

Follow world mode reveals Minecraft's native sky and clouds, including changes made by the installed resource pack and supported Iris shader pipeline. Manual time/weather mode uses the installed sun, moon, cloud, rain, and snow textures with the mod's own atmosphere. Manual mode does not run a shader pack's custom sky or volumetric-cloud programs.

The dependency pairs below are prepared test configurations. Availability or compilation does not establish visual compatibility. Only the explicit reviewed capture status establishes a tested configuration. Pack behavior can differ by GPU, pack settings, and pack version.

| Minecraft | Iris | Sodium | Shader static | Shader walking | Resource static | Resource walking |
| --- | --- | --- | --- | --- | --- | --- |
| 26.3 | [1.11.6+mc26.3](https://modrinth.com/mod/iris/versions?g=26.3) | [0.9.2+mc26.3](https://modrinth.com/mod/sodium/versions?g=26.3) | 22 captures; reviewed; current JAR | 4 captures; review pending; current JAR | 22 captures; review pending; JAR binding pending | 4 captures; review pending; current JAR |
| 26.2 | [1.11.4+26.2-fabric](https://api.modrinth.com/v2/version/gxZWWnKH) | [mc26.2-0.9.2-fabric](https://api.modrinth.com/v2/version/xJZxADzI) | Pending | Pending | Pending | Pending |
| 26.1.2 | [1.11.4+26.1-fabric](https://api.modrinth.com/v2/version/sZbVsl2Q) | [mc26.1.2-0.9.2-fabric](https://api.modrinth.com/v2/version/tZQ3jqnf) | Pending | Pending | Pending | Pending |
| 26.1.1 | [1.10.9+mc26.1.1](https://api.modrinth.com/v2/version/MwcLS51S) | [0.8.9+mc26.1.1](https://api.modrinth.com/v2/version/uGvVQBnw) | Pending | Pending | Pending | Pending |
| 26.1 | [1.10.8+mc26.1](https://api.modrinth.com/v2/version/Yi5E3d2l) | [0.8.7+mc26.1](https://api.modrinth.com/v2/version/Amr4VcZo) | Pending | Pending | Pending | Pending |
| 1.21.11 | [1.10.8+1.21.11-fabric](https://api.modrinth.com/v2/version/ka9PDlsN) | [mc1.21.11-0.8.14-fabric](https://api.modrinth.com/v2/version/rkdTcxoT) | Pending | Pending | 22 captures; reviewed; current JAR | Pending |
| 1.21.10 | [1.9.7+1.21.10-fabric](https://api.modrinth.com/v2/version/a98UkgML) | [mc1.21.10-0.7.3-fabric](https://api.modrinth.com/v2/version/sFfidWgd) | Pending | Pending | Pending | Pending |
| 1.21.9 | [1.9.7+1.21.10-fabric](https://api.modrinth.com/v2/version/a98UkgML) | [mc1.21.10-0.7.3-fabric](https://api.modrinth.com/v2/version/sFfidWgd) | Pending | Pending | Pending | Pending |
| 1.21.8 | [1.9.6+1.21.8-fabric](https://api.modrinth.com/v2/version/Rhzf61g1) | [mc1.21.8-0.7.2-fabric](https://api.modrinth.com/v2/version/pJRT8Zxt) | Pending | Pending | Pending | Pending |
| 1.21.7 | [1.9.6+1.21.8-fabric](https://api.modrinth.com/v2/version/Rhzf61g1) | [mc1.21.8-0.7.2-fabric](https://api.modrinth.com/v2/version/pJRT8Zxt) | Pending | Pending | Pending | Pending |
| 1.21.6 | [1.9.6+1.21.8-fabric](https://api.modrinth.com/v2/version/Rhzf61g1) | [mc1.21.8-0.7.2-fabric](https://api.modrinth.com/v2/version/pJRT8Zxt) | Pending | Pending | Pending | Pending |
| 1.21.5 | [1.8.11+1.21.5-fabric](https://api.modrinth.com/v2/version/U6evbjd0) | [mc1.21.5-0.6.12-fabric](https://api.modrinth.com/v2/version/fVbw1C7i) | Pending | Pending | Pending | Pending |
| 1.21.4 | [1.8.8+mc1.21.4](https://api.modrinth.com/v2/version/Ca054sTe) | [0.6.13+mc1.21.4](https://api.modrinth.com/v2/version/c3YkZvne) | Pending | Pending | Pending | Pending |
| 1.21.3 | [1.8.1+mc1.21.3](https://api.modrinth.com/v2/version/xQYJAYTI) | [0.6.1+mc1.21.3](https://api.modrinth.com/v2/version/gXZfuKxx) | Pending | Pending | Pending | Pending |
| 1.21.2 | [1.8.0+mc1.21.3](https://api.modrinth.com/v2/version/qyGMuaY6) | [0.6.0+mc1.21.3](https://api.modrinth.com/v2/version/SzAjFUSx) | Pending | Pending | Pending | Pending |
| 1.21.1 | [1.8.8+mc1.21.1](https://api.modrinth.com/v2/version/zsoi0dso) | [0.6.13+mc1.21.1](https://api.modrinth.com/v2/version/u1OEbNKx) | Pending | Pending | Pending | Pending |
| 1.21 | [1.8.8+mc1.21.1](https://api.modrinth.com/v2/version/zsoi0dso) | [0.6.13+mc1.21.1](https://api.modrinth.com/v2/version/u1OEbNKx) | Pending | Pending | Pending | Pending |
| 1.20.6 | [1.7.2+mc1.20.6](https://api.modrinth.com/v2/version/1bvcmYOc) | [0.5.11+mc1.20.6](https://api.modrinth.com/v2/version/OwLQelEI) | Pending | Pending | Pending | Pending |
| 1.20.5 | [1.7.0+mc1.20.5](https://api.modrinth.com/v2/version/JjUWi3Lj) | [0.5.8+mc1.20.6](https://api.modrinth.com/v2/version/IZskON6d) | Pending | Pending | Pending | Pending |
| 1.20.4 | [1.7.2+1.20.4](https://api.modrinth.com/v2/version/hq98tuSS) | [mc1.20.4-0.5.8](https://api.modrinth.com/v2/version/4GyXKCLd) | Pending | Pending | Pending | Pending |
| 1.20.3 | [1.7.2+1.20.4](https://api.modrinth.com/v2/version/hq98tuSS) | [mc1.20.4-0.5.8](https://api.modrinth.com/v2/version/4GyXKCLd) | Pending | Pending | Pending | Pending |
| 1.20.2 | [1.6.14+1.20.2](https://api.modrinth.com/v2/version/Cjwm9s3i) | [mc1.20.2-0.5.5](https://api.modrinth.com/v2/version/pmgeU5yX) | Pending | Pending | Pending | Pending |
| 1.20.1 | [1.7.6+1.20.1](https://api.modrinth.com/v2/version/s5eFLITc) | [mc1.20.1-0.5.11](https://api.modrinth.com/v2/version/ygf8cVZg) | Pending | Pending | Pending | Pending |

Reviewed enabled-shader captures use `ComplementaryReimagined_r5.9.3.zip` with profile `HIGH`.

Resource captures use the generated `sky-window-test.zip` replacement-sun fixture. They verify visible texture substitution; they do not establish arbitrary custom-sky resource-pack behavior.

## Manual sky while walking

These captures use manual noon/Sunny on the ceiling while the real world remains unchanged. They exercise the manual atmosphere renderer, which is separate from the native sky revealed in follow world mode. A clean client exit does not establish correct depth alignment; image review is required.

| Minecraft | Vanilla | Iris, shaders off | Iris, shaders on | Resource pack |
| --- | --- | --- | --- | --- |
| 26.3 | Pending | 4 captures; review pending; current JAR | 4 captures; reviewed; current JAR | Pending |

Iris and Sodium are client dependencies for the optional shader path. They are not bundled in the mod or the compatibility ZIP. Use the version pair for your target; some author metadata lists a patch release in the filename while permitting adjacent patch releases.

Executed GPU checks use macOS with OpenGL and an integrated server. Vulkan, remote multiplayer, and arbitrary shader or resource packs remain unverified. Manual mode does not reproduce shader-pack-specific atmosphere.

## Release audit

[compatibility.json](../compatibility.json) records exact filenames, SHA256 values, Java class versions, dependency metadata, and evidence paths. The audit rejects Minecraft dependency ranges, test mods, nested dependency JARs, and bundled Iris or Sodium classes, and parses every JSON and mcmeta file in each release JAR.

Evidence and project paths refer to the source checkout; they are not included in the install ZIP. Release packaging requires a valid production JAR, passing server tests, and 22 visually reviewed vanilla captures for every target, plus clean, visually reviewed 26.3 walking runs for follow world mode with Iris installed and shaders disabled and for manual mode with Iris shaders enabled. Both walking proofs must match the current release hash.

Static and walking captures cover different scenarios. The vanilla blue-sky pixel classifier is used only with vanilla rendering or disabled shaders; pack-specific colors require image inspection. Failed visual inspections are bound to image digests and reject release proofs even when the client process exits cleanly.

Regenerate the inventory with `python3 scripts/package-compatibility.py`. Bind a completed, current run with `--record-proof VERSION KIND ports/mcVERSION/build/OUTPUT`; add `--reviewed --notes TEXT` only after inspecting the images. Record a rejected inspection with `--record-visual-review VERSION failed ports/mcVERSION/build/OUTPUT --notes TEXT`. Create the final ZIP with `--package` after the release gates pass.

Metadata sources: [Mojang version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json), [Fabric Loader metadata](https://meta.fabricmc.net/v2/versions/loader), [Fabric API Maven metadata](https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml), and the authors' linked Modrinth version metadata.
