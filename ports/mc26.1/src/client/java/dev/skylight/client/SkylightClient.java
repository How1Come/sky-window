package dev.skylight.client;

import dev.skylight.block.SkyWindowBlockEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class SkylightClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SkyWindowControls.initialize();
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof SkyWindowBlockEntity) {
                SkyWindowRenderer.add(level, entity.getBlockPos());
            }
        });
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof SkyWindowBlockEntity) {
                SkyWindowRenderer.remove(level, entity.getBlockPos());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(SkyWindowRenderer::extract);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SkyWindowRenderer.reset());
    }
}
