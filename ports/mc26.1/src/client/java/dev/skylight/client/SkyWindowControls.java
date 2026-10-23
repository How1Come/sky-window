package dev.skylight.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.skylight.SkylightMod;
import dev.skylight.block.SkyWindowBlockEntity;
import dev.skylight.network.ApplySkySettingsPayload;
import dev.skylight.network.SkySettingsResultPayload;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;

public final class SkyWindowControls {
    private static KeyMapping modifier;

    public static void initialize() {
        modifier = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sky_window.settings_modifier",
                InputConstants.Type.KEYSYM, InputConstants.KEY_LSHIFT,
                KeyMapping.Category.register(SkylightMod.id("settings"))));
        ClientPlayNetworking.registerGlobalReceiver(SkySettingsResultPayload.ID, (payload, context) -> {
            if (context.client().screen instanceof SkyWindowSettingsScreen screen
                    && screen.getBlockPos().equals(payload.pos())) {
                screen.receiveResult(payload);
            } else if (context.player() != null) {
                context.player().sendOverlayMessage(Component.translatable(payload.messageKey(), payload.count()));
            }
        });
    }

    public static KeyMapping modifier() {
        return modifier;
    }

    public static boolean tryOpenSettings() {
        Minecraft client = Minecraft.getInstance();
        if (modifier == null || !modifier.isDown() || client.screen != null || client.level == null
                || client.player == null || client.player.isSpectator()
                || !(client.hitResult instanceof BlockHitResult hit)
                || !(client.level.getBlockEntity(hit.getBlockPos()) instanceof SkyWindowBlockEntity entity)) {
            return false;
        }
        if (!ClientPlayNetworking.canSend(ApplySkySettingsPayload.ID)) {
            client.player.sendOverlayMessage(Component.translatable("message.sky_window.server_unavailable"));
            return true;
        }
        client.setScreen(new SkyWindowSettingsScreen(hit.getBlockPos(), entity.getSettings()));
        return true;
    }

    private SkyWindowControls() {
    }
}
