package dev.skylight.block;

import dev.skylight.SkylightMod;
import dev.skylight.settings.SkyWindowSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class SkyWindowBlockEntity extends BlockEntity {
    private SkyWindowSettings settings = SkyWindowSettings.LIVE;

    public SkyWindowBlockEntity(BlockPos pos, BlockState state) {
        super(SkylightMod.SKY_WINDOW_ENTITY, pos, state);
    }

    public SkyWindowSettings getSettings() {
        return settings;
    }

    public void setSettings(SkyWindowSettings settings) {
        if (!this.settings.equals(settings)) {
            this.settings = settings;
            setChanged();
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().blockChanged(worldPosition);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        settings = SkyWindowSettings.fromNbt(input.read("SkySettings", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("SkySettings", CompoundTag.CODEC, settings.toNbt());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
