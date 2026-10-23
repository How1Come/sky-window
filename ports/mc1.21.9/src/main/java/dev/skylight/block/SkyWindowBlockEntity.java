package dev.skylight.block;

import dev.skylight.SkylightMod;
import dev.skylight.settings.SkyWindowSettings;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
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
    protected void readData(ReadView view) {
        super.readData(view);
        settings = SkyWindowSettings.fromNbt(view.read("SkySettings", NbtCompound.CODEC).orElseGet(NbtCompound::new));
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.put("SkySettings", NbtCompound.CODEC, settings.toNbt());
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
