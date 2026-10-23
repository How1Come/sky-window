package dev.skylight.network;

import dev.skylight.SkylightMod;
import dev.skylight.settings.SkyWindowSettings;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record ApplySkySettingsPayload(BlockPos pos, SkyWindowSettings settings) implements CustomPayload {
    public static final Id<ApplySkySettingsPayload> ID = new Id<>(SkylightMod.id("apply_settings"));
    public static final PacketCodec<RegistryByteBuf, ApplySkySettingsPayload> CODEC = PacketCodec.ofStatic(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                payload.settings().write(buf);
            },
            buf -> new ApplySkySettingsPayload(buf.readBlockPos(), SkyWindowSettings.read(buf)));

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
