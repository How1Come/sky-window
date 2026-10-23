package dev.skylight.settings;

import java.util.Locale;
import java.util.Objects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

public record SkyWindowSettings(boolean followWorld, int time, Weather weather) {
    public static final SkyWindowSettings LIVE = new SkyWindowSettings(true, 6000, Weather.CLEAR);

    public SkyWindowSettings {
        if (time < 0 || time >= 24000) {
            throw new IllegalArgumentException("Sky time must be between 0 and 23999");
        }
        Objects.requireNonNull(weather, "weather");
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("FollowWorld", followWorld);
        nbt.putInt("Time", time);
        nbt.putString("Weather", weather.name());
        return nbt;
    }

    public static SkyWindowSettings fromNbt(NbtCompound nbt) {
        if (nbt.getBoolean("FollowWorld").isEmpty()
                || nbt.getInt("Time").isEmpty()
                || nbt.getString("Weather").isEmpty()) {
            return LIVE;
        }
        try {
            return new SkyWindowSettings(nbt.getBoolean("FollowWorld", true), nbt.getInt("Time", 6000),
                    Weather.valueOf(nbt.getString("Weather", "CLEAR").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return LIVE;
        }
    }

    public void write(PacketByteBuf buf) {
        buf.writeBoolean(followWorld);
        buf.writeVarInt(time);
        buf.writeEnumConstant(weather);
    }

    public static SkyWindowSettings read(PacketByteBuf buf) {
        return new SkyWindowSettings(buf.readBoolean(), buf.readVarInt(), buf.readEnumConstant(Weather.class));
    }

    public enum Weather {
        CLEAR,
        RAIN,
        THUNDER,
        SNOW
    }
}
