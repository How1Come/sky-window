package dev.skylight.test;

import dev.skylight.SkylightMod;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.text.Text;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.test.TestContext;
import net.minecraft.test.TimedTaskRunner;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;

public final class SkylightGameTests {
    private static final String ROOM = "sky_window_gametest:room";
    private static final String TALL_ROOM = "sky_window_gametest:tall_room";
    private static final BlockPos WINDOW = new BlockPos(11, 9, 11);
    private static final BlockPos FLOOR_SAMPLE = new BlockPos(11, 2, 11);

    @GameTest(structure = ROOM, maxTicks = 400)
    public void placementProvidesRealSkylightWithLateralFalloff(TestContext context) {
        TimedTaskRunner sequence = prepareRoom(context);
        sequence.createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .createAndAdd(() -> context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW))
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 15, 0);
                    expectLight(context, FLOOR_SAMPLE, 15, 0);
                    expectLight(context, FLOOR_SAMPLE.east(), 14, 0);
                    expectLight(context, FLOOR_SAMPLE.east(2), 13, 0);
                    context.expectBlock(Blocks.STONE, WINDOW.up());
                })
                .completeIfSuccessful();
    }

    @GameTest(structure = ROOM, maxTicks = 600)
    public void removalAndReplacementRecomputeLighting(TestContext context) {
        TimedTaskRunner sequence = prepareRoom(context);
        sequence.createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .createAndAdd(() -> context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .createAndAdd(() -> context.setBlockState(WINDOW, Blocks.STONE))
                .createAndAdd(() -> {
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                    expectLight(context, FLOOR_SAMPLE.east(), 0, 0);
                })
                .createAndAdd(() -> context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .completeIfSuccessful();
    }

    @GameTest(structure = ROOM, maxTicks = 600)
    public void stoneOccludesSkylightAndBreakingItRestoresSkylight(TestContext context) {
        TimedTaskRunner sequence = prepareRoom(context);
        sequence.createAndAdd(() -> context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .createAndAdd(() -> fillPlane(context, 5, Blocks.STONE))
                .createAndAdd(() -> {
                    expectLight(context, new BlockPos(11, 6, 11), 15, 0);
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                })
                .createAndAdd(() -> context.setBlockState(11, 5, 11, Blocks.AIR))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .completeIfSuccessful();
    }

    @GameTest(structure = ROOM, maxTicks = 600)
    public void removingOneWindowPreservesTheOtherSource(TestContext context) {
        BlockPos secondWindow = WINDOW.east(4);
        TimedTaskRunner sequence = prepareRoom(context);
        sequence.createAndAdd(() -> {
                    context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW);
                    context.setBlockState(secondWindow, SkylightMod.SKY_WINDOW);
                })
                .createAndAdd(() -> {
                    expectLight(context, FLOOR_SAMPLE, 15, 0);
                    expectLight(context, FLOOR_SAMPLE.east(2), 13, 0);
                    expectLight(context, FLOOR_SAMPLE.east(4), 15, 0);
                })
                .createAndAdd(() -> context.setBlockState(WINDOW, Blocks.STONE))
                .createAndAdd(() -> {
                    expectLight(context, FLOOR_SAMPLE, 11, 0);
                    expectLight(context, FLOOR_SAMPLE.east(4), 15, 0);
                })
                .createAndAdd(() -> context.setBlockState(secondWindow, Blocks.STONE))
                .createAndAdd(() -> {
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                    expectLight(context, FLOOR_SAMPLE.east(4), 0, 0);
                })
                .completeIfSuccessful();
    }

    @GameTest(structure = ROOM, maxTicks = 600)
    public void propagationAndRemovalCrossChunkBoundaries(TestContext context) {
        int boundaryX = 3;
        while ((context.getAbsolutePos(new BlockPos(boundaryX, 2, 11)).getX() & 15) != 15) {
            boundaryX++;
        }
        BlockPos source = new BlockPos(boundaryX, 9, 11);
        BlockPos sample = new BlockPos(boundaryX, 2, 11);
        check(context, (context.getAbsolutePos(sample).getX() >> 4)
                        != (context.getAbsolutePos(sample.east()).getX() >> 4),
                "Samples must straddle a chunk boundary");
        TimedTaskRunner sequence = prepareRoom(context);
        sequence.createAndAdd(() -> context.setBlockState(source, SkylightMod.SKY_WINDOW))
                .createAndAdd(() -> {
                    expectLight(context, sample, 15, 0);
                    expectLight(context, sample.east(), 14, 0);
                })
                .createAndAdd(() -> context.setBlockState(source, Blocks.STONE))
                .createAndAdd(() -> {
                    expectLight(context, sample, 0, 0);
                    expectLight(context, sample.east(), 0, 0);
                })
                .completeIfSuccessful();
    }

    @GameTest(structure = TALL_ROOM, maxTicks = 600)
    public void tallEmptyChunkSectionsKeepDirectSkylight(TestContext context) {
        buildRoom(context, 46, 70, 46);
        BlockPos origin = context.getAbsolutePos(BlockPos.ORIGIN);
        int centerX = Math.floorDiv(origin.getX() + 17, 16) * 16 + 7 - origin.getX();
        int centerZ = Math.floorDiv(origin.getZ() + 17, 16) * 16 + 7 - origin.getZ();
        BlockPos source = new BlockPos(centerX, 69, centerZ);
        BlockPos sample = new BlockPos(centerX, 2, centerZ);
        context.createTimedTaskRunner()
                .expectMinDuration(20)
                .createAndAdd(() -> expectLight(context, sample, 0, 0))
                .createAndAdd(() -> context.setBlockState(source, SkylightMod.SKY_WINDOW))
                .createAndAdd(() -> {
                    for (int y : new int[]{2, 17, 33, 49, 68}) {
                        expectLight(context, new BlockPos(centerX, y, centerZ), 15, 0);
                    }
                    expectLight(context, sample.east(), 14, 0);
                })
                .createAndAdd(() -> context.setBlockState(source, Blocks.STONE))
                .createAndAdd(() -> {
                    for (int y : new int[]{2, 17, 33, 49, 68}) {
                        expectLight(context, new BlockPos(centerX, y, centerZ), 0, 0);
                    }
                })
                .completeIfSuccessful();
    }

    @GameTest(structure = ROOM, skyAccess = true, maxTicks = 600)
    public void naturalSkySurvivesWindowRemovalAndRespectsClosingTheRoof(TestContext context) {
        TimedTaskRunner sequence = prepareRoom(context);
        sequence.createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .createAndAdd(() -> context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .createAndAdd(() -> {
                    context.setBlockState(WINDOW.up(), Blocks.AIR);
                    context.setBlockState(WINDOW, Blocks.AIR);
                })
                .expectMinDuration(20)
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .createAndAdd(() -> context.setBlockState(WINDOW, Blocks.STONE))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .completeIfSuccessful();
    }

    @GameTest(structure = ROOM, maxTicks = 600)
    public void anInitiallyBlockedWindowWaitsForAnOpening(TestContext context) {
        TimedTaskRunner sequence = prepareRoom(context);
        fillPlane(context, 8, Blocks.STONE);
        sequence.createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .createAndAdd(() -> context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW))
                .expectMinDuration(20)
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 0, 0);
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                })
                .createAndAdd(() -> context.setBlockState(WINDOW.down(), Blocks.AIR))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .completeIfSuccessful();
    }

    @GameTest(structure = ROOM, maxTicks = 600)
    public void stackedWindowsIlluminateSeparateRoomsIndependently(TestContext context) {
        BlockPos lowerWindow = new BlockPos(11, 5, 11);
        TimedTaskRunner sequence = prepareRoom(context);
        fillPlane(context, 5, Blocks.STONE);
        sequence.createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .createAndAdd(() -> {
                    context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW);
                    context.setBlockState(lowerWindow, SkylightMod.SKY_WINDOW);
                })
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 15, 0);
                    expectLight(context, FLOOR_SAMPLE, 15, 0);
                })
                .createAndAdd(() -> context.setBlockState(WINDOW, Blocks.STONE))
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 0, 0);
                    expectLight(context, FLOOR_SAMPLE, 15, 0);
                })
                .createAndAdd(() -> context.setBlockState(lowerWindow, Blocks.STONE))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .completeIfSuccessful();
    }

    @GameTest(structure = ROOM, maxTicks = 600)
    public void reenablingLoadedChunkRediscoversSources(TestContext context) {
        ChunkPos chunkPos = new ChunkPos(context.getAbsolutePos(WINDOW));
        AtomicReference<CompletableFuture<?>> completed = new AtomicReference<>();
        var lighting = context.getWorld().getChunkManager().getLightingProvider();
        TimedTaskRunner sequence = prepareRoom(context);
        sequence.createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .createAndAdd(() -> context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .createAndAdd(() -> {
                    lighting.setColumnEnabled(chunkPos, false);
                    completed.set(lighting.enqueue(chunkPos.x, chunkPos.z));
                })
                .createAndAdd(() -> check(context, completed.get().isDone(), "Chunk light must finish disabling"))
                .createAndAdd(() -> {
                    lighting.setColumnEnabled(chunkPos, true);
                    completed.set(lighting.enqueue(chunkPos.x, chunkPos.z));
                })
                .createAndAdd(() -> check(context, completed.get().isDone(), "Chunk light must finish enabling"))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .createAndAdd(() -> context.setBlockState(WINDOW, Blocks.STONE))
                .createAndAdd(() -> {
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                    expectLight(context, FLOOR_SAMPLE.east(), 0, 0);
                })
                .completeIfSuccessful();
    }

    @GameTest(structure = ROOM, maxTicks = 600)
    public void leavesDirectlyBelowWindowAttenuateAndRecoverAfterReplacement(TestContext context) {
        TimedTaskRunner sequence = prepareRoom(context);
        fillPlane(context, 8, Blocks.STONE);
        context.setBlockState(WINDOW.down(), Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
        sequence.createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .createAndAdd(() -> context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW))
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 14, 0);
                    expectLight(context, WINDOW.down(2), 13, 0);
                    expectLight(context, FLOOR_SAMPLE, 8, 0);
                })
                .createAndAdd(() -> context.setBlockState(WINDOW.down(), Blocks.AIR))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 15, 0))
                .createAndAdd(() -> context.setBlockState(WINDOW.down(),
                        Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true)))
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 14, 0);
                    expectLight(context, FLOOR_SAMPLE, 8, 0);
                })
                .createAndAdd(() -> context.setBlockState(WINDOW, Blocks.STONE))
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 0, 0);
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                })
                .completeIfSuccessful();
    }

    @GameTest(structure = ROOM, maxTicks = 600)
    public void waterDirectlyBelowWindowAttenuatesAndRemovalClearsLighting(TestContext context) {
        TimedTaskRunner sequence = prepareRoom(context);
        fillPlane(context, 8, Blocks.STONE);
        context.setBlockState(WINDOW.down(2), Blocks.GLASS);
        context.setBlockState(WINDOW.down(), Blocks.WATER);
        sequence.createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .createAndAdd(() -> context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW))
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 14, 0);
                    expectLight(context, WINDOW.down(2), 13, 0);
                    expectLight(context, FLOOR_SAMPLE, 8, 0);
                })
                .createAndAdd(() -> context.setBlockState(WINDOW, Blocks.STONE))
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 0, 0);
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                })
                .completeIfSuccessful();
    }

    @GameTest(structure = ROOM, maxTicks = 600)
    public void attenuatedSourceSurvivesNeighborSourceRemoval(TestContext context) {
        BlockPos secondWindow = WINDOW.east();
        TimedTaskRunner sequence = prepareRoom(context);
        context.setBlockState(WINDOW.down(), Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
        sequence.createAndAdd(() -> {
                    context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW);
                    context.setBlockState(secondWindow, SkylightMod.SKY_WINDOW);
                })
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 14, 0);
                    expectLight(context, FLOOR_SAMPLE.east(), 15, 0);
                })
                .createAndAdd(() -> context.setBlockState(secondWindow, Blocks.STONE))
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 14, 0);
                    expectLight(context, WINDOW.down(2), 13, 0);
                    expectLight(context, FLOOR_SAMPLE, 8, 0);
                    expectLight(context, FLOOR_SAMPLE.east(), 7, 0);
                })
                .createAndAdd(() -> context.setBlockState(WINDOW, Blocks.STONE))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 0, 0))
                .completeIfSuccessful();
    }

    @GameTest(structure = ROOM, maxTicks = 600)
    public void reenablingLoadedChunkRediscoversAttenuatedSources(TestContext context) {
        ChunkPos chunkPos = new ChunkPos(context.getAbsolutePos(WINDOW));
        AtomicReference<CompletableFuture<?>> completed = new AtomicReference<>();
        var lighting = context.getWorld().getChunkManager().getLightingProvider();
        TimedTaskRunner sequence = prepareRoom(context);
        context.setBlockState(WINDOW.down(), Blocks.OAK_LEAVES.getDefaultState().with(LeavesBlock.PERSISTENT, true));
        sequence.createAndAdd(() -> context.setBlockState(WINDOW, SkylightMod.SKY_WINDOW))
                .createAndAdd(() -> expectLight(context, FLOOR_SAMPLE, 8, 0))
                .createAndAdd(() -> {
                    lighting.setColumnEnabled(chunkPos, false);
                    completed.set(lighting.enqueue(chunkPos.x, chunkPos.z));
                })
                .createAndAdd(() -> check(context, completed.get().isDone(), "Chunk light must finish disabling"))
                .createAndAdd(() -> {
                    lighting.setColumnEnabled(chunkPos, true);
                    completed.set(lighting.enqueue(chunkPos.x, chunkPos.z));
                })
                .createAndAdd(() -> check(context, completed.get().isDone(), "Chunk light must finish enabling"))
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 14, 0);
                    expectLight(context, FLOOR_SAMPLE, 8, 0);
                })
                .createAndAdd(() -> context.setBlockState(WINDOW, Blocks.STONE))
                .createAndAdd(() -> {
                    expectLight(context, WINDOW.down(), 0, 0);
                    expectLight(context, FLOOR_SAMPLE, 0, 0);
                })
                .completeIfSuccessful();
    }

    private static TimedTaskRunner prepareRoom(TestContext context) {
        buildRoom(context, 22, 10, 22);
        return context.createTimedTaskRunner().expectMinDuration(20);
    }

    private static void buildRoom(TestContext context, int maxX, int maxY, int maxZ) {
        for (BlockPos pos : BlockPos.iterate(1, 1, 1, maxX, maxY, maxZ)) {
            boolean shell = pos.getX() == 1 || pos.getX() == maxX
                    || pos.getZ() == 1 || pos.getZ() == maxZ
                    || pos.getY() == 1 || pos.getY() >= maxY - 1;
            context.setBlockState(pos, shell ? Blocks.STONE : Blocks.AIR);
        }
    }

    private static void fillPlane(TestContext context, int y, Block block) {
        for (BlockPos pos : BlockPos.iterate(2, y, 2, 21, y, 21)) {
            context.setBlockState(pos, block);
        }
    }

    private static void expectLight(TestContext context, BlockPos relative, int sky, int block) {
        BlockPos absolute = context.getAbsolutePos(relative);
        int actualSky = context.getWorld().getLightLevel(LightType.SKY, absolute);
        int actualBlock = context.getWorld().getLightLevel(LightType.BLOCK, absolute);
        if (actualSky != sky || actualBlock != block) {
            context.throwPositionedException(Text.literal("Expected sky/block light " + sky + "/" + block
                    + " but got " + actualSky + "/" + actualBlock), relative);
        }
    }
    private static void check(TestContext context, boolean condition, String message) {
        context.assertTrue(condition, Text.literal(message));
    }

}
