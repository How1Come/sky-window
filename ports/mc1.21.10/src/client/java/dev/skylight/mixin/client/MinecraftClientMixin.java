package dev.skylight.mixin.client;

import dev.skylight.client.SkyWindowControls;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void skylight$openSettings(CallbackInfo ci) {
        if (SkyWindowControls.tryOpenSettings()) {
            ci.cancel();
        }
    }
}
