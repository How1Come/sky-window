# Minecraft 26.3 verification

**Alpha.5 production build and all 24 required server GameTests passed.** It caches visible manual sky colors before Iris final composition, then copies those pixels afterward without a second depth comparison. Alpha.4's invariant-position candidate failed visual review: enabled-shader walking captures still showed rows and a diagonal wedge of native shader sky inside the custom ceiling. Fresh alpha.5 checks completed with clean exits: 22 vanilla static scenarios, 22 enabled-Iris static scenarios, four live walking scenarios each with Iris shaders disabled, Iris shaders enabled, and a replacement-sun resource pack, and four manual walking scenarios each with Iris shaders disabled and enabled. All four enabled-Iris manual walking images and all 22 enabled-Iris static images passed visual review. The remaining new profiles require recorded image review.

Alpha.3 completed the live walking profiles with vanilla, Iris disabled, Iris enabled, and the replacement-sun resource pack, plus all 22 static Iris-disabled scenarios, with clean exits. Those runs verify alpha.3 and are retained as historical coverage.

Alpha.1's earlier vanilla, resource-pack, and Iris runs each completed 22 static scenarios. Alpha.2 corrected the walking projection regression; its subsequent vanilla run exposed a shutdown crash. Alpha.3 serialized index access and completed the affected lifecycle checks.

| Component | Target |
| --- | --- |
| Minecraft Java | 26.3 |
| Java | 25 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Fabric Loom | 1.18.2 |
| Gradle | 9.7.0 |
| Sky Window | 0.3.0-alpha.5+mc26.3 |

| Check | Status | Evidence location |
| --- | --- | --- |
| Production build | Passed; production JAR inspected | `build/libs` |
| Minecraft server GameTests | Passed: 23 mod tests + 1 Fabric baseline | `build/run/gameTest/logs/latest.log` |
| Alpha.4 manual Iris walking visual checks | Failed: rows and diagonal native-sky patches remain | `build/render-test-manual-movement-iris-on-alpha4` |
| Alpha.5 manual Iris walking visual checks | Passed: all four images inspected; clean exit | `build/render-test-manual-movement-iris-on-alpha5` |
| Alpha.5 Iris static visual checks | Passed: all 22 images inspected; clean exit | `build/render-test-iris-on-alpha5` |
| Alpha.5 vanilla static captures | Completed: 22 captures and clean exit; image review pending | `build/render-test-alpha5` |
| Alpha.5 live walking captures | Completed: four captures per Iris-off, Iris-on, and resource profile; clean exits; image review pending | `build/render-test-movement-*-alpha5` |
| Alpha.5 manual Iris-disabled walking captures | Completed: four captures and clean exit; image review pending | `build/render-test-manual-movement-iris-off-alpha5` |
| Alpha.1 vanilla static visual checks | Completed: 22 captures; representative views inspected | `build/render-test` |
| Alpha.1 Iris static visual checks | Completed: 22 captures; alpha.1 code inspected | `build/render-test-iris` |
| Alpha.1 resource-pack static visual checks | Completed: 22 captures; replacement sun inspected in both modes | `build/render-test-resources` |

Artifact: `build/libs/sky-window-0.3.0-alpha.5+mc26.3.jar`, **77,341 bytes**, Java class major version **69** (Java 25). Test mods, Iris, and Sodium are not bundled. JSON resources passed validation.

```text
SHA-256 1356c2f176b339592909afd93ae61ccc26f7be725bbd20f5a5aeca6fc96a2100
```

## Walking regression

The old renderer projected ceiling faces with the camera's unmodified projection matrix. Minecraft and Sodium render terrain with the projection after view bobbing, hurt tilt, and nausea transforms. This made the ceiling aperture fail its depth test while walking with bobbing. Alpha.2 captures the actual terrain projection before it enters the GPU projection buffer and uses it for both live/manual draws and Iris aperture masks.

A separate corridor contains 585 connected ceiling blocks across chunk boundaries. The harness holds Minecraft's forward key and walks from x=84.5 to x=118.78 or x=123.10. It captures still/no-bob, walking/no-bob, walking/bob, and walking/bob while nearby terrain is rebuilt. Each run records actual player positions. The test uses clear noon weather with clouds off; a central ceiling patch must contain at least 99.5% bright blue sky or white sun pixels.

| Scenario | Alpha.1 clear-sky pixels | Alpha.2 clear-sky pixels |
| --- | --- | --- |
| Standing, bobbing off | 100% | 100% |
| Walking across chunks, bobbing off | 100% | 100% |
| Walking across chunks, bobbing on | 0% | 100% |
| Walking during terrain rebuilds, bobbing on | 0% | 100% |

The baseline bobbing captures visibly show tiled glass over most of the ceiling. Both fixed bobbing captures show a continuous sky. [Before](images/walking-before.png) and [after](images/walking-after.png) images are retained with [pixel evidence](verification-artifacts/walking-baseline-pixels.txt), [fixed measurements](verification-artifacts/walking-fixed-measurements.txt), and [fixed client log](verification-artifacts/walking-fixed-client.log). The baseline did not reproduce fallback glass with bobbing disabled.

```sh
./gradlew runClientSmoke -PmovementSmoke -PirisDisabledSmoke --console plain --no-daemon
```

This uses an isolated movement instance and disables its Iris shader pack. Alpha.3 completed the four live walking scenarios separately with vanilla, enabled Iris shaders, and a replacement sun resource pack. All three runs exited cleanly and their captures were inspected. The original 66 static captures establish alpha.1 coverage and are not counted as later reruns.

## Manual sky and Iris depth agreement

Alpha.3's custom Sunny/noon walking fixture exposed a sharp diagonal patch of the native shader sky during bobbing and a horizontal seam during nearby terrain rebuilding. Alpha.4 declared `invariant gl_Position` in both the manual color shader and Iris aperture mask, but fresh captures still failed visual review. The [failed walking capture](images/manual-walking-before.png) and [review result](verification-artifacts/manual-iris-alpha.4-visual-review-result.json) are retained.

Alpha.5 uses one linked OpenGL program for aperture depth and custom color, switching a uniform between mask and color output. Before Iris final composition, it copies scene depth and renders custom sky into a transparent offscreen color target against that copy. The stored depth selects foreground objects and the nearest ceiling setting. After composition, a fullscreen pass copies only the visible custom pixels. The manual color pass no longer compares its geometry against Iris's final depth attachment. Sun, moon, cloud, rain, and snow textures keep their resource-pack samplers. The chosen time and weather affect only custom sky colors.

The inspected alpha.5 manual Iris-on images show continuous custom blue sky and a white sun in all four stages, without the earlier diagonal wedge or horizontal bands. The process exited successfully. A [walking result](images/manual-walking-after.png) and [artifact-bound proof](verification-artifacts/manual-iris-alpha.5-proof.json) are retained.

The manual fixture sets 585 connected corridor windows to Sunny at time 6000, walks across chunk boundaries, and verifies that those settings reached the client while real time and weather remain unchanged. Run `./gradlew runClientSmoke -PmanualMovementSmoke -PirisSmoke --console plain --no-daemon` for enabled shaders, or replace `-PirisSmoke` with `-PirisDisabledSmoke` for the disabled-pack profile. Shader captures require visual review.

## Shutdown lifecycle

The alpha.2 vanilla movement captures passed, but unloading its larger corridor exposed an `ArrayIndexOutOfBoundsException` in `LongOpenHashSet.rehash` during window removal. The index was shared by block-entity load/unload, extraction, and disconnect/reset with no serial guard. Alpha.3 synchronizes those operations on one monitor, including tracked-level updates and immutable render snapshot publication. Rendering reads the immutable snapshot and does not hold the index monitor during GPU commands.

The [alpha.2 crash report](verification-artifacts/alpha.2-shutdown-crash.txt), [alpha.3 build/server log](verification-artifacts/alpha.3-build.log), [alpha.4 build/server log](verification-artifacts/alpha.4-build.log), and [alpha.5 build/server log](verification-artifacts/alpha.5-build.log) are retained. Each new client run must both complete its captures and exit cleanly; `COMPLETE.txt` alone does not establish shutdown correctness.

## Server acceptance checks

Run `./gradlew runGameTest --console plain`. The suite defines **14 lighting tests** and **9 settings tests**.

The 26.3 lighting implementation uses raw block light dampening when tracking direct skylight, so air retains full skylight through tall columns. GameTest lighting barriers wait for asynchronous lighting work before checking the resulting values; this prevents the accelerated test server from outrunning the lighting workers.

| Area | Required behavior |
| --- | --- |
| Direct and lateral lighting | Stores SKY light 15 beneath a clear window, BLOCK light 0, and normal sideways falloff |
| Removal and replacement | Removes and restores window skylight |
| Obstructions | Stops direct light at solid blocks and attenuates through leaves and water |
| Source overlap | Preserves remaining bright or attenuated sources |
| Chunk boundaries | Propagates and removes light across chunk boundaries |
| Sparse sections and loading | Preserves direct skylight through tall empty sections and rediscovers loaded sources |
| Natural openings | Preserves ordinary skylight after removing a window, then darkens when the roof closes |
| Connected settings | Updates all six face connections and crosses chunk boundaries |
| Independent settings | Leaves edge/corner contacts and disconnected windows unchanged |
| Atomic rejection | Rejects a protected member without modifying the component |
| Persistence and packets | Round-trips block-entity settings and request/result payloads |
| Invalid settings | Uses live mode for invalid or legacy saved data |
| Player authorization | Rejects out-of-reach, read-only, or removed-target requests |
| Real-world isolation | Leaves the real clock and weather unchanged after custom Apply |

The source-level block limit and unloaded-chunk checks are not substitutes for executed acceptance evidence. The suite's assertions cover NBT persistence; a complete save/quit/reload client session requires a separate check.

## Client acceptance checks

Run `./gradlew runClientSmoke --console plain --no-daemon`. The harness defines **22 capture scenarios** in an isolated creative Overworld, including a sealed room with twelve solid roof layers above 225 ceiling windows.

| Area | Required behavior |
| --- | --- |
| Live sky | Shows day, sunset, night, rain, thunder, and moving clouds |
| Face restriction | Keeps ordinary top and side faces |
| Foreground occlusion | Keeps an opaque beam in front of the ceiling view |
| Room illumination | Follows real daytime and nighttime brightness |
| Settings panel | Opens with the modifier and Use Item, and supports rebinding |
| Apply path | Uses the screen's Apply button and server networking |
| Custom weather | Shows midnight thunder and daytime snow while reality stays clear at time 6000 |
| Independent ceilings | Shows different settings on disconnected ceilings |
| Stacked ceilings | Displays the nearest visible window's setting |
| Reset | Returns custom ceilings to Follow world |

Successful completion must produce `COMPLETE.txt`, `measurements.txt`, and all capture PNGs. Completion confirms the harness and assertions; visual correctness also requires inspecting the PNGs. A client log should be retained with each completed run.

The historical alpha.1 vanilla run contains all 22 captures, `COMPLETE.txt`, `measurements.txt`, and `client.log`. Inspected captures show live day/night views, foreground beam occlusion, the settings panel, custom midnight thunder, custom snow, and simultaneous independent skies. The control assertions passed for default Left Shift sharing Sneak, rebinding the modifier to R, and preserving the Use Item binding.

The Apply/network assertions verified settings on 225 room windows, 16 custom demo windows, 16 independent demo windows, 144 far stacked windows, and 16 near stacked windows. Both client and server assertions retained real time 6000 and clear weather during custom settings. All recorded room samples were SKY light 15 and BLOCK light 0. The stored SKY value is distinct from visual brightness after real time/weather shading.

## Pack acceptance checks

| Configuration | Checks | Status |
| --- | --- | --- |
| Vanilla renderer | All 22 scenarios | Completed on alpha.1; representative PNGs inspected |
| Iris 1.11.6+mc26.3 + Sodium 0.9.2+mc26.3 + Complementary Reimagined r5.9.3, HIGH profile | Live shader sky, custom overlay, occlusion, independent and stacked ceilings | Passed on alpha.5: all 22 static images inspected; clean exit |
| Vanilla renderer + replacement sun resource pack | Resource-selected sun in live and custom modes | Completed on alpha.1: 22 captures; replacement sun inspected in both modes |

The optional Iris hook releases reverse-Z aperture depth before Iris copies hand/deferred depth, restores it for translucent geometry, and releases it again before final composition. This preserves the pack's live sky buffers instead of copying an ordinary color attachment. Alpha.5 captures visible custom colors before final world composition and copies them afterward.

The historical alpha.1 resource run shows the generated magenta sun with a cyan cross through both the live ceiling (`01-day.png`) and the custom noon ceiling (`16-custom-noon.png`). The historical alpha.1 Iris run was visually inspected for native pack clouds, bloom, night sky, storm darkening, isolated custom weather, foreground occlusion, nearest-window selection, and reset to live mode. This run includes the depth release immediately before native cloud rendering. All three runs retain `COMPLETE.txt`, `measurements.txt`, `client.log`, and 22 PNG captures. Every recorded room light sample was SKY 15 and BLOCK 0.

Durable examples: [independent Iris skies](images/iris-independent-skies.png), [live replacement sun](images/resource-live-sun.png), [custom replacement sun](images/resource-custom-sun.png).

Client checks used an Intel Mac with a **Radeon Pro 560X**, **OpenGL 4.1**, and **2560 × 1440** captures. These checks establish coverage for the listed configurations, not arbitrary shader packs.

For Iris reproduction, select Complementary Reimagined r5.9.3 in the isolated smoke instance and run:

```sh
./gradlew -PirisSmoke runClientSmoke --console plain --no-daemon
```

For resource reproduction, generate and enable the replacement-sun pack in the isolated instance:

```sh
python3 scripts/create-sky-test-pack.py
./gradlew -PresourceSmoke runClientSmoke --console plain --no-daemon
```

The 26.3 sun resource path is `assets/minecraft/textures/environment/celestial/sun.png`; older packs targeting `environment/sun.png` do not verify the new texture path.

## Limits and outstanding checks

- Custom skies use resource-pack textures with the mod's vanilla-style sky and weather effects. They do not reproduce arbitrary shader-pack skies, clouds, or final postprocessing.
- Shader shadows, cave fog, exposure, and path-traced lighting still use the actual underground camera and solid roof. Cloud clipping and temporal effects can alter aperture edges; edge behavior needs further checks across packs and projections.
- Live world precipitation uses the real heightmap. Custom snow and storms are visual and do not create world precipitation, snow layers, lightning, or thunder sounds.
- Custom sky settings leave real room lighting tied to real time and weather. Custom mode's moon phase follows the real world; its base color comes from the current biome/dimension before applying the chosen custom time and weather.
- Hot resource reload, shader toggling, resizing, dimension transitions, glass/water foreground views, a complete disk reload, and remote multiplayer need dedicated client checks.
- This artifact supports 26.3 only. Separate source projects target stable releases down to 1.20.1. Verification remains in progress, and this source snapshot does not include a finished compatibility bundle.
