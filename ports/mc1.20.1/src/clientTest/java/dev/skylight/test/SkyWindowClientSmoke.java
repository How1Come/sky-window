package dev.skylight.test;

import dev.skylight.SkylightMod;
import dev.skylight.block.SkyWindowBlockEntity;
import dev.skylight.client.SkyWindowSettingsScreen;
import dev.skylight.client.SkyWindowControls;
import dev.skylight.client.SkyWindowRenderer;
import dev.skylight.settings.SkyWindowSettings;
import dev.skylight.settings.SkyWindowSettingsService;
import dev.skylight.settings.SkyWindowSettings.Weather;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.util.InputUtil;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.LightType;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SkyWindowClientSmoke implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("sky_window_client_test");
    private static final BlockPos ROOM_ANCHOR = new BlockPos(0, 12, 0);
    private static final BlockPos DEMO_ANCHOR = new BlockPos(31, 8, 0);
    private static final BlockPos STACK_FAR_ANCHOR = new BlockPos(55, 12, 1);
    private static final BlockPos STACK_NEAR_ANCHOR = new BlockPos(55, 8, 1);
    private static final SkyWindowSettings NIGHT_THUNDER = new SkyWindowSettings(false, 18000, Weather.THUNDER);
    private static final SkyWindowSettings DAY_SNOW = new SkyWindowSettings(false, 6000, Weather.SNOW);
    private static final boolean MOVEMENT = Boolean.getBoolean("sky_window.smoke.movement");
    private static final boolean MANUAL_MOVEMENT = Boolean.getBoolean("sky_window.smoke.manualMovement");
    private static final SkyWindowSettings MOVEMENT_SETTINGS = new SkyWindowSettings(false, 6000, Weather.CLEAR);
    private static final Stage[] STAGES = MOVEMENT ? new Stage[] {
            new Stage("23-walk-still-no-bob", 6000, false, false, false, 84.5, 1, 0.5, -70, 100),
            new Stage("24-walk-moving-no-bob", 6000, false, false, false, 84.5, 1, 0.5, -70, 180),
            new Stage("25-walk-moving-bob", 6000, false, false, false, 84.5, 1, 0.5, -70, 180),
            new Stage("26-walk-moving-bob-rebuild", 6000, false, false, false, 84.5, 1, 0.5, -70, 200)
    } : new Stage[] {
            new Stage("01-day", 6000, false, false, false, 0.5, 2, 0.5, -70, 120),
            new Stage("02-cloud-motion", 6000, false, false, false, 0.5, 2, 0.5, -70, 240),
            new Stage("03-sunset", 12000, false, false, false, 0.5, 2, 0.5, -70, 80),
            new Stage("04-night", 18000, false, false, false, 0.5, 2, 0.5, -70, 80),
            new Stage("05-rain", 6000, true, false, false, 0.5, 2, 0.5, -70, 160),
            new Stage("06-thunder", 6000, true, true, false, 0.5, 2, 0.5, -70, 160),
            new Stage("07-foreground", 6000, false, false, true, 0.5, 2, 0.5, -70, 160),
            new Stage("08-room-day", 6000, false, false, false, 0.5, 2, 0.5, 0, 80),
            new Stage("09-room-night", 18000, false, false, false, 0.5, 2, 0.5, 0, 80),
            new Stage("10-top", 6000, false, false, false, 18.5, 7, -3, 55, 100),
            new Stage("11-side", 6000, false, false, false, 18.5, 3, -3, 0, 60),
            new Stage("12-settings-panel", 6000, false, false, false, 0.5, 9, 0.5, -70, 80,
                    ROOM_ANCHOR, NIGHT_THUNDER, true),
            new Stage("13-custom-night-thunder", 6000, false, false, false, 0.5, 2, 0.5, -70, 140,
                    ROOM_ANCHOR, NIGHT_THUNDER, false),
            new Stage("14-custom-snow", 6000, false, false, false, 0.5, 2, 0.5, -70, 100,
                    ROOM_ANCHOR, DAY_SNOW, false),
            new Stage("15-independent-skies", 6000, false, false, false, 32.5, 4, -1.5, -45, 140,
                    DEMO_ANCHOR, NIGHT_THUNDER, false),
            new Stage("16-custom-noon", 6000, false, false, false, 0.5, 2, 0.5, -70, 100,
                    ROOM_ANCHOR, new SkyWindowSettings(false, 6000, Weather.CLEAR), false),
            new Stage("17-custom-foreground", 6000, false, false, true, 0.5, 2, 0.5, -70, 120,
                    ROOM_ANCHOR, NIGHT_THUNDER, false),
            new Stage("18-stacked-live-near", 6000, false, false, false, 56, 4, 1.5, -80, 120,
                    STACK_FAR_ANCHOR, NIGHT_THUNDER, false),
            new Stage("19-stacked-live-far", 6000, false, false, false, 56, 4, 1.5, -80, 100,
                    STACK_FAR_ANCHOR, SkyWindowSettings.LIVE, false),
            new Stage("20-stacked-custom-near", 6000, false, false, false, 56, 4, 1.5, -80, 120,
                    STACK_NEAR_ANCHOR, NIGHT_THUNDER, false),
            new Stage("21-reset-live", 6000, false, false, false, 0.5, 2, 0.5, -70, 100,
                    ROOM_ANCHOR, SkyWindowSettings.LIVE, false),
            new Stage("22-reset-detached", 6000, false, false, false, 32.5, 4, -1.5, -45, 100,
                    DEMO_ANCHOR, SkyWindowSettings.LIVE, false)
    };
    private final Path output = Path.of(System.getProperty("sky_window.smoke.output", "../../build/render-test"));
    private Vec3d motionStart;
    private CompletableFuture<Void> indexStress;
    private boolean indexStressVerified;
    private boolean started;
    private boolean preparing;
    private boolean complete;
    private volatile boolean ready;
    private int stageIndex;
    private int stageTicks;
    private long startedAt;
    private boolean settingsStarted;
    private boolean settingsSynchronized;
    private volatile boolean settingsVerified;
    private SkyWindowSettings appliedSettings;
    private SkyWindowSettings expectedRoom = SkyWindowSettings.LIVE;
    private SkyWindowSettings expectedDemo = SkyWindowSettings.LIVE;
    private SkyWindowSettings expectedStackFar = SkyWindowSettings.LIVE;
    private SkyWindowSettings expectedStackNear = SkyWindowSettings.LIVE;

    @Override
    public void onInitializeClient() {
        if (System.getProperty("sky_window.smoke.output") == null) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        if (complete) {
            return;
        }
        try {
            if (!started) {
                if (client.currentScreen instanceof TitleScreen && client.getOverlay() == null) {
                    start(client);
                }
                return;
            }
            if (System.nanoTime() - startedAt > 480_000_000_000L) {
                throw new IllegalStateException("Render smoke test exceeded eight minutes");
            }
            if (client.world == null || client.player == null || client.getServer() == null) {
                return;
            }
            if (!preparing) {
                preparing = true;
                UUID playerId = client.player.getUuid();
                IntegratedServer server = client.getServer();
                server.execute(() -> {
                    try {
                        prepare(server, playerId);
                        applyStage(server, playerId, STAGES[0]);
                    } catch (Throwable failure) {
                        client.execute(() -> fail(client, failure));
                    }
                });
                return;
            }
            if (!ready) {
                return;
            }
            Stage stage = STAGES[stageIndex];
            if (MOVEMENT && indexStress == null) {
                indexStress = stressWindowIndex(client.world);
            }
            if (!stage.screenOnly() && !settingsStarted) {
                client.setScreen(null);
            }
            client.player.setYaw(MOVEMENT ? -90 : 0);
            client.player.setPitch(stage.pitch());
            boolean walking = MOVEMENT && stageIndex > 0 && stageTicks >= 19;
            client.player.getAbilities().flying = !MOVEMENT;
            client.options.forwardKey.setPressed(walking);
            client.options.getBobView().setValue(MOVEMENT && stage.name().contains("-bob")
                    && !stage.name().contains("no-bob"));
            stageTicks++;
            if (walking) {
                if (stageTicks == 20) {
                    motionStart = client.player.getPos();
                }
                if (stageTicks >= 18 && stageTicks % 8 == 2) {
                    try (NativeImage frame = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                        frame.writeTo(output.resolve(stage.name() + "-frame-" + stageTicks + ".png"));
                    }
                }
                if (stageTicks == stage.waitTicks()) {
                    double distance = client.player.getPos().subtract(motionStart).horizontalLength();
                    if (distance < 1.5 || !client.player.isOnGround() || client.player.horizontalSpeed <= 0) {
                        throw new IllegalStateException("Walking regression did not perform grounded player movement: " + distance);
                    }
                    LOGGER.info("SKY_WINDOW_WALKING_VERIFIED {} distance={} bob={} position={}", stage.name(),
                            distance, client.options.getBobView().getValue(), client.player.getPos());
                }
            }
            if (MOVEMENT && stage.name().endsWith("rebuild") && stageTicks % 20 == 0) {
                int changingX = (int) client.player.getX() + 4;
                boolean glass = stageTicks % 40 != 0;
                client.getServer().execute(() -> client.getServer().getOverworld().setBlockState(
                        new BlockPos(changingX, 4, -5), (glass ? Blocks.GLASS : Blocks.STONE_BRICKS).getDefaultState(), Block.NOTIFY_ALL));
            }
            if (stage.settings() != null && stageTicks >= 20) {
                updateSettingsStage(client, stage);
            }
            if (stageTicks < stage.waitTicks() || stage.settings() != null && !stage.screenOnly() && !settingsVerified) {
                return;
            }
            if (MOVEMENT && !indexStressVerified) {
                if (!indexStress.isDone()) {
                    return;
                }
                indexStress.join();
                indexStressVerified = true;
                Files.writeString(output.resolve("index-concurrency.txt"), "Four concurrent workers completed 262144 add/remove operations while rendering.\n");
                LOGGER.info("SKY_WINDOW_INDEX_CONCURRENCY_VERIFIED workers=4 operations=262144");
            }
            capture(client, stage);
            client.options.forwardKey.setPressed(false);
            ready = false;
            stageTicks = 0;
            settingsStarted = false;
            settingsSynchronized = false;
            settingsVerified = false;
            client.setScreen(null);
            if (++stageIndex == STAGES.length) {
                Files.writeString(output.resolve("COMPLETE.txt"), "Captured " + STAGES.length + " render scenarios. Inspect the PNG files for visual correctness.\n");
                complete = true;
                LOGGER.info("SKY_WINDOW_RENDER_SMOKE_COMPLETE {}", output.toAbsolutePath());
                client.scheduleStop();
                return;
            }
            UUID playerId = client.player.getUuid();
            IntegratedServer server = client.getServer();
            Stage next = STAGES[stageIndex];
            server.execute(() -> {
                try {
                    applyStage(server, playerId, next);
                } catch (Throwable failure) {
                    client.execute(() -> fail(client, failure));
                }
            });
        } catch (Throwable failure) {
            fail(client, failure);
        }
    }

    private void start(MinecraftClient client) throws IOException {
        started = true;
        startedAt = System.nanoTime();
        Files.createDirectories(output);
        Files.deleteIfExists(output.resolve("COMPLETE.txt"));
        Files.deleteIfExists(output.resolve("FAILED.txt"));
        Files.deleteIfExists(output.resolve("compatibility-proof.json"));
        Files.writeString(output.resolve("pixels.txt"), "");
        Files.writeString(output.resolve("measurements.txt"), "Sky Window render smoke test\n");
        client.options.pauseOnLostFocus = false;
        client.options.hudHidden = true;
        client.options.getViewDistance().setValue(8);
        client.options.getSimulationDistance().setValue(5);
        client.options.getMaxFps().setValue(60);
        client.options.getEnableVsync().setValue(false);
        client.options.getCloudRenderMode().setValue(MOVEMENT ? CloudRenderMode.OFF : CloudRenderMode.FANCY);
        client.options.getFov().setValue(70);
        client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue(0.0);
        client.options.setPerspective(Perspective.FIRST_PERSON);
        GameRules rules = new GameRules();
        rules.get(GameRules.DO_DAYLIGHT_CYCLE).set(false, null);
        rules.get(GameRules.DO_WEATHER_CYCLE).set(false, null);
        rules.get(GameRules.DO_MOB_SPAWNING).set(false, null);
        LevelInfo info = new LevelInfo("Sky Window Render Smoke", GameMode.CREATIVE, false, Difficulty.PEACEFUL, true, rules, DataConfiguration.SAFE_MODE);
        client.createIntegratedServerLoader().createAndStart(
                "sky-window-smoke-" + System.currentTimeMillis(), info, new GeneratorOptions(42L, false, false),
                registries -> registries.get(RegistryKeys.WORLD_PRESET).entryOf(WorldPresets.FLAT).value().createDimensionsRegistryHolder());
    }

    private void prepare(IntegratedServer server, UUID playerId) {
        ServerWorld world = server.getOverworld();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = 0; y <= 24; y++) {
                    boolean solid = x == -12 || x == 12 || z == -12 || z == 12 || y == 0 || y >= 12;
                    world.setBlockState(pos.set(x, y, z), (solid ? Blocks.STONE_BRICKS : Blocks.AIR).getDefaultState(), Block.NOTIFY_ALL);
                }
                if (Math.abs(x) <= 7 && Math.abs(z) <= 7) {
                    world.setBlockState(pos.set(x, 12, z), SkylightMod.SKY_WINDOW.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
        for (int x = 16; x <= 20; x++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlockState(pos.set(x, 2, z), Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
        world.setBlockState(new BlockPos(18, 4, 0), SkylightMod.SKY_WINDOW.getDefaultState(), Block.NOTIFY_ALL);
        for (int x = 25; x <= 40; x++) {
            for (int z = -5; z <= 8; z++) {
                world.setBlockState(pos.set(x, 0, z), Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
                if ((x >= 28 && x <= 31 || x >= 33 && x <= 36) && z >= 0 && z <= 3) {
                    world.setBlockState(pos.set(x, 8, z), SkylightMod.SKY_WINDOW.getDefaultState(), Block.NOTIFY_ALL);
                    for (int y = 9; y <= 12; y++) {
                        world.setBlockState(pos.set(x, y, z), Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
            }
        }
        for (int x = 48; x <= 63; x++) {
            for (int z = -8; z <= 11; z++) {
                world.setBlockState(pos.set(x, 0, z), Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
                if (x >= 50 && x <= 61 && z >= -4 && z <= 7) {
                    world.setBlockState(pos.set(x, 12, z), SkylightMod.SKY_WINDOW.getDefaultState(), Block.NOTIFY_ALL);
                    for (int y = 13; y <= 16; y++) {
                        world.setBlockState(pos.set(x, y, z), Blocks.STONE_BRICKS.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
                if (x >= 54 && x <= 57 && z >= 0 && z <= 3) {
                    world.setBlockState(pos.set(x, 8, z), SkylightMod.SKY_WINDOW.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
        if (MOVEMENT) {
            for (int x = 80; x <= 144; x++) {
                for (int z = -5; z <= 5; z++) {
                    for (int y = 0; y <= 16; y++) {
                        Block block = y == 12 && Math.abs(z) < 5 ? SkylightMod.SKY_WINDOW
                                : y == 0 || y >= 12 || Math.abs(z) == 5 ? Blocks.STONE_BRICKS : Blocks.AIR;
                        world.setBlockState(pos.set(x, y, z), block.getDefaultState(), Block.NOTIFY_ALL);
                        if (MANUAL_MOVEMENT && block == SkylightMod.SKY_WINDOW) {
                            requireWindow(world.getBlockEntity(pos), pos).setSettings(MOVEMENT_SETTINGS);
                        }
                    }
                }
            }
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("No local player joined the render test world");
        }
        player.changeGameMode(GameMode.CREATIVE);
        player.getAbilities().flying = true;
        player.sendAbilitiesUpdate();
    }

    private void applyStage(IntegratedServer server, UUID playerId, Stage stage) {
        ServerWorld world = server.getOverworld();
        world.setTimeOfDay(stage.time());
        world.setWeather(stage.rain() ? 0 : 6000, 6000, stage.rain(), stage.thunder());
        for (int x = -3; x <= 3; x++) {
            world.setBlockState(new BlockPos(x, 8, 2), (stage.foreground() ? Blocks.RED_WOOL : Blocks.AIR).getDefaultState(), Block.NOTIFY_ALL);
        }
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("Render test player disconnected");
        }
        if (stage.anchor() != null) {
            player.teleport(world, stage.anchor().getX() + 0.5, stage.anchor().getY() - 3,
                    stage.anchor().getZ() + 0.5, 0, stage.pitch());
        } else {
            player.teleport(world, stage.x(), stage.y(), stage.z(), 0, stage.pitch());
        }
        player.getAbilities().flying = !MOVEMENT;
        player.sendAbilitiesUpdate();
        LOGGER.info("SKY_WINDOW_RENDER_STAGE {}", stage.name());
        ready = true;
    }

    private void updateSettingsStage(MinecraftClient client, Stage stage) {
        if (!settingsStarted) {
            settingsStarted = true;
            SkyWindowBlockEntity entity = requireWindow(client.world.getBlockEntity(stage.anchor()), stage.anchor());
            SkyWindowSettingsScreen screen;
            if (stage.screenOnly()) {
                screen = verifyControlsAndOpen(client, stage.anchor());
            } else {
                screen = new SkyWindowSettingsScreen(stage.anchor(), entity.getSettings());
                client.setScreen(screen);
            }
            configureScreen(screen, stage.settings());
            appliedSettings = screen.getDraftSettings();
            if (stage.settings().followWorld() ? !appliedSettings.followWorld() : !appliedSettings.equals(stage.settings())) {
                throw new IllegalStateException("Settings widgets did not produce the requested values: " + appliedSettings);
            }
            if (!stage.screenOnly()) {
                ButtonWidget apply = screen.children().stream()
                        .filter(child -> child instanceof ButtonWidget)
                        .map(child -> (ButtonWidget) child)
                        .filter(button -> button.getMessage().getString().equals(Text.translatable("screen.sky_window.apply").getString()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Settings panel has no Apply button"));
                click(apply);
            }
        }
        if (stage.screenOnly() || settingsSynchronized) {
            return;
        }
        SkyWindowBlockEntity entity = requireWindow(client.world.getBlockEntity(stage.anchor()), stage.anchor());
        if (!entity.getSettings().equals(appliedSettings)) {
            if (stageTicks > 120) {
                throw new IllegalStateException("Apply did not synchronize settings to the client for " + stage.name());
            }
            return;
        }
        settingsSynchronized = true;
        if (stage.anchor().equals(ROOM_ANCHOR)) {
            expectedRoom = appliedSettings;
        } else if (stage.anchor().equals(DEMO_ANCHOR)) {
            expectedDemo = appliedSettings;
        } else if (stage.anchor().equals(STACK_FAR_ANCHOR)) {
            expectedStackFar = appliedSettings;
        } else if (stage.anchor().equals(STACK_NEAR_ANCHOR)) {
            expectedStackNear = appliedSettings;
        } else {
            throw new IllegalStateException("Unknown settings test anchor " + stage.anchor());
        }
        UUID playerId = client.player.getUuid();
        IntegratedServer server = client.getServer();
        server.execute(() -> {
            try {
                verifySettings(server.getOverworld());
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
                if (player == null) {
                    throw new IllegalStateException("Render test player disconnected");
                }
                player.teleport(server.getOverworld(), stage.x(), stage.y(), stage.z(), 0, stage.pitch());
                settingsVerified = true;
            } catch (Throwable failure) {
                client.execute(() -> fail(client, failure));
            }
        });
    }

    private static SkyWindowSettingsScreen verifyControlsAndOpen(MinecraftClient client, BlockPos anchor) {
        KeyBinding modifier = SkyWindowControls.modifier();
        InputUtil.Key original = KeyBindingHelper.getBoundKeyOf(modifier);
        InputUtil.Key leftShift = InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_LEFT_SHIFT);
        InputUtil.Key rebound = InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_R);
        InputUtil.Key use = KeyBindingHelper.getBoundKeyOf(client.options.useKey);
        try {
            modifier.setBoundKey(leftShift);
            KeyBinding.updateKeysByCode();
            KeyBinding.setKeyPressed(leftShift, true);
            if (!modifier.isPressed() || !client.options.sneakKey.isPressed()) {
                throw new IllegalStateException("Default settings modifier consumed the shared Sneak key binding");
            }
            KeyBinding.setKeyPressed(leftShift, false);
            modifier.setBoundKey(rebound);
            KeyBinding.updateKeysByCode();
            KeyBinding.setKeyPressed(leftShift, true);
            if (modifier.isPressed() || !client.options.sneakKey.isPressed()) {
                throw new IllegalStateException("Rebinding settings modifier changed the Sneak key binding");
            }
            KeyBinding.setKeyPressed(leftShift, false);
            KeyBinding.setKeyPressed(use, true);
            if (!client.options.useKey.isPressed()) {
                throw new IllegalStateException("Settings modifier consumed the Use Item key binding");
            }
            KeyBinding.setKeyPressed(use, false);
            KeyBinding.setKeyPressed(rebound, true);
            client.crosshairTarget = new BlockHitResult(Vec3d.ofBottomCenter(anchor), Direction.DOWN, anchor, false);
            if (!modifier.isPressed() || !SkyWindowControls.tryOpenSettings()
                    || !(client.currentScreen instanceof SkyWindowSettingsScreen screen)) {
                throw new IllegalStateException("Rebound settings modifier did not open the ceiling panel");
            }
            LOGGER.info("SKY_WINDOW_CONTROLS_VERIFIED default_shift=shared_with_sneak rebound=R use=preserved");
            return screen;
        } finally {
            KeyBinding.setKeyPressed(leftShift, false);
            KeyBinding.setKeyPressed(rebound, false);
            KeyBinding.setKeyPressed(use, false);
            modifier.setPressed(false);
            client.options.sneakKey.setPressed(false);
            modifier.setBoundKey(original);
            KeyBinding.updateKeysByCode();
        }
    }

    private static void configureScreen(SkyWindowSettingsScreen screen, SkyWindowSettings settings) {
        CyclingButtonWidget<?> mode = screen.children().stream()
                .filter(child -> child instanceof CyclingButtonWidget<?> button && button.getValue() instanceof Boolean)
                .map(child -> (CyclingButtonWidget<?>) child)
                .findFirst().orElseThrow(() -> new IllegalStateException("Settings panel has no Follow world control"));
        if (!mode.getValue().equals(settings.followWorld())) {
            click(mode);
        }
        if (settings.followWorld()) {
            return;
        }
        SliderWidget slider = screen.children().stream()
                .filter(child -> child instanceof SliderWidget)
                .map(child -> (SliderWidget) child)
                .findFirst().orElseThrow(() -> new IllegalStateException("Settings panel has no time slider"));
        double sliderX = slider.getX() + 4 + (settings.time() + 0.1) / 23999.0 * (slider.getWidth() - 8);
        slider.mouseClicked(sliderX, slider.getY() + slider.getHeight() / 2.0, 0);
        slider.mouseReleased(sliderX, slider.getY() + slider.getHeight() / 2.0, 0);
        CyclingButtonWidget<?> weather = screen.children().stream()
                .filter(child -> child instanceof CyclingButtonWidget<?> button && button.getValue() instanceof Weather)
                .map(child -> (CyclingButtonWidget<?>) child)
                .findFirst().orElseThrow(() -> new IllegalStateException("Settings panel has no weather control"));
        for (int attempts = 0; attempts < Weather.values().length && !weather.getValue().equals(settings.weather()); attempts++) {
            click(weather);
        }
    }

    private static void click(ClickableWidget widget) {
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        if (!widget.mouseClicked(x, y, 0)) {
            throw new IllegalStateException("Could not click " + widget.getMessage().getString());
        }
        widget.mouseReleased(x, y, 0);
    }

    private void verifySettings(ServerWorld world) {
        if (world.getTimeOfDay() != 6000 || world.isRaining() || world.isThundering()) {
            throw new IllegalStateException("Changing ceiling settings changed the real world time or weather");
        }
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                assertSettings(world, new BlockPos(x, 12, z), expectedRoom);
            }
        }
        for (int x = 28; x <= 36; x++) {
            if (x == 32) {
                continue;
            }
            for (int z = 0; z <= 3; z++) {
                assertSettings(world, new BlockPos(x, 8, z), x <= 31 ? expectedDemo : SkyWindowSettings.LIVE);
            }
        }
        for (int x = 50; x <= 61; x++) {
            for (int z = -4; z <= 7; z++) {
                assertSettings(world, new BlockPos(x, 12, z), expectedStackFar);
                if (x >= 54 && x <= 57 && z >= 0 && z <= 3) {
                    assertSettings(world, new BlockPos(x, 8, z), expectedStackNear);
                }
            }
        }
        assertSettings(world, new BlockPos(18, 4, 0), SkyWindowSettings.LIVE);
        LOGGER.info("SKY_WINDOW_SETTINGS_VERIFIED room=225 custom_demo=16 independent_demo=16 stacked_far=144 stacked_near=16 world_time=6000 world_weather=clear");
    }

    private static void assertSettings(ServerWorld world, BlockPos pos, SkyWindowSettings expected) {
        SkyWindowSettings actual = requireWindow(world.getBlockEntity(pos), pos).getSettings();
        if (!actual.equals(expected)) {
            throw new IllegalStateException("Unexpected connected ceiling settings at " + pos + ": " + actual + ", expected " + expected);
        }
    }

    private static SkyWindowBlockEntity requireWindow(Object entity, BlockPos pos) {
        if (entity instanceof SkyWindowBlockEntity window) {
            return window;
        }
        throw new IllegalStateException("No Sky Window block entity at " + pos);
    }

    private static CompletableFuture<Void> stressWindowIndex(ClientWorld world) {
        CompletableFuture<?>[] workers = new CompletableFuture<?>[4];
        for (int worker = 0; worker < workers.length; worker++) {
            int originX = 4096 + worker * 32;
            workers[worker] = CompletableFuture.runAsync(() -> {
                for (int round = 0; round < 32; round++) {
                    for (int position = 0; position < 1024; position++) {
                        SkyWindowRenderer.add(world, new BlockPos(originX + position % 32, 12, position / 32));
                    }
                    for (int position = 0; position < 1024; position++) {
                        SkyWindowRenderer.remove(world, new BlockPos(originX + position % 32, 12, position / 32));
                    }
                }
            });
        }
        return CompletableFuture.allOf(workers);
    }

    private void capture(MinecraftClient client, Stage stage) throws IOException {
        if (MOVEMENT && stageIndex > 0 && client.player.getX() < 97) {
            throw new IllegalStateException("Walking did not cross the ceiling chunk boundary: x=" + client.player.getX());
        }
        if (MANUAL_MOVEMENT) {
            if (client.world.getTimeOfDay() != 6000 || client.world.getRainGradient(1) != 0
                    || client.world.getThunderGradient(1) != 0) {
                throw new IllegalStateException("Manual movement ceiling changed the real world time or weather");
            }
            for (int x = 80; x <= 144; x++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = new BlockPos(x, 12, z);
                    if (!requireWindow(client.world.getBlockEntity(pos), pos).getSettings().equals(MOVEMENT_SETTINGS)) {
                        throw new IllegalStateException("Manual movement settings did not synchronize at " + pos);
                    }
                }
            }
        }
        if (stage.settings() != null && (client.world.getTimeOfDay() != 6000
                || client.world.getRainGradient(1) != 0 || client.world.getThunderGradient(1) != 0)) {
            throw new IllegalStateException("Custom ceiling leaked time or weather into the client world");
        }
        try (NativeImage screenshot = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
            screenshot.writeTo(output.resolve(stage.name() + ".png"));
            if (MOVEMENT) {
                int skyPixels = 0;
                int samples = 0;
                for (int x = screenshot.getWidth() * 2 / 5; x < screenshot.getWidth() * 3 / 5; x += 4) {
                    for (int y = screenshot.getHeight() / 5; y < screenshot.getHeight() * 2 / 5; y += 4) {
                        int color = screenshot.getColor(x, y);
                        int red = color & 255;
                        int green = color >> 8 & 255;
                        int blue = color >> 16 & 255;
                        if (blue > 190 && green > 130 && blue > red + 20 && blue > green + 10
                                || red > 235 && green > 235 && blue > 235) {
                            skyPixels++;
                        }
                        samples++;
                    }
                }
                double fraction = (double) skyPixels / samples;
                Files.writeString(output.resolve("pixels.txt"), stage.name() + " sky_fraction=" + fraction + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                LOGGER.info("SKY_WINDOW_MOVEMENT_PIXELS {} sky_fraction={}", stage.name(), fraction);
                if (Boolean.getBoolean("sky_window.smoke.assertMovement") && fraction < 0.995) {
                    throw new IllegalStateException("Fallback ceiling pixels during " + stage.name() + ": clear sky fraction=" + fraction);
                }
            }
        }
        BlockPos sample = new BlockPos(0, 2, 0);
        String measurement = stage.name() + " time=" + client.world.getTimeOfDay()
                + " rain=" + client.world.getRainGradient(1)
                + " thunder=" + client.world.getThunderGradient(1)
                + " sky_light=" + client.world.getLightLevel(LightType.SKY, sample)
                + " block_light=" + client.world.getLightLevel(LightType.BLOCK, sample)
                + " player_position=" + client.player.getPos() + " bob=" + client.options.getBobView().getValue()
                + (MOVEMENT ? " manual=" + MANUAL_MOVEMENT : "")
                + (stage.settings() == null ? "" : " ceiling=" + requireWindow(client.world.getBlockEntity(stage.anchor()), stage.anchor()).getSettings())
                + "\n";
        Files.writeString(output.resolve("measurements.txt"), measurement, StandardOpenOption.APPEND);
        LOGGER.info("SKY_WINDOW_RENDER_CAPTURE {}", stage.name());
    }

    private void fail(MinecraftClient client, Throwable failure) {
        complete = true;
        client.options.forwardKey.setPressed(false);
        LOGGER.error("SKY_WINDOW_RENDER_SMOKE_FAILED", failure);
        try {
            Files.createDirectories(output);
            Files.writeString(output.resolve("FAILED.txt"), failure.toString() + "\n");
        } catch (IOException secondary) {
            LOGGER.error("Could not write smoke test failure", secondary);
        }
        client.scheduleStop();
    }

    private record Stage(String name, long time, boolean rain, boolean thunder, boolean foreground,
                         double x, double y, double z, float pitch, int waitTicks,
                         BlockPos anchor, SkyWindowSettings settings, boolean screenOnly) {
        private Stage(String name, long time, boolean rain, boolean thunder, boolean foreground,
                      double x, double y, double z, float pitch, int waitTicks) {
            this(name, time, rain, thunder, foreground, x, y, z, pitch, waitTicks, null, null, false);
        }
    }
}
