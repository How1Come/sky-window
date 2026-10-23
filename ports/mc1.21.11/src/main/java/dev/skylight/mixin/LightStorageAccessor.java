package dev.skylight.mixin;

import net.minecraft.world.chunk.light.LightStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LightStorage.class)
public interface LightStorageAccessor {
    @Invoker("hasSection")
    boolean skylight$hasSection(long sectionPos);

    @Invoker("get")
    int skylight$get(long blockPos);

    @Invoker("set")
    void skylight$set(long blockPos, int level);
}
