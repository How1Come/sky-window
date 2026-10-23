package dev.skylight.network;

import dev.skylight.SkylightMod;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record SkySettingsResultPayload(BlockPos pos, boolean success, int count, String messageKey) {
    public static final Identifier ID = SkylightMod.id("settings_result");

    public void write(PacketByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(success);
        buf.writeVarInt(count);
        buf.writeString(messageKey, 128);
    }

    public static SkySettingsResultPayload read(PacketByteBuf buf) {
        return new SkySettingsResultPayload(buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readString(128));
    }
}
