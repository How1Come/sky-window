package dev.skylight.network;

import dev.skylight.SkylightMod;
import dev.skylight.settings.SkyWindowSettings;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record ApplySkySettingsPayload(BlockPos pos, SkyWindowSettings settings) {
    public static final Identifier ID = SkylightMod.id("apply_settings");

    public void write(PacketByteBuf buf) {
        buf.writeBlockPos(pos);
        settings.write(buf);
    }

    public static ApplySkySettingsPayload read(PacketByteBuf buf) {
        return new ApplySkySettingsPayload(buf.readBlockPos(), SkyWindowSettings.read(buf));
    }
}
