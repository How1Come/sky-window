package dev.skylight.mixin;

import dev.skylight.SkylightMod;
import dev.skylight.lighting.SkylightLightHooks;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkProvider;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.ChunkSkyLightProvider;
import net.minecraft.world.chunk.light.LightSourceView;
import net.minecraft.world.chunk.light.SkyLightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkSkyLightProvider.class)
public abstract class ChunkSkyLightProviderMixin extends ChunkLightProvider
        implements SkylightLightHooks {
    @Unique
    private final Long2ObjectMap<byte[]> skylight$sources = new Long2ObjectOpenHashMap<>();
    @Unique
    private final LongSet skylight$dirtyColumns = new LongOpenHashSet();
    @Unique
    private final LongSet skylight$loadingColumns = new LongOpenHashSet();

    protected ChunkSkyLightProviderMixin(ChunkProvider chunkProvider, SkyLightStorage lightStorage) {
        super(chunkProvider, lightStorage);
    }

    @Shadow
    protected abstract void method_51590(int x, int z, int surfaceY);

    @Inject(method = "method_51529", at = @At("TAIL"))
    private void skylight$markColumn(long blockPos, CallbackInfo ci) {
        long column = BlockPos.asLong(BlockPos.unpackLongX(blockPos), 0, BlockPos.unpackLongZ(blockPos));
        if (skylight$sources.containsKey(column) || getStateForLighting(BlockPos.fromLong(blockPos)).isOf(SkylightMod.SKY_WINDOW)) {
            skylight$dirtyColumns.add(column);
        }
    }

    @Inject(method = "method_51530", at = @At("HEAD"))
    private void skylight$restoreClearedSource(long blockPos, long flags, CallbackInfo ci) {
        long column = BlockPos.asLong(BlockPos.unpackLongX(blockPos), 0, BlockPos.unpackLongZ(blockPos));
        byte[] sources = skylight$sources.get(column);
        if (sources == null) {
            return;
        }
        LightSourceView chunk = getChunk(ChunkSectionPos.getSectionCoord(BlockPos.unpackLongX(blockPos)),
                ChunkSectionPos.getSectionCoord(BlockPos.unpackLongZ(blockPos)));
        if (chunk == null) {
            return;
        }
        int index = BlockPos.unpackLongY(blockPos) - chunk.getBottomY();
        if (index >= 0 && index < chunk.getHeight() && sources[index] > 0) {
            LightStorageAccessor storage = (LightStorageAccessor) lightStorage;
            if (storage.skylight$hasSection(ChunkSectionPos.fromBlockPos(blockPos))
                    && storage.skylight$get(blockPos) < sources[index]) {
                BlockState state = getStateForLighting(BlockPos.fromLong(blockPos));
                method_51566(blockPos, class_8531.method_51573(sources[index], isTrivialForLighting(state)));
            }
        }
    }

    @Inject(method = "setColumnEnabled", at = @At("TAIL"))
    private void skylight$loadColumn(ChunkPos chunkPos, boolean enabled, CallbackInfo ci) {
        if (enabled) {
            skylight$discoverWindows(chunkPos);
        } else {
            for (int x = chunkPos.getStartX(); x <= chunkPos.getEndX(); x++) {
                for (int z = chunkPos.getStartZ(); z <= chunkPos.getEndZ(); z++) {
                    long column = BlockPos.asLong(x, 0, z);
                    skylight$sources.remove(column);
                    skylight$dirtyColumns.remove(column);
                    skylight$loadingColumns.remove(column);
                }
            }
        }
    }

    @Inject(method = "propagateLight", at = @At("TAIL"))
    private void skylight$initializeSources(ChunkPos chunkPos, CallbackInfo ci) {
        skylight$discoverWindows(chunkPos);
    }

    @Unique
    private void skylight$discoverWindows(ChunkPos chunkPos) {
        LightSourceView view = getChunk(chunkPos.x, chunkPos.z);
        if (!(view instanceof Chunk chunk)) {
            return;
        }
        LongSet columns = new LongOpenHashSet();
        ChunkSection[] sections = chunk.getSectionArray();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (!section.hasAny(state -> state.isOf(SkylightMod.SKY_WINDOW))) {
                continue;
            }
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    long column = BlockPos.asLong(chunkPos.getStartX() + x, 0, chunkPos.getStartZ() + z);
                    if (columns.contains(column)) {
                        continue;
                    }
                    for (int y = 0; y < 16; y++) {
                        if (section.getBlockState(x, y, z).isOf(SkylightMod.SKY_WINDOW)) {
                            columns.add(column);
                            int worldY = ChunkSectionPos.getBlockCoord(chunk.sectionIndexToCoord(sectionIndex)) + y;
                            checkBlock(new BlockPos(chunkPos.getStartX() + x, worldY, chunkPos.getStartZ() + z));
                            break;
                        }
                    }
                }
            }
        }
        for (long column : columns) {
            if (!skylight$sources.containsKey(column)) {
                byte[] sources = skylight$scanColumn(view, BlockPos.unpackLongX(column), BlockPos.unpackLongZ(column));
                sources[view.getHeight()] = 0;
                skylight$sources.put(column, sources);
            }
            skylight$dirtyColumns.add(column);
            skylight$loadingColumns.add(column);
        }
    }

    @Override
    public void skylight$flushColumns() {
        LightStorageAccessor storage = (LightStorageAccessor) lightStorage;
        for (long column : skylight$dirtyColumns) {
            int x = BlockPos.unpackLongX(column);
            int z = BlockPos.unpackLongZ(column);
            LightSourceView chunk = getChunk(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z));
            if (chunk == null) {
                continue;
            }
            int bottomY = chunk.getBottomY();
            byte[] previous = skylight$sources.get(column);
            byte[] current = skylight$scanColumn(chunk, x, z);
            boolean containsWindow = current[chunk.getHeight()] != 0;
            current[chunk.getHeight()] = 0;
            int surfaceY = chunk.getChunkSkyLight().get(ChunkSectionPos.getLocalCoord(x), ChunkSectionPos.getLocalCoord(z));
            if (previous != null) {
                boolean removed = false;
                for (int index = 0; index < chunk.getHeight(); index++) {
                    if (previous[index] <= current[index]) {
                        continue;
                    }
                    removed = true;
                    int y = bottomY + index;
                    long pos = BlockPos.asLong(x, y, z);
                    if (storage.skylight$hasSection(ChunkSectionPos.fromBlockPos(pos))) {
                        int level = Math.max(previous[index], storage.skylight$get(pos));
                        storage.skylight$set(pos, 0);
                        method_51565(pos, class_8531.packWithAllDirectionsSet(level));
                    }
                }
                if (removed) {
                    method_51590(x, z, surfaceY);
                }
            }
            boolean loading = skylight$loadingColumns.contains(column);
            BlockPos.Mutable mutable = new BlockPos.Mutable();
            for (int index = 0; index < chunk.getHeight(); index++) {
                int level = current[index];
                if (level == 0) {
                    continue;
                }
                long pos = BlockPos.asLong(x, bottomY + index, z);
                if (storage.skylight$hasSection(ChunkSectionPos.fromBlockPos(pos))
                        && (loading || storage.skylight$get(pos) < level)) {
                    storage.skylight$set(pos, Math.max(level, storage.skylight$get(pos)));
                    BlockState state = chunk.getBlockState(mutable.set(pos));
                    method_51566(pos, class_8531.method_51573(level, isTrivialForLighting(state)));
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
    private byte[] skylight$scanColumn(LightSourceView chunk, int x, int z) {
        byte[] sources = new byte[chunk.getHeight() + 1];
        BlockPos.Mutable lowerPos = new BlockPos.Mutable(x, chunk.getTopYInclusive() + 1, z);
        BlockPos.Mutable upperPos = new BlockPos.Mutable(x, chunk.getTopYInclusive() + 2, z);
        BlockState upperState = Blocks.AIR.getDefaultState();
        boolean exposed = false;
        for (int y = chunk.getTopYInclusive(); y >= chunk.getBottomY(); y--) {
            lowerPos.setY(y);
            upperPos.setY(y + 1);
            BlockState state = chunk.getBlockState(lowerPos);
            if (state.isOf(SkylightMod.SKY_WINDOW)) {
                exposed = true;
                sources[chunk.getHeight()] = 1;
                upperState = Blocks.AIR.getDefaultState();
                continue;
            }
            if (exposed) {
                int opacity = state.getOpacity();
                boolean blocked = VoxelShapes.unionCoversFullCube(getOpaqueShape(upperState, Direction.DOWN),
                        getOpaqueShape(state, Direction.UP));
                if (!blocked && opacity < 15) {
                    sources[y - chunk.getBottomY()] = (byte) (15 - opacity);
                }
                exposed = !blocked && opacity == 0;
            }
            upperState = state;
        }
        return sources;
    }
}
