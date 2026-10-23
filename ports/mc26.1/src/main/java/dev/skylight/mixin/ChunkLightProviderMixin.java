package dev.skylight.mixin;

import dev.skylight.SkylightMod;
import dev.skylight.lighting.SkylightLightHooks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightEngine.class)
public abstract class ChunkLightProviderMixin {
    @Inject(method = "hasDifferentLightProperties", at = @At("HEAD"), cancellable = true)
    private static void skylight$checkWindowChanges(BlockState oldState,
                                                  BlockState newState, CallbackInfoReturnable<Boolean> cir) {
        if (oldState.is(SkylightMod.SKY_WINDOW) != newState.is(SkylightMod.SKY_WINDOW)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "runLightUpdates", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/lighting/LightEngine;propagateDecreases()I"))
    private void skylight$updateSources(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof SkylightLightHooks hooks) {
            hooks.skylight$flushColumns();
        }
    }
}
