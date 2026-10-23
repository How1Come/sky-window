package dev.skylight.mixin.client;

import dev.skylight.client.SkyWindowControls;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void skylight$openSettings(CallbackInfo ci) {
        if (SkyWindowControls.tryOpenSettings()) {
            ci.cancel();
        }
    }
}
