package com.biodrive.registry;

import com.biodrive.BioDriveMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

/**
 * Registers the dedicated "BioDrive" Creative inventory tab.
 */
public final class ModItemGroups {

    public static final RegistryKey<ItemGroup> BIODRIVE_GROUP_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            BioDriveMod.id("general")
    );

    public static final ItemGroup BIODRIVE_GROUP = Registry.register(
            Registries.ITEM_GROUP,
            BIODRIVE_GROUP_KEY,
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModBlocks.SOLAR_PANEL))
                    .displayName(Text.translatable("itemGroup.biodrive.general"))
                    .entries((displayContext, entries) -> {
                        entries.add(ModBlocks.SOLAR_PANEL);
                        entries.add(ModBlocks.FUEL_GENERATOR);
                        entries.add(ModBlocks.SIMPLE_BATTERY);
                        entries.add(ModBlocks.ENERGY_CABLE);
                    })
                    .build()
    );

    private ModItemGroups() {
    }

    public static void register() {
        BioDriveMod.LOGGER.debug("Registered BioDrive creative tab.");
    }
}
