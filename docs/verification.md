# Verification

Target: Minecraft Java 1.21.1, Fabric Loader 0.16.14, Fabric API 0.116.17+1.21.1, Java 21.

On 2026-09-28, the mod compiled, all **14 required Minecraft GameTests passed**, and the vanilla client completed all **11 visual captures**. Inspected captures show a continuous sun/cloud view, a moon/star view, storm darkening, foreground occlusion, brighter daylight on the floor, darker nighttime lighting, and ordinary top/side faces. The camera was inside a sealed room with twelve solid layers above the window ceiling.

On 2026-09-29, the settings update passed **23 Minecraft GameTests** and completed **22 vanilla client captures**. The client exercised the real Apply button and networking path, checked the shared Shift/Sneak binding and rebinding to R, and verified all 225 windows in the main ceiling. Separate ceilings retained independent settings. Both the client measurements and integrated server assertions retained real time 6000 with clear weather while the ceiling showed midnight thunder or daytime snow. Inspected captures confirm the settings panel, local snow, simultaneous live/custom skies, and nearest-window selection for stacked ceilings. These captures preceded the additional vertex-position invariance and texture-failure cleanup changes; those changes require the next client run.

The client reported sky light 15 and block light 0 inside the room at all tested times. Rain and thunder gradients reached 1.0 in their respective scenarios. This is the normal distinction between stored skylight and the visible brightness after time/weather shading.

Examples: [day](images/01-day.png), [night](images/04-night.png), [foreground obstruction](images/07-foreground.png). Full captures and measurements are generated under `build/render-test`.

| Client configuration | Captures | Result |
| --- | --- | --- |
| Vanilla renderer | 22 | Live sky checks plus settings controls, custom weather, independent ceilings, stacked ceilings, and reset to live completed |
| Iris 1.8.8 + Sodium 0.6.13 + Complementary Reimagined r5.5.1, HIGH profile | 11 baseline | Live pack-rendered sky/clouds, day/night views, storm sky, foreground obstruction, and room daylight inspected; custom settings rerun pending |
| Vanilla renderer + generated replacement sun resource pack | 11 baseline | Distinctive magenta/cyan replacement sun appears through the live ceiling; custom settings rerun pending |

GPU checks used macOS 15.5 with a Radeon Pro 560X and OpenGL 4.1. Iris logged a nonfatal `BIOME_PALE_GARDEN` uniform warning from the shader pack on Minecraft 1.21.1; the pack loaded and the capture run completed. No universal compatibility claim follows from this one tested pack.

Iris captures are in `build/render-test-iris`; resource-pack captures are in `build/render-test-resources`. Each directory contains `measurements.txt`, `client.log`, and `COMPLETE.txt`. Durable examples: [Iris sky](images/iris-day.png), [replacement sun](images/resource-pack-sun.png).

## Automated server checks

Run `./gradlew runGameTest`. The test mod creates sealed rooms with stone above the window and checks the sky-light and block-light channels separately.

| Check | Expected result |
| --- | --- |
| Placement in a sealed room | Direct sky light 15, block light 0 |
| Sideways propagation | Sky light 14 and 13 one and two blocks from the direct column |
| Window removal and replacement | Darkness returns, then skylight returns |
| Solid floor below a window | Lower chamber darkens until the floor is opened |
| Overlapping window contributions | Removing one window preserves the other source |
| Chunk boundary propagation | Light crosses the boundary and is removed correctly |
| Tall hall with empty sections | Direct skylight reaches the floor without distance attenuation |
| Transition to a physical roof opening | Natural skylight survives window removal and stops when the roof closes |
| Initially blocked underside | Skylight begins after the blocking block is removed |
| Stacked windows | Each separated room retains its own light source |
| Chunk lighting re-enable | Sources are rediscovered and subsequently removed correctly |
| Leaves immediately below a window | Skylight attenuates, follows replacement, and disappears after removal |
| Water immediately below a window | Skylight enters the water with vanilla attenuation |
| Overlapping attenuated sources | A dimmer source survives the removal of an adjacent brighter source |
| Reloading an attenuated source | Chunk lighting re-enable restores the partial source |

Chunk re-enable tests exercise the lighting lifecycle in memory; they do not establish full disk save/reload or multiplayer compatibility.

The nine added settings tests cover shared-face connectivity, separation of edge/corner contacts, atomic rejection of a protected component, cross-chunk application and reset to live, block-entity NBT and update packets, malformed/legacy NBT defaults, payload codec round trips, player reach/build permission checks, and rejection of a removed target. NBT round trips establish serialization behavior, not a complete quit/reopen test. The 16,384-block limit and unloaded-neighbor rejection are implemented but do not yet have dedicated integration tests.

## Custom rendering limits

Custom skies use the installed sun, moon, cloud, rain, and snow textures with the mod's own atmosphere and animation. They do not execute a shader pack's custom sky or volumetric-cloud programs. Follow world mode continues to use the native shader-rendered sky.

Opaque foreground geometry and the nearest window depth protect custom sky composition. Foreground effects that do not write depth, including some translucent materials and particles, can be overwritten because custom sky composition occurs after world rendering. Glass, water, underwater fog, and pack-specific translucency need dedicated visual checks. Treat these paths as unsupported for custom mode until verified.

## Visual acceptance checks

Use a creative Overworld test world with a sealed room and a broad Sky Window ceiling. Repeat with vanilla rendering, the intended resource pack, and each intended Iris shader pack.

| Check | Action |
| --- | --- |
| Day and night | Compare `/time set noon` and `/time set midnight` |
| Rain and thunder | Compare `/weather clear`, `/weather rain`, and `/weather thunder` |
| Cloud movement | Enable clouds and observe the opening over time |
| Continuous ceiling | Pan across adjoining window blocks |
| Foreground occlusion | Place a solid column between the camera and part of the ceiling |
| Restricted face | Observe the same blocks from above and from the side |
| Light response | Compare the floor under the ceiling at noon and midnight |
| Reload handling | Leave and reload the world, then unload and revisit the chunks |
| Resource replacement | Reload resources after replacing sun, moon, cloud, or block textures |
| Iris lifecycle | Toggle shaders, resize the window, and switch packs |
| Transparency | View through glass and water in front of the opening |
| Other dimensions | Visit the Nether and End and confirm the decorative fallback |

Hot resource reload, shader toggling, window resizing, dimension transitions, glass/water in front of the aperture, full disk reload, and remote multiplayer remain unverified. The block and lighting are implemented for both client and server; the executed client runs used an integrated server.

## Reproduce pack checks

To test resource selection in the isolated smoke instance:

```sh
python3 scripts/create-sky-test-pack.py
./gradlew -PresourceSmoke runClientSmoke --no-daemon
```

The script enables its generated pack only in `run/client-smoke/options.txt`. Deselect it there before repeating the baseline check.

For the Iris check, place [Complementary Reimagined r5.5.1](https://modrinth.com/shader/complementary-reimagined/version/sAAjYvFB) in `run/client-smoke/shaderpacks`, and set `run/client-smoke/config/iris.properties` to:

```properties
enableShaders=true
shaderPack=ComplementaryReimagined_r5.5.1.zip
```

Then run:

```sh
./gradlew -PirisSmoke runClientSmoke --no-daemon
```

This development-only flag supplies the pinned Iris/Sodium versions and Iris's runtime libraries. None is bundled in the production mod.
