package dev.skylight.network;

import dev.skylight.SkylightMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.BlockPos;

public record SkySettingsResultPayload(BlockPos pos, boolean success, int count, String messageKey)
        implements CustomPacketPayload {
    public static final Type<SkySettingsResultPayload> ID = new Type<>(SkylightMod.id("settings_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SkySettingsResultPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeBoolean(payload.success());
                buf.writeVarInt(payload.count());
                buf.writeUtf(payload.messageKey(), 128);
            },
            buf -> new SkySettingsResultPayload(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(),
                    buf.readUtf(128)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
