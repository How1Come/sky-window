# Sky Window for Minecraft 1.20.1

This Fabric build targets Minecraft Java 1.20.1 only. Install its matching JAR on the server and each client, together with Fabric Loader 0.16.14 or newer and Fabric API 0.92.12+1.20.1 or newer for this Minecraft version. Java 17 or newer is required.

## Install and use

1. Copy `build/libs/sky-window-0.3.0-alpha.1+mc1.20.1.jar` into the Minecraft instance's `mods` folder.
2. Find Sky Window in the Functional Blocks creative tab, or craft eight blocks using six quartz, two glass and one ender pearl.
3. Replace a ceiling with Sky Window blocks. The underside shows the live native sky and supplies real SKY light underneath. The top and sides retain the normal block appearance.
4. Hold Left Shift and use a block to open its settings. Rebind the modifier in Minecraft Controls; the Use Item key also follows Minecraft Controls.
5. Choose live mode or a custom time and sunny, rainy, thunderstorm or snowy appearance. Apply updates the entire loaded component joined by shared block faces. Edge and corner contact does not join ceilings.

Custom time and weather affect only the window image. They do not change world time, weather or biome. Actual SKY light still follows the world. Custom presets use resource-pack textures; arbitrary shader-pack atmosphere and volumetric clouds are not reproduced. Live mode retains the native renderer's shader and resource-pack effects. Depth-nonwriting translucent foreground, such as some glass, water and particles, is not covered by the custom-preset occlusion guarantee.

Apply checks server reach and edit permissions, requires all neighboring chunks to be loaded and rejects the whole change when its connected component exceeds 16,384 blocks. Settings persist and synchronize with the block entities.

## Build and verify

Run `./gradlew build` from this folder with a Java 17 JDK. The build runs the mod's server GameTests and creates the production JAR; it excludes the test mods. Run `./gradlew runClientSmoke` for the 22 visual and settings scenarios. Add `-PmovementSmoke -PirisDisabledSmoke` for the separate four-case walking regression with Iris installed and shaders disabled; it checks standing, walking, view bobbing and chunk rebuilds across a ceiling chunk boundary. Run with `-PirisSmoke` to include the version-specific Iris and Sodium dependencies; shader-pack installation and activation are separate Minecraft instance settings. For shader movement, install a pack under `run/client-smoke/shaderpacks` and add `-PmovementSmoke -PirisSmoke -PsmokeShaderPack=PACK.zip`. For resource movement, generate the test pack and add `-PmovementSmoke -PresourceSmoke`. These pack runs record pixel measurements without enforcing vanilla sky colors. Use `-PsmokeOutput=build/OUTPUT` to keep each run separate. The resource integration test pack can be generated with `python3 scripts/create-sky-test-pack.py`.

Server verification logs are preserved under `build/verification/server-gametest.log`. Visual outputs use `build/render-test`, `build/render-test-iris`, `build/render-test-resources` and `build/render-test-movement`, `build/render-test-movement-iris-off`, `build/render-test-movement-iris-on` and `build/render-test-movement-resources`.
