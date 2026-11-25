package com.biodrive.energy;

import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a block entity or node capable of supplying BioDrive energy (unit: {@code E}).
 */
public interface EnergyProvider {

    /**
     * Attempts to extract up to {@code maxAmount} energy from this provider.
     *
     * @param maxAmount maximum energy to extract (must be >= 0)
     * @param simulate  if {@code true}, no internal state is modified
     * @return the amount of energy actually extracted (or that would be extracted)
     */
    int extractEnergy(int maxAmount, boolean simulate);

    /**
     * Whether energy can be extracted from the given face of this block.
     * A {@code null} direction represents a side-agnostic query.
     */
    default boolean canExtract(@Nullable Direction side) {
        return true;
    }
}
