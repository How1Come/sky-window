package dev.skylight.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

public final class SkyWindowBlock extends BlockWithEntity {
    public static final MapCodec<SkyWindowBlock> CODEC = createCodec(SkyWindowBlock::new);

    public SkyWindowBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<SkyWindowBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SkyWindowBlockEntity(pos, state);
    }
}
