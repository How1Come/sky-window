package dev.skylight.mixin.client;

import dev.skylight.client.SkyWindowControls;
import java.util.Map;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    @Shadow @Final private static Map<String, KeyBinding> KEYS_BY_ID;
    @Shadow @Final private static Map<InputUtil.Key, KeyBinding> KEY_TO_BINDINGS;

    @Inject(method = "setKeyPressed", at = @At("RETURN"))
    private static void skylight$shareModifier(InputUtil.Key key, boolean pressed, CallbackInfo ci) {
        KeyBinding modifier = SkyWindowControls.modifier();
        if (modifier != null && KeyBindingHelper.getBoundKeyOf(modifier).equals(key)) {
            modifier.setPressed(pressed);
        }
    }

    @Inject(method = "updateKeysByCode", at = @At("RETURN"))
    private static void skylight$preserveSharedBinding(CallbackInfo ci) {
        KeyBinding modifier = SkyWindowControls.modifier();
        if (modifier != null) {
            InputUtil.Key key = KeyBindingHelper.getBoundKeyOf(modifier);
            for (KeyBinding binding : KEYS_BY_ID.values()) {
                if (binding != modifier && KeyBindingHelper.getBoundKeyOf(binding).equals(key)) {
                    KEY_TO_BINDINGS.put(key, binding);
                    return;
                }
            }
        }
    }
}
