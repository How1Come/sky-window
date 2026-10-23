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
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class SkySettingsGameTests implements FabricGameTest {
    private static final String ROOM = "sky_window_gametest:room";
    private static final BlockPos ORIGIN = new BlockPos(11, 8, 11);
    private static final SkyWindowSettings NIGHT_STORM = new SkyWindowSettings(false, 18000,
            SkyWindowSettings.Weather.THUNDER);

    @GameTest(templateName = ROOM)
    public void settingsApplyToAllSixFacesWithoutChangingReality(TestContext context) {
        context.setBlockState(ORIGIN, SkylightMod.SKY_WINDOW);
        for (Direction direction : Direction.values()) {
            context.setBlockState(ORIGIN.offset(direction), SkylightMod.SKY_WINDOW);
        }
        BlockPos separate = ORIGIN.east(2).south(2);
        context.setBlockState(separate, SkylightMod.SKY_WINDOW);
        long worldTime = context.getWorld().getTimeOfDay();
        float rain = context.getWorld().getRainGradient(1.0f);
        float thunder = context.getWorld().getThunderGradient(1.0f);
        var result = SkyWindowSettingsService.apply(context.getWorld(), context.getAbsolutePos(ORIGIN),
                NIGHT_STORM, pos -> true);
        context.assertTrue(result.success() && result.count() == 7, "All seven face-connected blocks must update");
        expectSettings(context, ORIGIN, NIGHT_STORM);
        for (Direction direction : Direction.values()) {
            expectSettings(context, ORIGIN.offset(direction), NIGHT_STORM);
        }
        expectSettings(context, separate, SkyWindowSettings.LIVE);
        context.assertTrue(context.getWorld().getTimeOfDay() == worldTime, "Real world time must stay unchanged");
        context.assertTrue(context.getWorld().getRainGradient(1.0f) == rain, "Real rain must stay unchanged");
        context.assertTrue(context.getWorld().getThunderGradient(1.0f) == thunder, "Real thunder must stay unchanged");
        context.complete();
    }

    @GameTest(templateName = ROOM)
    public void edgeAndCornerTouchingWindowsStayIndependent(TestContext context) {
        BlockPos edge = ORIGIN.east().south();
        BlockPos corner = ORIGIN.west().north().up();
        context.setBlockState(ORIGIN, SkylightMod.SKY_WINDOW);
        context.setBlockState(edge, SkylightMod.SKY_WINDOW);
        context.setBlockState(corner, SkylightMod.SKY_WINDOW);
        var result = SkyWindowSettingsService.apply(context.getWorld(), context.getAbsolutePos(ORIGIN),
                NIGHT_STORM, pos -> true);
        context.assertTrue(result.success() && result.count() == 1, "Only shared faces connect ceilings");
        expectSettings(context, ORIGIN, NIGHT_STORM);
        expectSettings(context, edge, SkyWindowSettings.LIVE);
        expectSettings(context, corner, SkyWindowSettings.LIVE);
        context.complete();
    }

    @GameTest(templateName = ROOM)
    public void protectedMemberRejectsWholeConnectedCeiling(TestContext context) {
        for (int x = 0; x < 5; x++) {
            context.setBlockState(ORIGIN.east(x), SkylightMod.SKY_WINDOW);
        }
        BlockPos protectedPos = context.getAbsolutePos(ORIGIN.east(4));
        var result = SkyWindowSettingsService.apply(context.getWorld(), context.getAbsolutePos(ORIGIN),
                NIGHT_STORM, pos -> !pos.equals(protectedPos));
        context.assertTrue(!result.success() && result.count() == 0, "Permission denial must reject every change");
        for (int x = 0; x < 5; x++) {
            expectSettings(context, ORIGIN.east(x), SkyWindowSettings.LIVE);
        }
        context.complete();
    }

    @GameTest(templateName = ROOM)
    public void settingsCrossChunkBoundariesAndCanReturnToLive(TestContext context) {
        int boundaryX = 3;
        while ((context.getAbsolutePos(new BlockPos(boundaryX, 8, 11)).getX() & 15) != 15) {
            boundaryX++;
        }
        BlockPos left = new BlockPos(boundaryX, 8, 11);
        BlockPos right = left.east();
        context.setBlockState(left, SkylightMod.SKY_WINDOW);
        context.setBlockState(right, SkylightMod.SKY_WINDOW);
        var result = SkyWindowSettingsService.apply(context.getWorld(), context.getAbsolutePos(left),
                NIGHT_STORM, pos -> true);
        context.assertTrue(result.success() && result.count() == 2, "Shared faces must connect across loaded chunks");
        expectSettings(context, left, NIGHT_STORM);
        expectSettings(context, right, NIGHT_STORM);
        result = SkyWindowSettingsService.apply(context.getWorld(), context.getAbsolutePos(right),
                SkyWindowSettings.LIVE, pos -> true);
        context.assertTrue(result.success() && result.count() == 2, "Live mode must apply to the whole ceiling");
        expectSettings(context, left, SkyWindowSettings.LIVE);
        expectSettings(context, right, SkyWindowSettings.LIVE);
        context.complete();
    }

    @GameTest(templateName = ROOM)
    public void settingsPersistAndSynchronizeThroughBlockEntityNbt(TestContext context) {
        context.setBlockState(ORIGIN, SkylightMod.SKY_WINDOW);
        SkyWindowBlockEntity entity = context.getBlockEntity(ORIGIN);
        var registries = context.getWorld().getRegistryManager();
        for (SkyWindowSettings.Weather weather : SkyWindowSettings.Weather.values()) {
            SkyWindowSettings settings = new SkyWindowSettings(false, 23999, weather);
            entity.setSettings(settings);
            SkyWindowBlockEntity restored = new SkyWindowBlockEntity(entity.getPos(), entity.getCachedState());
            restored.read(entity.createNbt(registries), registries);
            context.assertTrue(restored.getSettings().equals(settings), "Disk NBT must retain every sky setting");
            SkyWindowBlockEntity initial = new SkyWindowBlockEntity(entity.getPos(), entity.getCachedState());
            initial.read(entity.toInitialChunkDataNbt(registries), registries);
            context.assertTrue(initial.getSettings().equals(settings), "Initial chunk data must synchronize settings");
            BlockEntityUpdateS2CPacket packet = entity.toUpdatePacket();
            SkyWindowBlockEntity update = new SkyWindowBlockEntity(entity.getPos(), entity.getCachedState());
            update.read(packet.getNbt(), registries);
            context.assertTrue(update.getSettings().equals(settings), "Block entity update packet must synchronize settings");
        }
        context.complete();
    }

    @GameTest(templateName = ROOM)
    public void malformedOrLegacySettingsSafelyUseLiveSky(TestContext context) {
        context.assertTrue(SkyWindowSettings.fromNbt(new NbtCompound()).equals(SkyWindowSettings.LIVE),
                "Older saves without settings must use the live sky");
        NbtCompound invalid = NIGHT_STORM.toNbt();
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
        context.complete();
    }

    @GameTest(templateName = ROOM)
    public void requestAndResponsePacketsRoundTrip(TestContext context) {
        RegistryByteBuf buffer = new RegistryByteBuf(Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            ApplySkySettingsPayload request = new ApplySkySettingsPayload(context.getAbsolutePos(ORIGIN), NIGHT_STORM);
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
        context.complete();
    }

    @GameTest(templateName = ROOM)
    public void outOfReachOrReadOnlyPlayerCannotChangeSettings(TestContext context) {
        context.setBlockState(ORIGIN, SkylightMod.SKY_WINDOW);
        BlockPos target = context.getAbsolutePos(ORIGIN);
        ServerPlayerEntity player = new ServerPlayerEntity(context.getWorld().getServer(), context.getWorld(),
                new GameProfile(UUID.randomUUID(), "sky-settings-test"), SyncedClientOptions.createDefault());
        player.getAbilities().allowModifyWorld = true;
        player.setPosition(target.getX() + 100.0, target.getY(), target.getZ());
        var result = SkyWindowSettingsService.playerApply(player, target, NIGHT_STORM);
        context.assertTrue(!result.success() && result.messageKey().equals("sky_window.settings.error.reach"),
                "A remote apply packet must fail the server reach check");
        player.setPosition(target.getX() + 0.5, target.getY() - 1.0, target.getZ() + 0.5);
        player.getAbilities().allowModifyWorld = false;
        result = SkyWindowSettingsService.playerApply(player, target, NIGHT_STORM);
        context.assertTrue(!result.success() && result.messageKey().equals("sky_window.settings.error.permission"),
                "Read-only players must not edit ceiling settings");
        expectSettings(context, ORIGIN, SkyWindowSettings.LIVE);
        context.complete();
    }

    @GameTest(templateName = ROOM)
    public void removedTargetCannotConfigurePreviouslyConnectedWindows(TestContext context) {
        context.setBlockState(ORIGIN, SkylightMod.SKY_WINDOW);
        context.setBlockState(ORIGIN.east(), SkylightMod.SKY_WINDOW);
        context.setBlockState(ORIGIN, Blocks.STONE);
        var result = SkyWindowSettingsService.apply(context.getWorld(), context.getAbsolutePos(ORIGIN),
                NIGHT_STORM, pos -> true);
        context.assertTrue(!result.success() && result.messageKey().equals("sky_window.settings.error.missing"),
                "A removed or replaced panel target must reject a stale apply packet");
        expectSettings(context, ORIGIN.east(), SkyWindowSettings.LIVE);
        context.complete();
    }

    private static void expectSettings(TestContext context, BlockPos pos, SkyWindowSettings settings) {
        SkyWindowBlockEntity entity = context.getBlockEntity(pos);
        context.assertTrue(entity.getSettings().equals(settings), "Unexpected sky settings at " + pos);
    }
}
