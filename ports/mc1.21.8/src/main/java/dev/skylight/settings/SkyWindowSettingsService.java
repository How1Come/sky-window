package dev.skylight.settings;

import dev.skylight.SkylightMod;
import dev.skylight.block.SkyWindowBlockEntity;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class SkyWindowSettingsService {
    public static final int MAX_CONNECTED_BLOCKS = 16384;

    private SkyWindowSettingsService() {
    }

    public static Result playerApply(ServerPlayerEntity player, BlockPos origin, SkyWindowSettings settings) {
        if (!player.isAlive() || player.isSpectator() || !player.canModifyBlocks()) {
            return Result.failure("sky_window.settings.error.permission");
        }
        if (!player.canInteractWithBlockAt(origin, 1.0)) {
            return Result.failure("sky_window.settings.error.reach");
        }
        ServerWorld world = player.getWorld();
        return apply(world, origin, settings, pos -> player.canModifyAt(world, pos)
                && world.getWorldBorder().contains(pos));
    }

    public static Result apply(ServerWorld world, BlockPos origin, SkyWindowSettings settings,
                               Predicate<BlockPos> canModify) {
        if (!world.isInBuildLimit(origin) || !world.isChunkLoaded(origin)) {
            return Result.failure("sky_window.settings.error.unloaded");
        }
        if (!world.getBlockState(origin).isOf(SkylightMod.SKY_WINDOW)) {
            return Result.failure("sky_window.settings.error.missing");
        }
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        List<SkyWindowBlockEntity> component = new ArrayList<>();
        pending.add(origin.toImmutable());
        visited.add(origin.asLong());
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            if (!world.isInBuildLimit(pos)) {
                continue;
            }
            if (!world.isChunkLoaded(pos)) {
                return Result.failure("sky_window.settings.error.unloaded");
            }
            if (!world.getBlockState(pos).isOf(SkylightMod.SKY_WINDOW)) {
                continue;
            }
            if (!canModify.test(pos)) {
                return Result.failure("sky_window.settings.error.permission");
            }
            if (!(world.getBlockEntity(pos) instanceof SkyWindowBlockEntity entity)) {
                return Result.failure("sky_window.settings.error.missing");
            }
            component.add(entity);
            if (component.size() > MAX_CONNECTED_BLOCKS) {
                return Result.failure("sky_window.settings.error.too_large");
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.offset(direction);
                if (visited.add(next.asLong())) {
                    pending.add(next);
                }
            }
        }
        for (SkyWindowBlockEntity entity : component) {
            entity.setSettings(settings);
        }
        return new Result(true, component.size(), "sky_window.settings.applied");
    }

    public record Result(boolean success, int count, String messageKey) {
        public static Result failure(String messageKey) {
            return new Result(false, 0, messageKey);
        }
    }
}
