package dev.skylight;

import dev.skylight.block.SkyWindowBlock;
import dev.skylight.block.SkyWindowBlockEntity;
import dev.skylight.network.SkyWindowNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class SkylightMod implements ModInitializer {
    public static final String MOD_ID = "sky_window";
    public static final Block SKY_WINDOW = new SkyWindowBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.PALE_PURPLE)
            .strength(1.5f, 6.0f)
            .sounds(BlockSoundGroup.GLASS)
            .requiresTool());
    public static final BlockEntityType<SkyWindowBlockEntity> SKY_WINDOW_ENTITY =
            FabricBlockEntityTypeBuilder.create(SkyWindowBlockEntity::new, SKY_WINDOW).build();

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(Registries.BLOCK, id("sky_window"), SKY_WINDOW);
        Registry.register(Registries.ITEM, id("sky_window"), new BlockItem(SKY_WINDOW, new Item.Settings()));
        Registry.register(Registries.BLOCK_ENTITY_TYPE, id("sky_window"), SKY_WINDOW_ENTITY);
        SkyWindowNetworking.initialize();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.add(SKY_WINDOW));
    }
}
