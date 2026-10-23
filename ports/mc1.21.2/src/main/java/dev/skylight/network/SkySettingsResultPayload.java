package dev.skylight.network;

import dev.skylight.SkylightMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record SkySettingsResultPayload(BlockPos pos, boolean success, int count, String messageKey)
        implements CustomPayload {
    public static final Id<SkySettingsResultPayload> ID = new Id<>(SkylightMod.id("settings_result"));
    public static final PacketCodec<RegistryByteBuf, SkySettingsResultPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeBoolean(payload.success());
                buf.writeVarInt(payload.count());
                buf.writeString(payload.messageKey(), 128);
            },
            buf -> new SkySettingsResultPayload(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(),
                    buf.readString(128)));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
