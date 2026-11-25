package com.biodrive.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BioDriveConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void createsDefaultConfigFileWhenMissing() {
        Path configPath = tempDir.resolve("config").resolve("biodrive.json");
        BioDriveConfig config = BioDriveConfig.load(configPath);

        assertTrue(Files.exists(configPath), "Config file should be auto-generated when missing");
        assertNotNull(config.energy);
        assertNotNull(config.machines);
        assertNotNull(config.vehicle);

        // Energy fields
        assertEquals(20, config.energy.solarGenerationPerTick);
        assertEquals(40, config.energy.generatorGenerationPerTick);
        assertEquals(50000, config.energy.batteryCapacity);
        assertEquals(30, config.energy.oilPressEnergyPerTick);
        assertEquals(40, config.energy.processorEnergyPerTick);
        assertEquals(60, config.energy.refineryEnergyPerTick);
        assertEquals(25, config.energy.dispenserEnergyPerTick);

        // Machine fields
        assertTrue(config.machines.oilPress.inputCount > 0);
        assertTrue(config.machines.oilPress.outputAmount > 0);
        assertTrue(config.machines.oilPress.processTicks > 0);
        assertTrue(config.machines.processor.inputAmount > 0);
        assertTrue(config.machines.processor.outputAmount > 0);
        assertTrue(config.machines.processor.processTicks > 0);
        assertTrue(config.machines.refinery.inputAmount > 0);
        assertTrue(config.machines.refinery.outputAmount > 0);
        assertTrue(config.machines.refinery.processTicks > 0);

        // Vehicle fields
        assertTrue(config.vehicle.maxSpeed > 0.0);
        assertTrue(config.vehicle.fuelConsumption >= 0.0);
        assertTrue(config.vehicle.tankCapacity > 0);
        assertNotNull(config.vehicle.surfaceMultipliers);
        assertTrue(config.vehicle.surfaceMultipliers.containsKey("road"));
        assertTrue(config.vehicle.surfaceMultipliers.containsKey("grass"));
        assertTrue(config.vehicle.surfaceMultipliers.containsKey("sand"));
    }

    @Test
    void loadsCustomValuesFromExistingJson() throws Exception {
        Path configPath = tempDir.resolve("biodrive.json");
        String customJson = """
                {
                  "energy": {
                    "solarGenerationPerTick": 35,
                    "generatorGenerationPerTick": 75,
                    "batteryCapacity": 120000,
                    "oilPressEnergyPerTick": 45,
                    "processorEnergyPerTick": 55,
                    "refineryEnergyPerTick": 90,
                    "dispenserEnergyPerTick": 15
                  },
                  "machines": {
                    "oilPress": { "inputCount": 6, "outputAmount": 300, "processTicks": 80 },
                    "processor": { "inputAmount": 300, "outputAmount": 275, "processTicks": 110 },
                    "refinery": { "inputAmount": 275, "outputAmount": 250, "processTicks": 150 }
                  },
                  "vehicle": {
                    "maxSpeed": 1.15,
                    "fuelConsumption": 1.5,
                    "tankCapacity": 20000,
                    "surfaceMultipliers": {
                      "road": 1.4,
                      "sand": 0.6
                    }
                  }
                }
                """;
        Files.writeString(configPath, customJson, StandardCharsets.UTF_8);

        BioDriveConfig loaded = BioDriveConfig.load(configPath);

        assertEquals(35, loaded.energy.solarGenerationPerTick);
        assertEquals(75, loaded.energy.generatorGenerationPerTick);
        assertEquals(120000, loaded.energy.batteryCapacity);
        assertEquals(45, loaded.energy.oilPressEnergyPerTick);
        assertEquals(55, loaded.energy.processorEnergyPerTick);
        assertEquals(90, loaded.energy.refineryEnergyPerTick);
        assertEquals(15, loaded.energy.dispenserEnergyPerTick);

        assertEquals(6, loaded.machines.oilPress.inputCount);
        assertEquals(300, loaded.machines.oilPress.outputAmount);
        assertEquals(80, loaded.machines.oilPress.processTicks);
        assertEquals(275, loaded.machines.processor.outputAmount);
        assertEquals(250, loaded.machines.refinery.outputAmount);

        assertEquals(1.15, loaded.vehicle.maxSpeed, 1e-6);
        assertEquals(1.5, loaded.vehicle.fuelConsumption, 1e-6);
        assertEquals(20000, loaded.vehicle.tankCapacity);
        assertEquals(1.4, loaded.vehicle.surfaceMultipliers.get("road"), 1e-6);
        assertEquals(0.6, loaded.vehicle.surfaceMultipliers.get("sand"), 1e-6);
    }

    @Test
    void sanitizesMissingSectionsAndInvalidNumbers() throws Exception {
        Path configPath = tempDir.resolve("partial.json");
        String partialJson = """
                {
                  "energy": {
                    "solarGenerationPerTick": -10,
                    "batteryCapacity": 0
                  }
                }
                """;
        Files.writeString(configPath, partialJson, StandardCharsets.UTF_8);

        BioDriveConfig loaded = BioDriveConfig.load(configPath);

        assertEquals(20, loaded.energy.solarGenerationPerTick);
        assertEquals(50000, loaded.energy.batteryCapacity);
        assertNotNull(loaded.machines);
        assertNotNull(loaded.vehicle);
        assertTrue(loaded.vehicle.tankCapacity > 0);
    }
}
