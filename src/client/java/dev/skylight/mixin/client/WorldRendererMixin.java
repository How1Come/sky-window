package dev.skylight.mixin.client;

import dev.skylight.client.SkyWindowRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void skylight$begin(RenderTickCounter ticks, boolean outline, Camera camera,
                                GameRenderer gameRenderer, LightmapTextureManager lightmap,
                                Matrix4f view, Matrix4f projection, CallbackInfo ci) {
        SkyWindowRenderer.begin(camera, view, projection);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", shift = At.Shift.AFTER))
    private void skylight$preserveSky(CallbackInfo ci) {
        SkyWindowRenderer.preserveSky();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getCloudRenderModeValue()Lnet/minecraft/client/option/CloudRenderMode;"))
    private void skylight$revealClouds(CallbackInfo ci) {
        SkyWindowRenderer.revealSky();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void skylight$end(CallbackInfo ci) {
        SkyWindowRenderer.end();
    }
}
