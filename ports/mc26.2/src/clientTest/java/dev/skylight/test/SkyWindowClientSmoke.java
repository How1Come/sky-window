package dev.skylight.test;

import dev.skylight.SkylightMod;
import dev.skylight.block.SkyWindowBlockEntity;
import dev.skylight.client.SkyWindowSettingsScreen;
import dev.skylight.client.SkyWindowControls;
import dev.skylight.settings.SkyWindowSettings;
import dev.skylight.settings.SkyWindowSettings.Weather;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.LevelSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.Set;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

public final class SkyWindowClientSmoke implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("sky_window_client_test");
    private static final BlockPos ROOM_ANCHOR = new BlockPos(0, 12, 0);
    private static final BlockPos DEMO_ANCHOR = new BlockPos(31, 8, 0);
    private static final BlockPos STACK_FAR_ANCHOR = new BlockPos(55, 12, 1);
    private static final BlockPos STACK_NEAR_ANCHOR = new BlockPos(55, 8, 1);
    private static final BlockPos LIGHT_SAMPLE = new BlockPos(0, 2, 0);
    private static final SkyWindowSettings NIGHT_THUNDER = new SkyWindowSettings(false, 18000, Weather.THUNDER);
    private static final SkyWindowSettings DAY_SNOW = new SkyWindowSettings(false, 6000, Weather.SNOW);
    private static final boolean MOVEMENT = Boolean.getBoolean("sky_window.smoke.movement");
    private static final boolean MANUAL_MOVEMENT = Boolean.getBoolean("sky_window.smoke.manualMovement");
    private static final SkyWindowSettings MOVEMENT_SETTINGS = new SkyWindowSettings(false, 6000, Weather.CLEAR);
    private static final Stage[] FULL_STAGES = {
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
    private static final Stage[] STAGES = MOVEMENT ? new Stage[] {
            new Stage("23-walk-still-no-bob", 6000, false, false, false, 84.5, 1, 0.5, -70, 100),
            new Stage("24-walk-moving-no-bob", 6000, false, false, false, 84.5, 1, 0.5, -70, 180),
            new Stage("25-walk-moving-bob", 6000, false, false, false, 84.5, 1, 0.5, -70, 180),
            new Stage("26-walk-moving-bob-rebuild", 6000, false, false, false, 84.5, 1, 0.5, -70, 200)
    } : FULL_STAGES;
    private final Path output = Path.of(System.getProperty("sky_window.smoke.output", "../../build/render-test"));
    private boolean started;
    private boolean preparing;
    private boolean complete;
    private volatile boolean ready;
    private int stageIndex;
    private int stageTicks;
    private long startedAt;
    private long lightingWaitStartedAt;
    private boolean captureStarted;
    private volatile boolean captureFinished;
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

    private void tick(Minecraft client) {
        if (complete) {
            return;
        }
        try {
            if (!started) {
                if (client.isGameLoadFinished() && client.gui.overlay() == null) {
                    start(client);
                }
                return;
            }
            if (System.nanoTime() - startedAt > 480_000_000_000L) {
                throw new IllegalStateException("Render smoke test exceeded eight minutes");
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null) {
                return;
            }
            if (!preparing) {
                preparing = true;
                UUID playerId = client.player.getUUID();
                IntegratedServer server = client.getSingleplayerServer();
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
            if (!stage.screenOnly() && !settingsStarted) {
                client.gui.setScreen(null);
            }
            client.player.setYRot(MOVEMENT ? -90 : 0);
            client.player.setXRot(stage.pitch());
            client.player.getAbilities().flying = !MOVEMENT;
            stageTicks++;
            if (MOVEMENT) {
                client.options.bobView().set(stage.name().contains("-bob") && !stage.name().contains("no-bob"));
                client.options.keyUp.setDown(stageIndex > 0 && stageTicks >= 20);
                if (stage.name().endsWith("rebuild") && stageTicks % 20 == 0) {
                    int changingX = (int) client.player.getX() + 4;
                    client.getSingleplayerServer().execute(() -> client.getSingleplayerServer().overworld().setBlock(
                            new BlockPos(changingX, 4, -5), (stageTicks % 40 == 0 ? Blocks.STONE_BRICKS : Blocks.GLASS).defaultBlockState(), Block.UPDATE_ALL));
                }
            }
            if (stage.settings() != null && stageTicks >= 20) {
                updateSettingsStage(client, stage);
            }
            if (stageTicks < stage.waitTicks() || stage.settings() != null && !stage.screenOnly() && !settingsVerified) {
                return;
            }
            int skyLight = client.level.getBrightness(LightLayer.SKY, LIGHT_SAMPLE);
            int blockLight = client.level.getBrightness(LightLayer.BLOCK, LIGHT_SAMPLE);
            if (skyLight != 15 || blockLight != 0) {
                if (lightingWaitStartedAt == 0) {
                    lightingWaitStartedAt = System.nanoTime();
                } else if (System.nanoTime() - lightingWaitStartedAt > 60_000_000_000L) {
                    throw new IllegalStateException("Ceiling lighting did not settle for " + stage.name()
                            + ": expected sky/block light 15/0, got " + skyLight + "/" + blockLight);
                }
                return;
            }
            lightingWaitStartedAt = 0;
            if (!captureStarted) {
                captureStarted = true;
                capture(client, stage);
                return;
            }
            if (!captureFinished) {
                return;
            }
            captureStarted = false;
            captureFinished = false;
            client.options.keyUp.setDown(false);
            ready = false;
            stageTicks = 0;
            settingsStarted = false;
            settingsSynchronized = false;
            settingsVerified = false;
            client.gui.setScreen(null);
            if (++stageIndex == STAGES.length) {
                Files.writeString(output.resolve("COMPLETE.txt"), "Captured " + STAGES.length + " render scenarios. Inspect the PNG files for visual correctness.\n");
                complete = true;
                LOGGER.info("SKY_WINDOW_RENDER_SMOKE_COMPLETE {}", output.toAbsolutePath());
                client.stop();
                return;
            }
            UUID playerId = client.player.getUUID();
            IntegratedServer server = client.getSingleplayerServer();
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

    private void start(Minecraft client) throws IOException {
        started = true;
        startedAt = System.nanoTime();
        Files.createDirectories(output);
        Files.deleteIfExists(output.resolve("COMPLETE.txt"));
        Files.deleteIfExists(output.resolve("FAILED.txt"));
        Files.writeString(output.resolve("measurements.txt"), "Sky Window render smoke test\n");
        client.options.pauseOnLostFocus = false;
        if (!client.gui.hud.isHidden()) {
            client.gui.hud.toggle();
        }
        client.options.renderDistance().set(8);
        client.options.simulationDistance().set(5);
        client.options.framerateLimit().set(60);
        client.options.enableVsync().set(false);
        client.options.cloudStatus().set(MOVEMENT ? CloudStatus.OFF : CloudStatus.FANCY);
        client.options.fov().set(70);
        client.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        client.options.setCameraType(CameraType.FIRST_PERSON);
        LevelSettings info = new LevelSettings("Sky Window Render Smoke", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        client.createWorldOpenFlows().createFreshLevel(
                "sky-window-smoke-" + System.currentTimeMillis(), info, new WorldOptions(42L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }

    private void prepare(IntegratedServer server, UUID playerId) {
        ServerLevel world = server.overworld();
        world.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
        world.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
        world.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = 0; y <= 24; y++) {
                    boolean solid = x == -12 || x == 12 || z == -12 || z == 12 || y == 0 || y >= 12;
                    world.setBlock(pos.set(x, y, z), (solid ? Blocks.STONE_BRICKS : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
                }
                if (Math.abs(x) <= 7 && Math.abs(z) <= 7) {
                    world.setBlock(pos.set(x, 12, z), SkylightMod.SKY_WINDOW.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        for (int x = 16; x <= 20; x++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlock(pos.set(x, 2, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        world.setBlock(new BlockPos(18, 4, 0), SkylightMod.SKY_WINDOW.defaultBlockState(), Block.UPDATE_ALL);
        for (int x = 25; x <= 40; x++) {
            for (int z = -5; z <= 8; z++) {
                world.setBlock(pos.set(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                if ((x >= 28 && x <= 31 || x >= 33 && x <= 36) && z >= 0 && z <= 3) {
                    world.setBlock(pos.set(x, 8, z), SkylightMod.SKY_WINDOW.defaultBlockState(), Block.UPDATE_ALL);
                    for (int y = 9; y <= 12; y++) {
                        world.setBlock(pos.set(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
        for (int x = 48; x <= 63; x++) {
            for (int z = -8; z <= 11; z++) {
                world.setBlock(pos.set(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                if (x >= 50 && x <= 61 && z >= -4 && z <= 7) {
                    world.setBlock(pos.set(x, 12, z), SkylightMod.SKY_WINDOW.defaultBlockState(), Block.UPDATE_ALL);
                    for (int y = 13; y <= 16; y++) {
                        world.setBlock(pos.set(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
                if (x >= 54 && x <= 57 && z >= 0 && z <= 3) {
                    world.setBlock(pos.set(x, 8, z), SkylightMod.SKY_WINDOW.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        if (MOVEMENT) {
            for (int x = 80; x <= 144; x++) {
                for (int z = -5; z <= 5; z++) {
                    for (int y = 0; y <= 16; y++) {
                        Block block = y == 12 && Math.abs(z) < 5 ? SkylightMod.SKY_WINDOW
                                : y == 0 || y >= 12 || Math.abs(z) == 5 ? Blocks.STONE_BRICKS : Blocks.AIR;
                        world.setBlock(pos.set(x, y, z), block.defaultBlockState(), Block.UPDATE_ALL);
                        if (MANUAL_MOVEMENT && block == SkylightMod.SKY_WINDOW) {
                            requireWindow(world.getBlockEntity(pos), pos).setSettings(MOVEMENT_SETTINGS);
                        }
                    }
                }
            }
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("No local player joined the render test world");
        }
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = !MOVEMENT;
        player.onUpdateAbilities();
    }

    private void applyStage(IntegratedServer server, UUID playerId, Stage stage) {
        ServerLevel world = server.overworld();
        world.clockManager().setTotalTicks(world.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK).getOrThrow(WorldClocks.OVERWORLD), stage.time());
        server.forceGameTimeSynchronization();
        server.setWeatherParameters(stage.rain() ? 0 : 6000, 6000, stage.rain(), stage.thunder());
        for (int x = -3; x <= 3; x++) {
            world.setBlock(new BlockPos(x, 8, 2), (stage.foreground() ? Blocks.WOOL.red() : Blocks.AIR).defaultBlockState(), Block.UPDATE_ALL);
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            throw new IllegalStateException("Render test player disconnected");
        }
        if (stage.anchor() != null) {
            player.teleportTo(world, stage.anchor().getX() + 0.5, stage.anchor().getY() - 3,
                    stage.anchor().getZ() + 0.5, Set.of(), 0, stage.pitch(), true);
        } else {
            player.teleportTo(world, stage.x(), stage.y(), stage.z(), Set.of(), 0, stage.pitch(), true);
        }
        player.getAbilities().flying = !MOVEMENT;
        player.onUpdateAbilities();
        LOGGER.info("SKY_WINDOW_RENDER_STAGE {}", stage.name());
        ready = true;
    }

    private void updateSettingsStage(Minecraft client, Stage stage) {
        if (!settingsStarted) {
            settingsStarted = true;
            SkyWindowBlockEntity entity = requireWindow(client.level.getBlockEntity(stage.anchor()), stage.anchor());
            SkyWindowSettingsScreen screen;
            if (stage.screenOnly()) {
                screen = verifyControlsAndOpen(client, stage.anchor());
            } else {
                screen = new SkyWindowSettingsScreen(stage.anchor(), entity.getSettings());
                client.gui.setScreen(screen);
            }
            configureScreen(screen, stage.settings());
            appliedSettings = screen.getDraftSettings();
            if (stage.settings().followWorld() ? !appliedSettings.followWorld() : !appliedSettings.equals(stage.settings())) {
                throw new IllegalStateException("Settings widgets did not produce the requested values: " + appliedSettings);
            }
            if (!stage.screenOnly()) {
                Button apply = screen.children().stream()
                        .filter(child -> child instanceof Button)
                        .map(child -> (Button) child)
                        .filter(button -> button.getMessage().getString().equals(Component.translatable("screen.sky_window.apply").getString()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Settings panel has no Apply button"));
                click(apply);
            }
        }
        if (stage.screenOnly() || settingsSynchronized) {
            return;
        }
        SkyWindowBlockEntity entity = requireWindow(client.level.getBlockEntity(stage.anchor()), stage.anchor());
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
        UUID playerId = client.player.getUUID();
        IntegratedServer server = client.getSingleplayerServer();
        server.execute(() -> {
            try {
                verifySettings(server.overworld());
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    throw new IllegalStateException("Render test player disconnected");
                }
                player.teleportTo(server.overworld(), stage.x(), stage.y(), stage.z(), Set.of(), 0, stage.pitch(), true);
                settingsVerified = true;
            } catch (Throwable failure) {
                client.execute(() -> fail(client, failure));
            }
        });
    }

    private static SkyWindowSettingsScreen verifyControlsAndOpen(Minecraft client, BlockPos anchor) {
        KeyMapping modifier = SkyWindowControls.modifier();
        InputConstants.Key original = KeyMappingHelper.getBoundKeyOf(modifier);
        InputConstants.Key leftShift = InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_LSHIFT);
        InputConstants.Key rebound = InputConstants.Type.KEYSYM.getOrCreate(InputConstants.KEY_R);
        InputConstants.Key use = KeyMappingHelper.getBoundKeyOf(client.options.keyUse);
        try {
            modifier.setKey(leftShift);
            KeyMapping.resetMapping();
            KeyMapping.set(leftShift, true);
            if (!modifier.isDown() || !client.options.keyShift.isDown()) {
                throw new IllegalStateException("Default settings modifier consumed the shared Sneak key binding");
            }
            KeyMapping.set(leftShift, false);
            modifier.setKey(rebound);
            KeyMapping.resetMapping();
            KeyMapping.set(leftShift, true);
            if (modifier.isDown() || !client.options.keyShift.isDown()) {
                throw new IllegalStateException("Rebinding settings modifier changed the Sneak key binding");
            }
            KeyMapping.set(leftShift, false);
            KeyMapping.set(use, true);
            if (!client.options.keyUse.isDown()) {
                throw new IllegalStateException("Settings modifier consumed the Use Item key binding");
            }
            KeyMapping.set(use, false);
            KeyMapping.set(rebound, true);
            client.hitResult = new BlockHitResult(Vec3.atBottomCenterOf(anchor), Direction.DOWN, anchor, false);
            if (!modifier.isDown() || !SkyWindowControls.tryOpenSettings()
                    || !(client.gui.screen() instanceof SkyWindowSettingsScreen screen)) {
                throw new IllegalStateException("Rebound settings modifier did not open the ceiling panel");
            }
            LOGGER.info("SKY_WINDOW_CONTROLS_VERIFIED default_shift=shared_with_sneak rebound=R use=preserved");
            return screen;
        } finally {
            KeyMapping.set(leftShift, false);
            KeyMapping.set(rebound, false);
            KeyMapping.set(use, false);
            modifier.setDown(false);
            client.options.keyShift.setDown(false);
            modifier.setKey(original);
            KeyMapping.resetMapping();
        }
    }

    private static void configureScreen(SkyWindowSettingsScreen screen, SkyWindowSettings settings) {
        CycleButton<?> mode = screen.children().stream()
                .filter(child -> child instanceof CycleButton<?> button && button.getValue() instanceof Boolean)
                .map(child -> (CycleButton<?>) child)
                .findFirst().orElseThrow(() -> new IllegalStateException("Settings panel has no Follow world control"));
        if (!mode.getValue().equals(settings.followWorld())) {
            click(mode);
        }
        if (settings.followWorld()) {
            return;
        }
        AbstractSliderButton slider = screen.children().stream()
                .filter(child -> child instanceof AbstractSliderButton)
                .map(child -> (AbstractSliderButton) child)
                .findFirst().orElseThrow(() -> new IllegalStateException("Settings panel has no time slider"));
        double sliderX = slider.getX() + 4 + (settings.time() + 0.1) / 23999.0 * (slider.getWidth() - 8);
        MouseButtonEvent sliderEvent = new MouseButtonEvent(sliderX, slider.getY() + slider.getHeight() / 2.0,
                new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        slider.mouseClicked(sliderEvent, false);
        slider.mouseReleased(sliderEvent);
        CycleButton<?> weather = screen.children().stream()
                .filter(child -> child instanceof CycleButton<?> button && button.getValue() instanceof Weather)
                .map(child -> (CycleButton<?>) child)
                .findFirst().orElseThrow(() -> new IllegalStateException("Settings panel has no weather control"));
        for (int attempts = 0; attempts < Weather.values().length && !weather.getValue().equals(settings.weather()); attempts++) {
            click(weather);
        }
    }

    private static void click(AbstractWidget widget) {
        double x = widget.getX() + widget.getWidth() / 2.0;
        double y = widget.getY() + widget.getHeight() / 2.0;
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        if (!widget.mouseClicked(event, false)) {
            throw new IllegalStateException("Could not click " + widget.getMessage().getString());
        }
        widget.mouseReleased(event);
    }

    private void verifySettings(ServerLevel world) {
        if (world.getOverworldClockTime() != 6000 || world.isRaining() || world.isThundering()) {
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

    private static void assertSettings(ServerLevel world, BlockPos pos, SkyWindowSettings expected) {
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

    private void capture(Minecraft client, Stage stage) throws IOException {
        if (MANUAL_MOVEMENT) {
            BlockPos sample = new BlockPos((int) Math.floor(client.player.getX()), 12, 0);
            if (!requireWindow(client.level.getBlockEntity(sample), sample).getSettings().equals(MOVEMENT_SETTINGS)) {
                throw new IllegalStateException("Manual movement ceiling settings did not synchronize at " + sample);
            }
            if (client.level.getOverworldClockTime() != 6000 || client.level.getRainLevel(1) != 0 || client.level.getThunderLevel(1) != 0) {
                throw new IllegalStateException("Manual movement fixture changed real time or weather");
            }
        }
        if (MOVEMENT && stageIndex > 0 && client.player.getX() < 97) {
            throw new IllegalStateException("Walking did not cross the ceiling chunk boundary: x=" + client.player.getX());
        }
        if (stage.settings() != null && (client.level.getOverworldClockTime() != 6000
                || client.level.getRainLevel(1) != 0 || client.level.getThunderLevel(1) != 0)) {
            throw new IllegalStateException("Custom ceiling leaked time or weather into the client world");
        }
        int skyLight = client.level.getBrightness(LightLayer.SKY, LIGHT_SAMPLE);
        int blockLight = client.level.getBrightness(LightLayer.BLOCK, LIGHT_SAMPLE);
        if (skyLight != 15 || blockLight != 0) {
            throw new IllegalStateException("Unexpected ceiling lighting during " + stage.name()
                    + ": expected sky/block light 15/0, got " + skyLight + "/" + blockLight);
        }
        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), screenshot -> {
            try (screenshot) {
                screenshot.writeToFile(output.resolve(stage.name() + ".png"));
                if (MOVEMENT) {
                    int skyPixels = 0;
                    int samples = 0;
                    for (int x = screenshot.getWidth() * 2 / 5; x < screenshot.getWidth() * 3 / 5; x += 4) {
                        for (int y = screenshot.getHeight() / 5; y < screenshot.getHeight() * 2 / 5; y += 4) {
                            int color = screenshot.getPixel(x, y);
                            int red = color >> 16 & 255;
                            int green = color >> 8 & 255;
                            int blue = color & 255;
                            if (blue > 190 && green > 130 && blue > red + 20 && blue > green + 10
                                    || red > 235 && green > 235 && blue > 235) {
                                skyPixels++;
                            }
                            samples++;
                        }
                    }
                    double fraction = (double) skyPixels / samples;
                    Files.writeString(output.resolve("pixels.txt"), stage.name() + " sky_fraction=" + fraction + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    LOGGER.info("SKY_WINDOW_MOVEMENT_PIXELS {} sky_fraction={}", stage.name(), fraction);
                    if (Boolean.getBoolean("sky_window.smoke.assertMovement") && fraction < 0.995) {
                        client.execute(() -> fail(client, new IllegalStateException("Fallback ceiling pixels during "
                                + stage.name() + ": clear sky fraction=" + fraction)));
                        return;
                    }
                }
                captureFinished = true;
            } catch (IOException failure) {
                client.execute(() -> fail(client, failure));
            }
        });
        String measurement = stage.name() + " time=" + client.level.getOverworldClockTime()
                + " rain=" + client.level.getRainLevel(1)
                + " thunder=" + client.level.getThunderLevel(1)
                + " sky_light=" + skyLight
                + " block_light=" + blockLight
                + (MOVEMENT ? " player_x=" + client.player.getX() + " bob=" + client.options.bobView().get() + " manual=" + MANUAL_MOVEMENT : "")
                + (stage.settings() == null ? "" : " ceiling=" + requireWindow(client.level.getBlockEntity(stage.anchor()), stage.anchor()).getSettings())
                + "\n";
        Files.writeString(output.resolve("measurements.txt"), measurement, StandardOpenOption.APPEND);
        LOGGER.info("SKY_WINDOW_RENDER_CAPTURE {}", stage.name());
    }

    private void fail(Minecraft client, Throwable failure) {
        complete = true;
        client.options.keyUp.setDown(false);
        LOGGER.error("SKY_WINDOW_RENDER_SMOKE_FAILED", failure);
        try {
            Files.createDirectories(output);
            Files.writeString(output.resolve("FAILED.txt"), failure.toString() + "\n");
        } catch (IOException secondary) {
            LOGGER.error("Could not write smoke test failure", secondary);
        }
        client.stop();
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
