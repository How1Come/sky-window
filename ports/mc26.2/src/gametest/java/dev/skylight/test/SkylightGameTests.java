package dev.skylight.test;

import dev.skylight.SkylightMod;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;

public final class SkylightGameTests {
    private static final String ROOM = "sky_window_gametest:room";
    private static final String TALL_ROOM = "sky_window_gametest:tall_room";
    private static final BlockPos WINDOW = new BlockPos(11, 9, 11);
    private static final BlockPos FLOOR_SAMPLE = new BlockPos(11, 2, 11);

    @GameTest(structure = ROOM, maxTicks = 4000)
    public void placementProvidesRealSkylightWithLateralFalloff(GameTestHelper context) {
        GameTestSequence sequence = prepareRoom(context);
        sequence.thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .thenWaitUntil(() -> context.setBlock(WINDOW, SkylightMod.SKY_WINDOW))
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 15, 0);
                    expectLight(context, FLOOR_SAMPLE, 15, 0);
                    expectLight(context, FLOOR_SAMPLE.east(), 14, 0);
                    expectLight(context, FLOOR_SAMPLE.east(2), 13, 0);
                    context.assertBlockPresent(Blocks.STONE, WINDOW.above());
                })
                .thenSucceed();
    }

    @GameTest(structure = ROOM, maxTicks = 6000)
    public void removalAndReplacementRecomputeLighting(GameTestHelper context) {
        GameTestSequence sequence = prepareRoom(context);
        sequence.thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .thenWaitUntil(() -> context.setBlock(WINDOW, SkylightMod.SKY_WINDOW))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .thenWaitUntil(() -> context.setBlock(WINDOW, Blocks.STONE))
                .thenWaitUntil(() -> {
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                    expectLight(context, FLOOR_SAMPLE.east(), 0, 0);
                })
                .thenWaitUntil(() -> context.setBlock(WINDOW, SkylightMod.SKY_WINDOW))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .thenSucceed();
    }

    @GameTest(structure = ROOM, maxTicks = 6000)
    public void stoneOccludesSkylightAndBreakingItRestoresSkylight(GameTestHelper context) {
        GameTestSequence sequence = prepareRoom(context);
        sequence.thenWaitUntil(() -> context.setBlock(WINDOW, SkylightMod.SKY_WINDOW))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .thenWaitUntil(() -> fillPlane(context, 5, Blocks.STONE))
                .thenWaitUntil(() -> {
                    expectLight(context, new BlockPos(11, 6, 11), 15, 0);
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                })
                .thenWaitUntil(() -> context.setBlock(11, 5, 11, Blocks.AIR))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .thenSucceed();
    }

    @GameTest(structure = ROOM, maxTicks = 6000)
    public void removingOneWindowPreservesTheOtherSource(GameTestHelper context) {
        BlockPos secondWindow = WINDOW.east(4);
        GameTestSequence sequence = prepareRoom(context);
        sequence.thenWaitUntil(() -> {
                    context.setBlock(WINDOW, SkylightMod.SKY_WINDOW);
                    context.setBlock(secondWindow, SkylightMod.SKY_WINDOW);
                })
                .thenWaitUntil(() -> {
                    expectLight(context, FLOOR_SAMPLE, 15, 0);
                    expectLight(context, FLOOR_SAMPLE.east(2), 13, 0);
                    expectLight(context, FLOOR_SAMPLE.east(4), 15, 0);
                })
                .thenWaitUntil(() -> context.setBlock(WINDOW, Blocks.STONE))
                .thenWaitUntil(() -> {
                    expectLight(context, FLOOR_SAMPLE, 11, 0);
                    expectLight(context, FLOOR_SAMPLE.east(4), 15, 0);
                })
                .thenWaitUntil(() -> context.setBlock(secondWindow, Blocks.STONE))
                .thenWaitUntil(() -> {
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                    expectLight(context, FLOOR_SAMPLE.east(4), 0, 0);
                })
                .thenSucceed();
    }

    @GameTest(structure = ROOM, maxTicks = 6000)
    public void propagationAndRemovalCrossChunkBoundaries(GameTestHelper context) {
        int boundaryX = 3;
        while ((context.absolutePos(new BlockPos(boundaryX, 2, 11)).getX() & 15) != 15) {
            boundaryX++;
        }
        BlockPos source = new BlockPos(boundaryX, 9, 11);
        BlockPos sample = new BlockPos(boundaryX, 2, 11);
        context.assertTrue((context.absolutePos(sample).getX() >> 4)
                        != (context.absolutePos(sample.east()).getX() >> 4),
                "Samples must straddle a chunk boundary");
        GameTestSequence sequence = prepareRoom(context);
        sequence.thenWaitUntil(() -> context.setBlock(source, SkylightMod.SKY_WINDOW))
                .thenWaitUntil(() -> {
                    expectLight(context, sample, 15, 0);
                    expectLight(context, sample.east(), 14, 0);
                })
                .thenWaitUntil(() -> context.setBlock(source, Blocks.STONE))
                .thenWaitUntil(() -> {
                    expectLight(context, sample, 0, 0);
                    expectLight(context, sample.east(), 0, 0);
                })
                .thenSucceed();
    }

    @GameTest(structure = TALL_ROOM, maxTicks = 6000)
    public void tallEmptyChunkSectionsKeepDirectSkylight(GameTestHelper context) {
        buildRoom(context, 46, 70, 46);
        BlockPos origin = context.absolutePos(BlockPos.ZERO);
        int centerX = Math.floorDiv(origin.getX() + 17, 16) * 16 + 7 - origin.getX();
        int centerZ = Math.floorDiv(origin.getZ() + 17, 16) * 16 + 7 - origin.getZ();
        BlockPos source = new BlockPos(centerX, 69, centerZ);
        BlockPos sample = new BlockPos(centerX, 2, centerZ);
        awaitLighting(context, 46, 46)
                .thenWaitUntil(() -> expectLight(context, sample, 0, 0))
                .thenWaitUntil(() -> context.setBlock(source, SkylightMod.SKY_WINDOW))
                .thenWaitUntil(() -> {
                    for (int y : new int[]{2, 17, 33, 49, 68}) {
                        expectLight(context, new BlockPos(centerX, y, centerZ), 15, 0);
                    }
                    expectLight(context, sample.east(), 14, 0);
                })
                .thenWaitUntil(() -> context.setBlock(source, Blocks.STONE))
                .thenWaitUntil(() -> {
                    for (int y : new int[]{2, 17, 33, 49, 68}) {
                        expectLight(context, new BlockPos(centerX, y, centerZ), 0, 0);
                    }
                })
                .thenSucceed();
    }

    @GameTest(structure = ROOM, skyAccess = true, maxTicks = 6000)
    public void naturalSkySurvivesWindowRemovalAndRespectsClosingTheRoof(GameTestHelper context) {
        GameTestSequence sequence = prepareRoom(context);
        sequence.thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .thenWaitUntil(() -> context.setBlock(WINDOW, SkylightMod.SKY_WINDOW))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .thenWaitUntil(() -> {
                    context.setBlock(WINDOW.above(), Blocks.AIR);
                    context.setBlock(WINDOW, Blocks.AIR);
                })
                .thenIdle(20)
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .thenWaitUntil(() -> context.setBlock(WINDOW, Blocks.STONE))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .thenSucceed();
    }

    @GameTest(structure = ROOM, maxTicks = 6000)
    public void anInitiallyBlockedWindowWaitsForAnOpening(GameTestHelper context) {
        GameTestSequence sequence = prepareRoom(context);
        fillPlane(context, 8, Blocks.STONE);
        sequence.thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .thenWaitUntil(() -> context.setBlock(WINDOW, SkylightMod.SKY_WINDOW))
                .thenIdle(20)
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 0, 0);
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                })
                .thenWaitUntil(() -> context.setBlock(WINDOW.below(), Blocks.AIR))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .thenSucceed();
    }

    @GameTest(structure = ROOM, maxTicks = 6000)
    public void stackedWindowsIlluminateSeparateRoomsIndependently(GameTestHelper context) {
        BlockPos lowerWindow = new BlockPos(11, 5, 11);
        GameTestSequence sequence = prepareRoom(context);
        fillPlane(context, 5, Blocks.STONE);
        sequence.thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .thenWaitUntil(() -> {
                    context.setBlock(WINDOW, SkylightMod.SKY_WINDOW);
                    context.setBlock(lowerWindow, SkylightMod.SKY_WINDOW);
                })
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 15, 0);
                    expectLight(context, FLOOR_SAMPLE, 15, 0);
                })
                .thenWaitUntil(() -> context.setBlock(WINDOW, Blocks.STONE))
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 0, 0);
                    expectLight(context, FLOOR_SAMPLE, 15, 0);
                })
                .thenWaitUntil(() -> context.setBlock(lowerWindow, Blocks.STONE))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .thenSucceed();
    }

    @GameTest(structure = ROOM, maxTicks = 6000)
    public void reenablingLoadedChunkRediscoversSources(GameTestHelper context) {
        BlockPos absolute = context.absolutePos(WINDOW);
        ChunkPos chunkPos = new ChunkPos(absolute.getX() >> 4, absolute.getZ() >> 4);
        AtomicReference<CompletableFuture<?>> completed = new AtomicReference<>();
        var lighting = context.getLevel().getChunkSource().getLightEngine();
        GameTestSequence sequence = prepareRoom(context);
        sequence.thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .thenWaitUntil(() -> context.setBlock(WINDOW, SkylightMod.SKY_WINDOW))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .thenWaitUntil(() -> {
                    lighting.setLightEnabled(chunkPos, false);
                    completed.set(lighting.waitForPendingTasks(chunkPos.x(), chunkPos.z()));
                })
                .thenWaitUntil(() -> context.assertTrue(completed.get().isDone(), "Chunk light must finish disabling"))
                .thenWaitUntil(() -> {
                    lighting.setLightEnabled(chunkPos, true);
                    completed.set(lighting.waitForPendingTasks(chunkPos.x(), chunkPos.z()));
                })
                .thenWaitUntil(() -> context.assertTrue(completed.get().isDone(), "Chunk light must finish enabling"))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .thenWaitUntil(() -> context.setBlock(WINDOW, Blocks.STONE))
                .thenWaitUntil(() -> {
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                    expectLight(context, FLOOR_SAMPLE.east(), 0, 0);
                })
                .thenSucceed();
    }

    @GameTest(structure = ROOM, maxTicks = 6000)
    public void leavesDirectlyBelowWindowAttenuateAndRecoverAfterReplacement(GameTestHelper context) {
        GameTestSequence sequence = prepareRoom(context);
        fillPlane(context, 8, Blocks.STONE);
        context.setBlock(WINDOW.below(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        sequence.thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .thenWaitUntil(() -> context.setBlock(WINDOW, SkylightMod.SKY_WINDOW))
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 14, 0);
                    expectLight(context, WINDOW.below(2), 13, 0);
                    expectLight(context, FLOOR_SAMPLE, 8, 0);
                })
                .thenWaitUntil(() -> context.setBlock(WINDOW.below(), Blocks.AIR))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .thenWaitUntil(() -> context.setBlock(WINDOW.below(),
                        Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)))
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 14, 0);
                    expectLight(context, FLOOR_SAMPLE, 8, 0);
                })
                .thenWaitUntil(() -> context.setBlock(WINDOW, Blocks.STONE))
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 0, 0);
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                })
                .thenSucceed();
    }

    @GameTest(structure = ROOM, maxTicks = 6000)
    public void waterDirectlyBelowWindowAttenuatesAndRemovalClearsLighting(GameTestHelper context) {
        GameTestSequence sequence = prepareRoom(context);
        fillPlane(context, 8, Blocks.STONE);
        context.setBlock(WINDOW.below(2), Blocks.GLASS);
        context.setBlock(WINDOW.below(), Blocks.WATER);
        sequence.thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .thenWaitUntil(() -> context.setBlock(WINDOW, SkylightMod.SKY_WINDOW))
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 14, 0);
                    expectLight(context, WINDOW.below(2), 13, 0);
                    expectLight(context, FLOOR_SAMPLE, 8, 0);
                })
                .thenWaitUntil(() -> context.setBlock(WINDOW, Blocks.STONE))
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 0, 0);
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                })
                .thenSucceed();
    }

    @GameTest(structure = ROOM, maxTicks = 6000)
    public void attenuatedSourceSurvivesNeighborSourceRemoval(GameTestHelper context) {
        BlockPos secondWindow = WINDOW.east();
        GameTestSequence sequence = prepareRoom(context);
        context.setBlock(WINDOW.below(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        sequence.thenWaitUntil(() -> {
                    context.setBlock(WINDOW, SkylightMod.SKY_WINDOW);
                    context.setBlock(secondWindow, SkylightMod.SKY_WINDOW);
                })
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 14, 0);
                    expectLight(context, FLOOR_SAMPLE.east(), 15, 0);
                })
                .thenWaitUntil(() -> context.setBlock(secondWindow, Blocks.STONE))
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 14, 0);
                    expectLight(context, WINDOW.below(2), 13, 0);
                    expectLight(context, FLOOR_SAMPLE, 8, 0);
                    expectLight(context, FLOOR_SAMPLE.east(), 7, 0);
                })
                .thenWaitUntil(() -> context.setBlock(WINDOW, Blocks.STONE))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .thenSucceed();
    }

    @GameTest(structure = ROOM, maxTicks = 6000)
    public void reenablingLoadedChunkRediscoversAttenuatedSources(GameTestHelper context) {
        BlockPos absolute = context.absolutePos(WINDOW);
        ChunkPos chunkPos = new ChunkPos(absolute.getX() >> 4, absolute.getZ() >> 4);
        AtomicReference<CompletableFuture<?>> completed = new AtomicReference<>();
        var lighting = context.getLevel().getChunkSource().getLightEngine();
        GameTestSequence sequence = prepareRoom(context);
        context.setBlock(WINDOW.below(), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        sequence.thenWaitUntil(() -> context.setBlock(WINDOW, SkylightMod.SKY_WINDOW))
                .thenWaitUntil(() -> expectLight(context, FLOOR_SAMPLE, 8, 0))
                .thenWaitUntil(() -> {
                    lighting.setLightEnabled(chunkPos, false);
                    completed.set(lighting.waitForPendingTasks(chunkPos.x(), chunkPos.z()));
                })
                .thenWaitUntil(() -> context.assertTrue(completed.get().isDone(), "Chunk light must finish disabling"))
                .thenWaitUntil(() -> {
                    lighting.setLightEnabled(chunkPos, true);
                    completed.set(lighting.waitForPendingTasks(chunkPos.x(), chunkPos.z()));
                })
                .thenWaitUntil(() -> context.assertTrue(completed.get().isDone(), "Chunk light must finish enabling"))
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 14, 0);
                    expectLight(context, FLOOR_SAMPLE, 8, 0);
                })
                .thenWaitUntil(() -> context.setBlock(WINDOW, Blocks.STONE))
                .thenWaitUntil(() -> {
                    expectLight(context, WINDOW.below(), 0, 0);
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                })
                .thenSucceed();
    }

    private static GameTestSequence prepareRoom(GameTestHelper context) {
        buildRoom(context, 22, 10, 22);
        return awaitLighting(context, 22, 22);
    }

    private static GameTestSequence awaitLighting(GameTestHelper context, int maxX, int maxZ) {
        BlockPos origin = context.absolutePos(BlockPos.ZERO);
        var lighting = context.getLevel().getChunkSource().getLightEngine();
        java.util.List<CompletableFuture<?>> pending = new java.util.ArrayList<>();
        for (int x = origin.getX() >> 4; x <= (origin.getX() + maxX) >> 4; x++) {
            for (int z = origin.getZ() >> 4; z <= (origin.getZ() + maxZ) >> 4; z++) {
                pending.add(lighting.waitForPendingTasks(x, z));
            }
        }
        CompletableFuture<?> completed = CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
        return context.startSequence().thenWaitUntil(() -> {
            context.assertTrue(completed.isDone(), "Fixture lighting must finish before window checks");
            completed.join();
        }).thenIdle(20);
    }

    private static void buildRoom(GameTestHelper context, int maxX, int maxY, int maxZ) {
        for (BlockPos pos : BlockPos.betweenClosed(1, 1, 1, maxX, maxY, maxZ)) {
            boolean shell = pos.getX() == 1 || pos.getX() == maxX
                    || pos.getZ() == 1 || pos.getZ() == maxZ
                    || pos.getY() == 1 || pos.getY() >= maxY - 1;
            context.setBlock(pos, shell ? Blocks.STONE : Blocks.AIR);
        }
    }

    private static void fillPlane(GameTestHelper context, int y, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(2, y, 2, 21, y, 21)) {
            context.setBlock(pos, block);
        }
    }

    private static void expectLight(GameTestHelper context, BlockPos relative, int sky, int block) {
        BlockPos absolute = context.absolutePos(relative);
        int actualSky = context.getLevel().getBrightness(LightLayer.SKY, absolute);
        int actualBlock = context.getLevel().getBrightness(LightLayer.BLOCK, absolute);
        if (actualSky != sky || actualBlock != block) {
            throw context.assertionException(relative, net.minecraft.network.chat.Component.literal("Expected sky/block light " + sky + "/" + block + " but got " + actualSky + "/" + actualBlock));
        }
    }
}
