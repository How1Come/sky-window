package dev.skylight.test;

import com.mojang.authlib.GameProfile;
import dev.skylight.SkylightMod;
import dev.skylight.block.SkyWindowBlockEntity;
import dev.skylight.network.ApplySkySettingsPayload;
import dev.skylight.network.SkySettingsResultPayload;
import dev.skylight.settings.SkyWindowSettings;
import dev.skylight.settings.SkyWindowSettingsService;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class SkySettingsGameTests {
    private static final String ROOM = "sky_window_gametest:room";
    private static final BlockPos ORIGIN = new BlockPos(11, 8, 11);
    private static final SkyWindowSettings NIGHT_STORM = new SkyWindowSettings(false, 18000,
            SkyWindowSettings.Weather.THUNDER);

    @GameTest(structure = ROOM)
    public void settingsApplyToAllSixFacesWithoutChangingReality(GameTestHelper context) {
        context.setBlock(ORIGIN, SkylightMod.SKY_WINDOW);
        for (Direction direction : Direction.values()) {
            context.setBlock(ORIGIN.relative(direction), SkylightMod.SKY_WINDOW);
        }
        BlockPos separate = ORIGIN.east(2).south(2);
        context.setBlock(separate, SkylightMod.SKY_WINDOW);
        long worldTime = context.getLevel().getOverworldClockTime();
        float rain = context.getLevel().getRainLevel(1.0f);
        float thunder = context.getLevel().getThunderLevel(1.0f);
        var result = SkyWindowSettingsService.apply(context.getLevel(), context.absolutePos(ORIGIN),
                NIGHT_STORM, pos -> true);
        context.assertTrue(result.success() && result.count() == 7, "All seven face-connected blocks must update");
        expectSettings(context, ORIGIN, NIGHT_STORM);
        for (Direction direction : Direction.values()) {
            expectSettings(context, ORIGIN.relative(direction), NIGHT_STORM);
        }
        expectSettings(context, separate, SkyWindowSettings.LIVE);
        context.assertTrue(context.getLevel().getOverworldClockTime() == worldTime, "Real world time must stay unchanged");
        context.assertTrue(context.getLevel().getRainLevel(1.0f) == rain, "Real rain must stay unchanged");
        context.assertTrue(context.getLevel().getThunderLevel(1.0f) == thunder, "Real thunder must stay unchanged");
        context.succeed();
    }

    @GameTest(structure = ROOM)
    public void edgeAndCornerTouchingWindowsStayIndependent(GameTestHelper context) {
        BlockPos edge = ORIGIN.east().south();
        BlockPos corner = ORIGIN.west().north().above();
        context.setBlock(ORIGIN, SkylightMod.SKY_WINDOW);
        context.setBlock(edge, SkylightMod.SKY_WINDOW);
        context.setBlock(corner, SkylightMod.SKY_WINDOW);
        var result = SkyWindowSettingsService.apply(context.getLevel(), context.absolutePos(ORIGIN),
                NIGHT_STORM, pos -> true);
        context.assertTrue(result.success() && result.count() == 1, "Only shared faces connect ceilings");
        expectSettings(context, ORIGIN, NIGHT_STORM);
        expectSettings(context, edge, SkyWindowSettings.LIVE);
        expectSettings(context, corner, SkyWindowSettings.LIVE);
        context.succeed();
    }

    @GameTest(structure = ROOM)
    public void protectedMemberRejectsWholeConnectedCeiling(GameTestHelper context) {
        for (int x = 0; x < 5; x++) {
            context.setBlock(ORIGIN.east(x), SkylightMod.SKY_WINDOW);
        }
        BlockPos protectedPos = context.absolutePos(ORIGIN.east(4));
        var result = SkyWindowSettingsService.apply(context.getLevel(), context.absolutePos(ORIGIN),
                NIGHT_STORM, pos -> !pos.equals(protectedPos));
        context.assertTrue(!result.success() && result.count() == 0, "Permission denial must reject every change");
        for (int x = 0; x < 5; x++) {
            expectSettings(context, ORIGIN.east(x), SkyWindowSettings.LIVE);
        }
        context.succeed();
    }

    @GameTest(structure = ROOM)
    public void settingsCrossChunkBoundariesAndCanReturnToLive(GameTestHelper context) {
        int boundaryX = 3;
        while ((context.absolutePos(new BlockPos(boundaryX, 8, 11)).getX() & 15) != 15) {
            boundaryX++;
        }
        BlockPos left = new BlockPos(boundaryX, 8, 11);
        BlockPos right = left.east();
        context.setBlock(left, SkylightMod.SKY_WINDOW);
        context.setBlock(right, SkylightMod.SKY_WINDOW);
        var result = SkyWindowSettingsService.apply(context.getLevel(), context.absolutePos(left),
                NIGHT_STORM, pos -> true);
        context.assertTrue(result.success() && result.count() == 2, "Shared faces must connect across loaded chunks");
        expectSettings(context, left, NIGHT_STORM);
        expectSettings(context, right, NIGHT_STORM);
        result = SkyWindowSettingsService.apply(context.getLevel(), context.absolutePos(right),
                SkyWindowSettings.LIVE, pos -> true);
        context.assertTrue(result.success() && result.count() == 2, "Live mode must apply to the whole ceiling");
        expectSettings(context, left, SkyWindowSettings.LIVE);
        expectSettings(context, right, SkyWindowSettings.LIVE);
        context.succeed();
    }

    @GameTest(structure = ROOM)
    public void settingsPersistAndSynchronizeThroughBlockEntityNbt(GameTestHelper context) {
        context.setBlock(ORIGIN, SkylightMod.SKY_WINDOW);
        SkyWindowBlockEntity entity = context.getBlockEntity(ORIGIN, SkyWindowBlockEntity.class);
        var registries = context.getLevel().registryAccess();
        for (SkyWindowSettings.Weather weather : SkyWindowSettings.Weather.values()) {
            SkyWindowSettings settings = new SkyWindowSettings(false, 23999, weather);
            entity.setSettings(settings);
            SkyWindowBlockEntity restored = new SkyWindowBlockEntity(entity.getBlockPos(), entity.getBlockState());
            restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, entity.saveWithoutMetadata(registries)));
            context.assertTrue(restored.getSettings().equals(settings), "Disk NBT must retain every sky setting");
            SkyWindowBlockEntity initial = new SkyWindowBlockEntity(entity.getBlockPos(), entity.getBlockState());
            initial.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, entity.getUpdateTag(registries)));
            context.assertTrue(initial.getSettings().equals(settings), "Initial chunk data must synchronize settings");
            ClientboundBlockEntityDataPacket packet = entity.getUpdatePacket();
            SkyWindowBlockEntity update = new SkyWindowBlockEntity(entity.getBlockPos(), entity.getBlockState());
            update.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, packet.getTag()));
            context.assertTrue(update.getSettings().equals(settings), "Block entity update packet must synchronize settings");
        }
        context.succeed();
    }

    @GameTest(structure = ROOM)
    public void malformedOrLegacySettingsSafelyUseLiveSky(GameTestHelper context) {
        context.assertTrue(SkyWindowSettings.fromNbt(new CompoundTag()).equals(SkyWindowSettings.LIVE),
                "Older saves without settings must use the live sky");
        CompoundTag invalid = NIGHT_STORM.toNbt();
        invalid.putInt("Time", -1);
        context.assertTrue(SkyWindowSettings.fromNbt(invalid).equals(SkyWindowSettings.LIVE),
                "Negative times must not corrupt rendering");
        invalid.putInt("Time", 24000);
        context.assertTrue(SkyWindowSettings.fromNbt(invalid).equals(SkyWindowSettings.LIVE),
                "Times beyond one day must not corrupt rendering");
        invalid.putInt("Time", 6000);
        invalid.putString("Weather", "unknown-weather");
        context.assertTrue(SkyWindowSettings.fromNbt(invalid).equals(SkyWindowSettings.LIVE),
                "Unknown weather values must not corrupt rendering");
        context.succeed();
    }

    @GameTest(structure = ROOM)
    public void requestAndResponsePacketsRoundTrip(GameTestHelper context) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.getLevel().registryAccess());
        try {
            ApplySkySettingsPayload request = new ApplySkySettingsPayload(context.absolutePos(ORIGIN), NIGHT_STORM);
            ApplySkySettingsPayload.CODEC.encode(buffer, request);
            context.assertTrue(ApplySkySettingsPayload.CODEC.decode(buffer).equals(request),
                    "Apply packet must retain position and settings");
            buffer.clear();
            SkySettingsResultPayload response = new SkySettingsResultPayload(request.pos(), true, 225,
                    "sky_window.settings.applied");
            SkySettingsResultPayload.CODEC.encode(buffer, response);
            context.assertTrue(SkySettingsResultPayload.CODEC.decode(buffer).equals(response),
                    "Result packet must retain success, component size, and message");
        } finally {
            buffer.release();
        }
        context.succeed();
    }

    @GameTest(structure = ROOM)
    public void outOfReachOrReadOnlyPlayerCannotChangeSettings(GameTestHelper context) {
        context.setBlock(ORIGIN, SkylightMod.SKY_WINDOW);
        BlockPos target = context.absolutePos(ORIGIN);
        ServerPlayer player = new ServerPlayer(context.getLevel().getServer(), context.getLevel(),
                new GameProfile(UUID.randomUUID(), "sky-settings-test"), ClientInformation.createDefault());
        player.getAbilities().mayBuild = true;
        player.setPos(target.getX() + 100.0, target.getY(), target.getZ());
        var result = SkyWindowSettingsService.playerApply(player, target, NIGHT_STORM);
        context.assertTrue(!result.success() && result.messageKey().equals("sky_window.settings.error.reach"),
                "A remote apply packet must fail the server reach check");
        player.setPos(target.getX() + 0.5, target.getY() - 1.0, target.getZ() + 0.5);
        player.getAbilities().mayBuild = false;
        result = SkyWindowSettingsService.playerApply(player, target, NIGHT_STORM);
        context.assertTrue(!result.success() && result.messageKey().equals("sky_window.settings.error.permission"),
                "Read-only players must not edit ceiling settings");
        expectSettings(context, ORIGIN, SkyWindowSettings.LIVE);
        context.succeed();
    }

    @GameTest(structure = ROOM)
    public void removedTargetCannotConfigurePreviouslyConnectedWindows(GameTestHelper context) {
        context.setBlock(ORIGIN, SkylightMod.SKY_WINDOW);
        context.setBlock(ORIGIN.east(), SkylightMod.SKY_WINDOW);
        context.setBlock(ORIGIN, Blocks.STONE);
        var result = SkyWindowSettingsService.apply(context.getLevel(), context.absolutePos(ORIGIN),
                NIGHT_STORM, pos -> true);
        context.assertTrue(!result.success() && result.messageKey().equals("sky_window.settings.error.missing"),
                "A removed or replaced panel target must reject a stale apply packet");
        expectSettings(context, ORIGIN.east(), SkyWindowSettings.LIVE);
        context.succeed();
    }

    private static void expectSettings(GameTestHelper context, BlockPos pos, SkyWindowSettings settings) {
        SkyWindowBlockEntity entity = context.getBlockEntity(pos, SkyWindowBlockEntity.class);
        context.assertTrue(entity.getSettings().equals(settings), "Unexpected sky settings at " + pos);
    }
}
