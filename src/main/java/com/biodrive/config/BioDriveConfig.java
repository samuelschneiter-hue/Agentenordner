package com.biodrive.config;

import com.biodrive.BioDriveMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central JSON configuration for BioDrive, persisted at {@code config/biodrive.json}.
 */
public class BioDriveConfig {
    public static final String CONFIG_FILE_NAME = "biodrive.json";

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static volatile BioDriveConfig instance = new BioDriveConfig();

    public EnergyConfig energy = new EnergyConfig();
    public MachinesConfig machines = new MachinesConfig();
    public VehicleConfig vehicle = new VehicleConfig();

    public static BioDriveConfig get() {
        return instance;
    }

    /**
     * Loads the configuration from the Fabric config directory ({@code config/biodrive.json}),
     * creating it with defaults if it does not yet exist.
     */
    public static BioDriveConfig load() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
        return load(configPath);
    }

    /**
     * Loads the configuration from the given path, creating it with defaults if absent.
     * Any missing sections or invalid values are normalized to safe defaults.
     */
    public static BioDriveConfig load(Path configPath) {
        BioDriveConfig loaded;
        boolean shouldSave = false;

        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, BioDriveConfig.class);
                if (loaded == null) {
                    BioDriveMod.LOGGER.warn("Config file {} was empty, regenerating defaults.", configPath);
                    loaded = new BioDriveConfig();
                    shouldSave = true;
                }
            } catch (IOException | JsonParseException e) {
                BioDriveMod.LOGGER.error("Failed to read config file {}, falling back to defaults.", configPath, e);
                loaded = new BioDriveConfig();
                shouldSave = true;
            }
        } else {
            loaded = new BioDriveConfig();
            shouldSave = true;
        }

        if (loaded.sanitize()) {
            shouldSave = true;
        }

        if (shouldSave) {
            save(configPath, loaded);
        }

        instance = loaded;
        return loaded;
    }

    /**
     * Writes the given configuration to disk as pretty-printed JSON.
     */
    public static void save(Path configPath, BioDriveConfig config) {
        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            BioDriveMod.LOGGER.error("Failed to write config file {}", configPath, e);
        }
    }

    /**
     * Ensures nested objects are non-null and numeric constraints are valid.
     *
     * @return {@code true} if any field had to be repaired.
     */
    public boolean sanitize() {
        boolean modified = false;

        if (energy == null) {
            energy = new EnergyConfig();
            modified = true;
        }
        modified |= energy.sanitize();

        if (machines == null) {
            machines = new MachinesConfig();
            modified = true;
        }
        modified |= machines.sanitize();

        if (vehicle == null) {
            vehicle = new VehicleConfig();
            modified = true;
        }
        modified |= vehicle.sanitize();

        return modified;
    }

    public static class EnergyConfig {
        public int solarGenerationPerTick = 20;
        public int generatorGenerationPerTick = 40;
        public int batteryCapacity = 50000;
        public int oilPressEnergyPerTick = 30;
        public int processorEnergyPerTick = 40;
        public int refineryEnergyPerTick = 60;
        public int dispenserEnergyPerTick = 25;

        boolean sanitize() {
            boolean modified = false;
            if (solarGenerationPerTick < 0) {
                solarGenerationPerTick = 20;
                modified = true;
            }
            if (generatorGenerationPerTick < 0) {
                generatorGenerationPerTick = 40;
                modified = true;
            }
            if (batteryCapacity <= 0) {
                batteryCapacity = 50000;
                modified = true;
            }
            if (oilPressEnergyPerTick < 0) {
                oilPressEnergyPerTick = 30;
                modified = true;
            }
            if (processorEnergyPerTick < 0) {
                processorEnergyPerTick = 40;
                modified = true;
            }
            if (refineryEnergyPerTick < 0) {
                refineryEnergyPerTick = 60;
                modified = true;
            }
            if (dispenserEnergyPerTick < 0) {
                dispenserEnergyPerTick = 25;
                modified = true;
            }
            return modified;
        }
    }

    public static class MachinesConfig {
        public OilPressConfig oilPress = new OilPressConfig();
        public ProcessorConfig processor = new ProcessorConfig();
        public RefineryConfig refinery = new RefineryConfig();

        boolean sanitize() {
            boolean modified = false;
            if (oilPress == null) {
                oilPress = new OilPressConfig();
                modified = true;
            }
            modified |= oilPress.sanitize();

            if (processor == null) {
                processor = new ProcessorConfig();
                modified = true;
            }
            modified |= processor.sanitize();

            if (refinery == null) {
                refinery = new RefineryConfig();
                modified = true;
            }
            modified |= refinery.sanitize();
            return modified;
        }
    }

    public static class OilPressConfig {
        public int inputCount = 4;
        public int outputAmount = 250;
        public int processTicks = 100;

        boolean sanitize() {
            boolean modified = false;
            if (inputCount <= 0) {
                inputCount = 4;
                modified = true;
            }
            if (outputAmount <= 0) {
                outputAmount = 250;
                modified = true;
            }
            if (processTicks <= 0) {
                processTicks = 100;
                modified = true;
            }
            return modified;
        }
    }

    public static class ProcessorConfig {
        public int inputAmount = 250;
        public int outputAmount = 250;
        public int processTicks = 120;

        boolean sanitize() {
            boolean modified = false;
            if (inputAmount <= 0) {
                inputAmount = 250;
                modified = true;
            }
            if (outputAmount <= 0) {
                outputAmount = 250;
                modified = true;
            }
            if (processTicks <= 0) {
                processTicks = 120;
                modified = true;
            }
            return modified;
        }
    }

    public static class RefineryConfig {
        public int inputAmount = 250;
        public int outputAmount = 200;
        public int processTicks = 160;

        boolean sanitize() {
            boolean modified = false;
            if (inputAmount <= 0) {
                inputAmount = 250;
                modified = true;
            }
            if (outputAmount <= 0) {
                outputAmount = 200;
                modified = true;
            }
            if (processTicks <= 0) {
                processTicks = 160;
                modified = true;
            }
            return modified;
        }
    }

    public static class VehicleConfig {
        public double maxSpeed = 0.85;
        public double fuelConsumption = 1.0;
        public int tankCapacity = 10000;
        public Map<String, Double> surfaceMultipliers = createDefaultSurfaceMultipliers();

        private static Map<String, Double> createDefaultSurfaceMultipliers() {
            Map<String, Double> defaults = new LinkedHashMap<>();
            defaults.put("road", 1.25);
            defaults.put("stone", 1.15);
            defaults.put("grass", 1.0);
            defaults.put("dirt", 0.9);
            defaults.put("sand", 0.75);
            defaults.put("snow", 0.7);
            defaults.put("mud", 0.55);
            defaults.put("default", 1.0);
            return defaults;
        }

        boolean sanitize() {
            boolean modified = false;
            if (maxSpeed <= 0.0) {
                maxSpeed = 0.85;
                modified = true;
            }
            if (fuelConsumption < 0.0) {
                fuelConsumption = 1.0;
                modified = true;
            }
            if (tankCapacity <= 0) {
                tankCapacity = 10000;
                modified = true;
            }
            if (surfaceMultipliers == null || surfaceMultipliers.isEmpty()) {
                surfaceMultipliers = createDefaultSurfaceMultipliers();
                modified = true;
            }
            return modified;
        }
    }
}
