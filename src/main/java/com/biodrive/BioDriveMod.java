package com.biodrive;

import com.biodrive.config.BioDriveConfig;
import com.biodrive.energy.EnergyNetworkManager;
import com.biodrive.registry.ModBlockEntities;
import com.biodrive.registry.ModBlocks;
import com.biodrive.registry.ModItemGroups;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Fabric mod entrypoint for BioDrive.
 */
public class BioDriveMod implements ModInitializer {

    public static final String MOD_ID = "biodrive";
    public static final Logger LOGGER = LoggerFactory.getLogger("BioDrive");

    @Override
    public void onInitialize() {
        BioDriveConfig.load();
        ModBlocks.register();
        ModBlockEntities.register();
        ModItemGroups.register();
        EnergyNetworkManager.registerEvents();

        LOGGER.info("BioDrive initialized (solar={} E/t, generator={} E/t, battery={} E).",
                BioDriveConfig.get().energy.solarGenerationPerTick,
                BioDriveConfig.get().energy.generatorGenerationPerTick,
                BioDriveConfig.get().energy.batteryCapacity);
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
