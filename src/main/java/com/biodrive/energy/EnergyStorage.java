package com.biodrive.energy;

/**
 * Represents a block entity or node that stores BioDrive energy (unit: {@code E})
 * and acts as both a consumer (charging) and a provider (discharging).
 */
public interface EnergyStorage extends EnergyProvider, EnergyConsumer {

    /**
     * @return current stored energy in {@code E}
     */
    int getEnergyStored();

    /**
     * @return maximum energy capacity in {@code E}
     */
    int getMaxEnergyStored();

    /**
     * @return remaining free storage capacity in {@code E}
     */
    default int getFreeEnergySpace() {
        return Math.max(0, getMaxEnergyStored() - getEnergyStored());
    }
}
