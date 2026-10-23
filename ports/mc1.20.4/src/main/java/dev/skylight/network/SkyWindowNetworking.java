package dev.skylight.network;

import dev.skylight.settings.SkyWindowSettingsService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

public final class SkyWindowNetworking {
    private static final Map<UUID, Long> LAST_APPLY = new HashMap<>();

    private SkyWindowNetworking() {
    }

    public static void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_APPLY.remove(handler.player.getUuid()));
        ServerPlayNetworking.registerGlobalReceiver(ApplySkySettingsPayload.ID, (server, player, handler, buf, sender) -> {
            ApplySkySettingsPayload payload = ApplySkySettingsPayload.read(buf);
            server.execute(() -> {
                long tick = server.getTicks();
                Long previous = LAST_APPLY.get(player.getUuid());
                if (previous != null && tick - previous < 10) {
                    send(player, new SkySettingsResultPayload(payload.pos(), false, 0,
                            "sky_window.settings.error.cooldown"));
                    return;
                }
                LAST_APPLY.put(player.getUuid(), tick);
                SkyWindowSettingsService.Result result = SkyWindowSettingsService.playerApply(
                        player, payload.pos(), payload.settings());
                send(player, new SkySettingsResultPayload(payload.pos(), result.success(),
                        result.count(), result.messageKey()));
            });
        });
    }

    private static void send(ServerPlayerEntity player, SkySettingsResultPayload payload) {
        PacketByteBuf buf = PacketByteBufs.create();
        payload.write(buf);
        ServerPlayNetworking.send(player, SkySettingsResultPayload.ID, buf);
    }
}
