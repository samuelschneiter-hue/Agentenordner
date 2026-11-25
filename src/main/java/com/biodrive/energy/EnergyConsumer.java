package com.biodrive.energy;

import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a block entity or node capable of receiving BioDrive energy (unit: {@code E}).
 */
public interface EnergyConsumer {

    /**
     * Attempts to insert up to {@code maxAmount} energy into this consumer.
     *
     * @param maxAmount maximum energy to insert (must be >= 0)
     * @param simulate  if {@code true}, no internal state is modified
     * @return the amount of energy actually accepted (or that would be accepted)
     */
    int receiveEnergy(int maxAmount, boolean simulate);

    /**
     * Whether energy can be inserted into the given face of this block.
     * A {@code null} direction represents a side-agnostic query.
     */
    default boolean canReceive(@Nullable Direction side) {
        return true;
    }
}
