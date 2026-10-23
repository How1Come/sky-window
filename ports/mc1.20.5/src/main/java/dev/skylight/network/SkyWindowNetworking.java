package dev.skylight.network;

import dev.skylight.settings.SkyWindowSettingsService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class SkyWindowNetworking {
    private static final Map<UUID, Long> LAST_APPLY = new HashMap<>();

    private SkyWindowNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(ApplySkySettingsPayload.ID, ApplySkySettingsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SkySettingsResultPayload.ID, SkySettingsResultPayload.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_APPLY.remove(handler.player.getUuid()));
        ServerPlayNetworking.registerGlobalReceiver(ApplySkySettingsPayload.ID, (payload, context) -> {
            long tick = context.player().getServer().getTicks();
            Long previous = LAST_APPLY.get(context.player().getUuid());
            if (previous != null && tick - previous < 10) {
                ServerPlayNetworking.send(context.player(), new SkySettingsResultPayload(payload.pos(), false, 0,
                        "sky_window.settings.error.cooldown"));
                return;
            }
            LAST_APPLY.put(context.player().getUuid(), tick);
            SkyWindowSettingsService.Result result = SkyWindowSettingsService.playerApply(
                    context.player(), payload.pos(), payload.settings());
            ServerPlayNetworking.send(context.player(), new SkySettingsResultPayload(payload.pos(), result.success(),
                    result.count(), result.messageKey()));
        });
    }
}
