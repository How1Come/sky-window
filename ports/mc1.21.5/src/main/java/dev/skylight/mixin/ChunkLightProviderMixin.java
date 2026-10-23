package dev.skylight.mixin;

import dev.skylight.SkylightMod;
import dev.skylight.lighting.SkylightLightHooks;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.chunk.light.ChunkLightProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkLightProvider.class)
public abstract class ChunkLightProviderMixin {
    @Inject(method = "needsLightUpdate", at = @At("HEAD"), cancellable = true)
    private static void skylight$checkWindowChanges(BlockState oldState,
                                                  BlockState newState, CallbackInfoReturnable<Boolean> cir) {
        if (oldState.isOf(SkylightMod.SKY_WINDOW) != newState.isOf(SkylightMod.SKY_WINDOW)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "doLightUpdates", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/chunk/light/ChunkLightProvider;method_51570()I"))
    private void skylight$updateSources(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof SkylightLightHooks hooks) {
            hooks.skylight$flushColumns();
        }
    }
}
