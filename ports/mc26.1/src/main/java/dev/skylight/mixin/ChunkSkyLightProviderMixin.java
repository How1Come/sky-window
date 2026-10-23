package dev.skylight.mixin;

import dev.skylight.SkylightMod;
import dev.skylight.lighting.SkylightLightHooks;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyLightEngine.class)
public abstract class ChunkSkyLightProviderMixin extends LightEngine
        implements SkylightLightHooks {
    @Unique
    private final Long2ObjectMap<byte[]> skylight$sources = new Long2ObjectOpenHashMap<>();
    @Unique
    private final LongSet skylight$dirtyColumns = new LongOpenHashSet();
    @Unique
    private final LongSet skylight$loadingColumns = new LongOpenHashSet();

    protected ChunkSkyLightProviderMixin(LightChunkGetter chunkProvider, SkyLightSectionStorage lightStorage) {
        super(chunkProvider, lightStorage);
    }

    @Shadow
    protected abstract void updateSourcesInColumn(int x, int z, int surfaceY);

    @Inject(method = "checkNode", at = @At("TAIL"))
    private void skylight$markColumn(long blockPos, CallbackInfo ci) {
        long column = BlockPos.asLong(BlockPos.getX(blockPos), 0, BlockPos.getZ(blockPos));
        if (skylight$sources.containsKey(column) || getState(BlockPos.of(blockPos)).is(SkylightMod.SKY_WINDOW)) {
            skylight$dirtyColumns.add(column);
        }
    }

    @Inject(method = "propagateDecrease", at = @At("HEAD"))
    private void skylight$restoreClearedSource(long blockPos, long flags, CallbackInfo ci) {
        long column = BlockPos.asLong(BlockPos.getX(blockPos), 0, BlockPos.getZ(blockPos));
        byte[] sources = skylight$sources.get(column);
        if (sources == null) {
            return;
        }
        LightChunk chunk = getChunk(SectionPos.blockToSectionCoord(BlockPos.getX(blockPos)),
                SectionPos.blockToSectionCoord(BlockPos.getZ(blockPos)));
        if (chunk == null) {
            return;
        }
        int index = BlockPos.getY(blockPos) - chunk.getMinY();
        if (index >= 0 && index < chunk.getHeight() && sources[index] > 0) {
            LightStorageAccessor skyStorage = (LightStorageAccessor) storage;
            if (skyStorage.skylight$hasSection(SectionPos.blockToSection(blockPos))
                    && skyStorage.skylight$get(blockPos) < sources[index]) {
                BlockState state = getState(BlockPos.of(blockPos));
                enqueueIncrease(blockPos, QueueEntry.increaseLightFromEmission(sources[index], isEmptyShape(state)));
            }
        }
    }

    @Inject(method = "setLightEnabled", at = @At("TAIL"))
    private void skylight$loadColumn(ChunkPos chunkPos, boolean enabled, CallbackInfo ci) {
        if (enabled) {
            skylight$discoverWindows(chunkPos);
        } else {
            for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
                for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                    long column = BlockPos.asLong(x, 0, z);
                    skylight$sources.remove(column);
                    skylight$dirtyColumns.remove(column);
                    skylight$loadingColumns.remove(column);
                }
            }
        }
    }

    @Inject(method = "propagateLightSources", at = @At("TAIL"))
    private void skylight$initializeSources(ChunkPos chunkPos, CallbackInfo ci) {
        skylight$discoverWindows(chunkPos);
    }

    @Unique
    private void skylight$discoverWindows(ChunkPos chunkPos) {
        LightChunk view = getChunk(chunkPos.x(), chunkPos.z());
        if (!(view instanceof ChunkAccess chunk)) {
            return;
        }
        LongSet columns = new LongOpenHashSet();
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.is(SkylightMod.SKY_WINDOW))) {
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    long column = BlockPos.asLong(chunkPos.getMinBlockX() + x, 0, chunkPos.getMinBlockZ() + z);
                    if (columns.contains(column)) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        if (section.getBlockState(x, y, z).is(SkylightMod.SKY_WINDOW)) {
                            columns.add(column);
                            int worldY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex)) + y;
                            checkBlock(new BlockPos(chunkPos.getMinBlockX() + x, worldY, chunkPos.getMinBlockZ() + z));
                            break;
                        }
                    }
                }
            }
        }
        for (long column : columns) {
            if (!skylight$sources.containsKey(column)) {
                byte[] sources = skylight$scanColumn(view, BlockPos.getX(column), BlockPos.getZ(column));
                sources[view.getHeight()] = 0;
                skylight$sources.put(column, sources);
            }
            skylight$dirtyColumns.add(column);
            skylight$loadingColumns.add(column);
        }
    }

    @Override
    public void skylight$flushColumns() {
        LightStorageAccessor skyStorage = (LightStorageAccessor) storage;
        for (long column : skylight$dirtyColumns) {
            int x = BlockPos.getX(column);
            int z = BlockPos.getZ(column);
            LightChunk chunk = getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
            if (chunk == null) {
                continue;
            }
            int bottomY = chunk.getMinY();
            byte[] previous = skylight$sources.get(column);
            byte[] current = skylight$scanColumn(chunk, x, z);
            boolean containsWindow = current[chunk.getHeight()] != 0;
            current[chunk.getHeight()] = 0;
            int surfaceY = chunk.getSkyLightSources().getLowestSourceY(SectionPos.sectionRelative(x), SectionPos.sectionRelative(z));
            if (previous != null) {
                boolean removed = false;
                for (int index = 0; index < chunk.getHeight(); index++) {
                    if (previous[index] <= current[index]) {
                        continue;
                    }
                    removed = true;
                    int y = bottomY + index;
                    long pos = BlockPos.asLong(x, y, z);
                    if (skyStorage.skylight$hasSection(SectionPos.blockToSection(pos))) {
                        int level = Math.max(previous[index], skyStorage.skylight$get(pos));
                        skyStorage.skylight$set(pos, 0);
                        enqueueDecrease(pos, QueueEntry.decreaseAllDirections(level));
                    }
                }
                if (removed) {
                    updateSourcesInColumn(x, z, surfaceY);
                }
            }
            boolean loading = skylight$loadingColumns.contains(column);
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            for (int index = 0; index < chunk.getHeight(); index++) {
                int level = current[index];
                if (level == 0) {
                    continue;
                }
                long pos = BlockPos.asLong(x, bottomY + index, z);
                if (skyStorage.skylight$hasSection(SectionPos.blockToSection(pos))
                        && (loading || skyStorage.skylight$get(pos) < level)) {
                    skyStorage.skylight$set(pos, Math.max(level, skyStorage.skylight$get(pos)));
                    BlockState state = chunk.getBlockState(mutable.set(pos));
                    enqueueIncrease(pos, QueueEntry.increaseLightFromEmission(level, isEmptyShape(state)));
                }
            }
            if (containsWindow) {
                skylight$sources.put(column, current);
            } else {
                skylight$sources.remove(column);
            }
        }
        skylight$dirtyColumns.clear();
        skylight$loadingColumns.clear();
    }

    @Unique
    private byte[] skylight$scanColumn(LightChunk chunk, int x, int z) {
        byte[] sources = new byte[chunk.getHeight() + 1];
        BlockPos.MutableBlockPos lowerPos = new BlockPos.MutableBlockPos(x, chunk.getMaxY(), z);
        BlockPos.MutableBlockPos upperPos = new BlockPos.MutableBlockPos(x, chunk.getMaxY() + 1, z);
        BlockState upperState = Blocks.AIR.defaultBlockState();
        boolean exposed = false;
        for (int y = chunk.getMaxY(); y >= chunk.getMinY(); y--) {
            lowerPos.setY(y);
            upperPos.setY(y + 1);
            BlockState state = chunk.getBlockState(lowerPos);
            if (state.is(SkylightMod.SKY_WINDOW)) {
                exposed = true;
                sources[chunk.getHeight()] = 1;
                upperState = Blocks.AIR.defaultBlockState();
                continue;
            }
            if (exposed) {
                int opacity = state.getLightDampening();
                boolean blocked = shapeOccludes(upperState, state, Direction.DOWN);
                if (!blocked && opacity < 15) {
                    sources[y - chunk.getMinY()] = (byte) (15 - opacity);
                }
                exposed = !blocked && opacity == 0;
            }
            upperState = state;
        }
        return sources;
    }
}
