package dev.skylight;

import dev.skylight.block.SkyWindowBlock;
import dev.skylight.block.SkyWindowBlockEntity;
import dev.skylight.network.SkyWindowNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.resources.Identifier;

public final class SkylightMod implements ModInitializer {
    public static final String MOD_ID = "sky_window";
    public static final Block SKY_WINDOW = new SkyWindowBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id("sky_window")))
            .mapColor(MapColor.QUARTZ)
            .strength(1.5f, 6.0f)
            .sound(SoundType.GLASS)
            .requiresCorrectToolForDrops());
    public static final BlockEntityType<SkyWindowBlockEntity> SKY_WINDOW_ENTITY =
            FabricBlockEntityTypeBuilder.create(SkyWindowBlockEntity::new, SKY_WINDOW).build();

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.BLOCK, id("sky_window"), SKY_WINDOW);
        Registry.register(BuiltInRegistries.ITEM, id("sky_window"), new BlockItem(SKY_WINDOW, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id("sky_window")))));
        Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("sky_window"), SKY_WINDOW_ENTITY);
        SkyWindowNetworking.initialize();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> entries.accept(SKY_WINDOW));
    }
}
