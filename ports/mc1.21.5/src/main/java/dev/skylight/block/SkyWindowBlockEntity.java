package dev.skylight.block;

import dev.skylight.SkylightMod;
import dev.skylight.settings.SkyWindowSettings;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

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
            markDirty();
            if (world instanceof ServerWorld serverWorld) {
                serverWorld.getChunkManager().markForUpdate(pos);
            }
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.readNbt(nbt, registryLookup);
        settings = SkyWindowSettings.fromNbt(nbt.getCompoundOrEmpty("SkySettings"));
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        super.writeNbt(nbt, registryLookup);
        nbt.put("SkySettings", settings.toNbt());
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}
