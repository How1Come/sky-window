package dev.skylight.network;

import dev.skylight.SkylightMod;
import dev.skylight.settings.SkyWindowSettings;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.BlockPos;

public record ApplySkySettingsPayload(BlockPos pos, SkyWindowSettings settings) implements CustomPacketPayload {
    public static final Type<ApplySkySettingsPayload> ID = new Type<>(SkylightMod.id("apply_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ApplySkySettingsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                payload.settings().write(buf);
            },
            buf -> new ApplySkySettingsPayload(buf.readBlockPos(), SkyWindowSettings.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
