package dev.skylight.mixin;

import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LayerLightSectionStorage.class)
public interface LightStorageAccessor {
    @Invoker("storingLightForSection")
    boolean skylight$hasSection(long sectionPos);

    @Invoker("getStoredLevel")
    int skylight$get(long blockPos);

    @Invoker("setStoredLevel")
    void skylight$set(long blockPos, int level);
}
