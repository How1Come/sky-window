package dev.skylight.mixin.client;

import dev.skylight.client.SkyWindowRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.ObjectAllocator;
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
    private void skylight$begin(ObjectAllocator allocator, RenderTickCounter ticks, boolean outline, Camera camera,
                                GameRenderer gameRenderer,
                                Matrix4f view, Matrix4f projection, CallbackInfo ci) {
        SkyWindowRenderer.begin(camera, view, projection);
    }

    @Inject(method = "method_62215", at = @At("RETURN"))
    private void skylight$preserveSky(CallbackInfo ci) {
        SkyWindowRenderer.preserveSky();
    }

    @Inject(method = "method_62205", at = @At("HEAD"))
    private void skylight$revealClouds(CallbackInfo ci) {
        SkyWindowRenderer.revealSky();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void skylight$end(CallbackInfo ci) {
        SkyWindowRenderer.end();
    }
}
