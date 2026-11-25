package com.biodrive.registry;

import com.biodrive.BioDriveMod;
import com.biodrive.block.entity.FuelGeneratorBlockEntity;
import com.biodrive.block.entity.SimpleBatteryBlockEntity;
import com.biodrive.block.entity.SolarPanelBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Registers all BioDrive {@link BlockEntityType}s.
 */
public final class ModBlockEntities {

    public static final BlockEntityType<SolarPanelBlockEntity> SOLAR_PANEL = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            BioDriveMod.id("solar_panel"),
            FabricBlockEntityTypeBuilder.create(SolarPanelBlockEntity::new, ModBlocks.SOLAR_PANEL).build()
    );

    public static final BlockEntityType<FuelGeneratorBlockEntity> FUEL_GENERATOR = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            BioDriveMod.id("fuel_generator"),
            FabricBlockEntityTypeBuilder.create(FuelGeneratorBlockEntity::new, ModBlocks.FUEL_GENERATOR).build()
    );

    public static final BlockEntityType<SimpleBatteryBlockEntity> SIMPLE_BATTERY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            BioDriveMod.id("simple_battery"),
            FabricBlockEntityTypeBuilder.create(SimpleBatteryBlockEntity::new, ModBlocks.SIMPLE_BATTERY).build()
    );

    private ModBlockEntities() {
    }

    public static void register() {
        BioDriveMod.LOGGER.debug("Registered BioDrive block entities.");
    }
}
