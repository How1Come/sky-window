package dev.skylight.settings;

import dev.skylight.SkylightMod;
import dev.skylight.block.SkyWindowBlockEntity;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public final class SkyWindowSettingsService {
    public static final int MAX_CONNECTED_BLOCKS = 16384;

    private SkyWindowSettingsService() {
    }

    public static Result playerApply(ServerPlayer player, BlockPos origin, SkyWindowSettings settings) {
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild()) {
            return Result.failure("sky_window.settings.error.permission");
        }
        if (!player.isWithinBlockInteractionRange(origin, 1.0)) {
            return Result.failure("sky_window.settings.error.reach");
        }
        ServerLevel world = player.level();
        return apply(world, origin, settings, pos -> player.mayInteract(world, pos)
                && world.getWorldBorder().isWithinBounds(pos));
    }

    public static Result apply(ServerLevel world, BlockPos origin, SkyWindowSettings settings,
                               Predicate<BlockPos> canModify) {
        if (!world.isInsideBuildHeight(origin) || !world.hasChunkAt(origin)) {
            return Result.failure("sky_window.settings.error.unloaded");
        }
        if (!world.getBlockState(origin).is(SkylightMod.SKY_WINDOW)) {
            return Result.failure("sky_window.settings.error.missing");
        }
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        List<SkyWindowBlockEntity> component = new ArrayList<>();
        pending.add(origin.immutable());
        visited.add(origin.asLong());
        while (!pending.isEmpty()) {
            BlockPos pos = pending.removeFirst();
            if (!world.isInsideBuildHeight(pos)) {
                continue;
            }
            if (!world.hasChunkAt(pos)) {
                return Result.failure("sky_window.settings.error.unloaded");
            }
            if (!world.getBlockState(pos).is(SkylightMod.SKY_WINDOW)) {
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
                BlockPos next = pos.relative(direction);
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
