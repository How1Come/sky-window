package dev.skylight.client;

import dev.skylight.block.SkyWindowBlockEntity;
import dev.skylight.network.ApplySkySettingsPayload;
import dev.skylight.network.SkySettingsResultPayload;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.glfw.GLFW;

public final class SkyWindowControls {
    private static KeyBinding modifier;

    public static void initialize() {
        modifier = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.sky_window.settings_modifier",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_SHIFT, "key.categories.sky_window"));
        KeyBinding.updateKeysByCode();
        ClientPlayNetworking.registerGlobalReceiver(SkySettingsResultPayload.ID, (client, handler, buf, sender) -> {
            SkySettingsResultPayload payload = SkySettingsResultPayload.read(buf);
            client.execute(() -> {
                if (client.currentScreen instanceof SkyWindowSettingsScreen screen
                        && screen.getBlockPos().equals(payload.pos())) {
                    screen.receiveResult(payload);
                } else if (client.player != null) {
                    client.player.sendMessage(Text.translatable(payload.messageKey(), payload.count()), true);
                }
            });
        });
    }

    public static KeyBinding modifier() {
        return modifier;
    }

    public static boolean tryOpenSettings() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (modifier == null || !modifier.isPressed() || client.currentScreen != null || client.world == null
                || client.player == null || client.player.isSpectator()
                || !(client.crosshairTarget instanceof BlockHitResult hit)
                || !(client.world.getBlockEntity(hit.getBlockPos()) instanceof SkyWindowBlockEntity entity)) {
            return false;
        }
        if (!ClientPlayNetworking.canSend(ApplySkySettingsPayload.ID)) {
            client.player.sendMessage(Text.translatable("message.sky_window.server_unavailable"), true);
            return true;
        }
        client.setScreen(new SkyWindowSettingsScreen(hit.getBlockPos(), entity.getSettings()));
        return true;
    }

    private SkyWindowControls() {
    }
}
