package com.sinestroboss;

import com.sinestroboss.item.SummonItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {
    public static final Item FEAR_TOTEM = Registry.register(
            Registries.ITEM,
            new Identifier(SinestroBoss.MOD_ID, "fear_totem"),
            new SummonItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));

    public static final Item SINESTRO_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            new Identifier(SinestroBoss.MOD_ID, "sinestro_spawn_egg"),
            new SpawnEggItem(ModEntities.SINESTRO, 0xD4A41B, 0x171616, new Item.Settings()));

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.add(SINESTRO_SPAWN_EGG));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(FEAR_TOTEM));
    }
}
