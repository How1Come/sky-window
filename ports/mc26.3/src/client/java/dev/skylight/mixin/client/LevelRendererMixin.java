package dev.skylight.mixin.client;

import com.mojang.renderpearl.api.commands.RenderPass;
import dev.skylight.client.SkyWindowRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.OptionsRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow @Final private LevelRenderState levelRenderState;
    @Shadow @Final private OptionsRenderState optionsRenderState;
    @Shadow @Final private CloudRenderer cloudRenderer;

    @Inject(method = "render", at = @At("HEAD"))
    private void skyWindowBegin(CallbackInfo ci) {
        SkyWindowRenderer.begin(levelRenderState, optionsRenderState);
    }

    @Inject(method = "lambda$addSkyPass$0", at = @At("RETURN"))
    private void skyWindowCapture(CallbackInfo ci) {
        SkyWindowRenderer.captureSky(Minecraft.getInstance().gameRenderer.mainRenderTarget());
    }

    @Inject(method = "prepareTranslucents", at = @At("RETURN"))
    private void skyWindowClouds(CallbackInfo ci) {
        SkyWindowRenderer.captureClouds(cloudRenderer, optionsRenderState);
    }

    @Inject(method = "executeSolid", at = @At("RETURN"))
    private void skyWindowDraw(ChunkSectionsToRender chunks, FeatureRenderDispatcher.PreparedFrame frame,
            RenderPass pass, CallbackInfo ci) {
        SkyWindowRenderer.draw(pass);
    }

    @Inject(method = "executeClassicTransparency", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/CloudRenderer;render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void skyWindowBeforeNativeClouds(CallbackInfo ci) {
        SkyWindowRenderer.revealIrisSky();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void skyWindowClose(CallbackInfo ci) {
        SkyWindowRenderer.close();
    }
}
