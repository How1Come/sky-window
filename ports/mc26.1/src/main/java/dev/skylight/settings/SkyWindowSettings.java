package dev.skylight.settings;

import java.util.Locale;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record SkyWindowSettings(boolean followWorld, int time, Weather weather) {
    public static final SkyWindowSettings LIVE = new SkyWindowSettings(true, 6000, Weather.CLEAR);

    public SkyWindowSettings {
        if (time < 0 || time >= 24000) {
            throw new IllegalArgumentException("Sky time must be between 0 and 23999");
        }
        Objects.requireNonNull(weather, "weather");
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putBoolean("FollowWorld", followWorld);
        nbt.putInt("Time", time);
        nbt.putString("Weather", weather.name());
        return nbt;
    }

    public static SkyWindowSettings fromNbt(CompoundTag nbt) {
        try {
            return new SkyWindowSettings(nbt.getBooleanOr("FollowWorld", true), nbt.getIntOr("Time", 6000),
                    Weather.valueOf(nbt.getStringOr("Weather", "CLEAR").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return LIVE;
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(followWorld);
        buf.writeVarInt(time);
        buf.writeEnum(weather);
    }

    public static SkyWindowSettings read(FriendlyByteBuf buf) {
        return new SkyWindowSettings(buf.readBoolean(), buf.readVarInt(), buf.readEnum(Weather.class));
    }

    public enum Weather {
        CLEAR,
        RAIN,
        THUNDER,
        SNOW
    }
}
