package dev.skylight.client;

import dev.skylight.block.SkyWindowBlockEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientBlockEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public final class SkylightClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SkyWindowControls.initialize();
        ClientBlockEntityEvents.BLOCK_ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof SkyWindowBlockEntity) {
                SkyWindowRenderer.add(world, entity.getPos());
            }
        });
        ClientBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof SkyWindowBlockEntity) {
                SkyWindowRenderer.remove(world, entity.getPos());
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SkyWindowRenderer.reset());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SkyWindowRenderer.close());
    }
}
