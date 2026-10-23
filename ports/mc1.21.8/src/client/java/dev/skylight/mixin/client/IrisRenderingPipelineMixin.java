package dev.skylight.mixin.client;

import dev.skylight.client.SkyWindowRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class IrisRenderingPipelineMixin {
    @Inject(method = "beginHand", at = @At("HEAD"), remap = false)
    private void skylight$beforeDepthCopies(CallbackInfo ci) {
        SkyWindowRenderer.revealSky();
    }

    @Inject(method = "beginTranslucents", at = @At("RETURN"), remap = false)
    private void skylight$protectTranslucentSky(CallbackInfo ci) {
        SkyWindowRenderer.protectSky();
    }
}
