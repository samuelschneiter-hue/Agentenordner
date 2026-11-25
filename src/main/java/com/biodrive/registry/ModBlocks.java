package com.biodrive.registry;

import com.biodrive.BioDriveMod;
import com.biodrive.block.EnergyCableBlock;
import com.biodrive.block.FuelGeneratorBlock;
import com.biodrive.block.SimpleBatteryBlock;
import com.biodrive.block.SolarPanelBlock;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * Registers all BioDrive blocks and their corresponding {@link BlockItem}s.
 */
public final class ModBlocks {

    public static final Block SOLAR_PANEL = registerBlock(
            "solar_panel",
            new SolarPanelBlock(FabricBlockSettings.create()
                    .mapColor(MapColor.LAPIS_BLUE)
                    .strength(2.5f, 4.0f)
                    .requiresTool()
                    .nonOpaque()
                    .sounds(BlockSoundGroup.METAL))
    );

    public static final Block FUEL_GENERATOR = registerBlock(
            "fuel_generator",
            new FuelGeneratorBlock(FabricBlockSettings.create()
                    .mapColor(MapColor.IRON_GRAY)
                    .strength(3.5f, 6.0f)
                    .requiresTool()
                    .luminance(state -> state.get(FuelGeneratorBlock.LIT) ? 13 : 0)
                    .sounds(BlockSoundGroup.METAL))
    );

    public static final Block SIMPLE_BATTERY = registerBlock(
            "simple_battery",
            new SimpleBatteryBlock(FabricBlockSettings.create()
                    .mapColor(MapColor.IRON_GRAY)
                    .strength(3.0f, 5.0f)
                    .requiresTool()
                    .sounds(BlockSoundGroup.METAL))
    );

    public static final Block ENERGY_CABLE = registerBlock(
            "energy_cable",
            new EnergyCableBlock(FabricBlockSettings.create()
                    .mapColor(MapColor.GRAY)
                    .strength(1.0f, 2.0f)
                    .nonOpaque()
                    .sounds(BlockSoundGroup.COPPER))
    );

    private ModBlocks() {
    }

    private static Block registerBlock(String name, Block block) {
        Identifier id = BioDriveMod.id(name);
        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new FabricItemSettings()));
        return block;
    }

    public static Item asItem(Block block) {
        return block.asItem();
    }

    public static void register() {
        BioDriveMod.LOGGER.debug("Registered BioDrive blocks.");
    }
}
