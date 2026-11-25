package com.biodrive.block.entity;

import com.biodrive.block.SimpleBatteryBlock;
import com.biodrive.config.BioDriveConfig;
import com.biodrive.energy.DirectEnergyTransfer;
import com.biodrive.energy.EnergyStorage;
import com.biodrive.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Stores BioDrive energy up to {@code config.energy.batteryCapacity}, updates its 5-stage
 * {@code charge_level} block state (0-4), and emits a 0-15 Redstone comparator signal.
 */
public class SimpleBatteryBlockEntity extends BlockEntity implements EnergyStorage {

    private static final String NBT_ENERGY = "Energy";

    private int energy = 0;
    private int lastComparatorOutput = -1;

    public SimpleBatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIMPLE_BATTERY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, SimpleBatteryBlockEntity blockEntity) {
        if (world.isClient) {
            return;
        }

        // Clamp stored energy if config batteryCapacity was lowered between restarts
        int capacity = blockEntity.getMaxEnergyStored();
        if (blockEntity.energy > capacity) {
            blockEntity.energy = capacity;
            blockEntity.markDirty();
            blockEntity.syncVisualAndComparatorState();
        }

        if (blockEntity.energy > 0) {
            DirectEnergyTransfer.pushToAdjacent(world, pos, blockEntity);
        }
    }

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return BioDriveConfig.get().energy.batteryCapacity;
    }

    @Override
    public int receiveEnergy(int maxAmount, boolean simulate) {
        if (maxAmount <= 0) {
            return 0;
        }
        int freeSpace = getFreeEnergySpace();
        int accepted = Math.min(maxAmount, freeSpace);
        if (accepted > 0 && !simulate) {
            energy += accepted;
            markDirty();
            syncVisualAndComparatorState();
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxAmount, boolean simulate) {
        if (maxAmount <= 0 || energy <= 0) {
            return 0;
        }
        int extracted = Math.min(maxAmount, energy);
        if (extracted > 0 && !simulate) {
            energy -= extracted;
            markDirty();
            syncVisualAndComparatorState();
        }
        return extracted;
    }

    /**
     * Maps stored energy to a 5-stage visual charge level (0 = empty, 1..4 = quarters up to full).
     */
    public static int computeChargeLevel(int energy, int capacity) {
        if (energy <= 0 || capacity <= 0) {
            return 0;
        }
        if (energy >= capacity) {
            return 4;
        }
        double ratio = (double) energy / (double) capacity;
        if (ratio <= 0.25) {
            return 1;
        }
        if (ratio <= 0.50) {
            return 2;
        }
        if (ratio <= 0.75) {
            return 3;
        }
        return 4;
    }

    /**
     * Maps stored energy to a vanilla-style comparator output in range {@code 0..15}.
     */
    public static int computeComparatorOutput(int energy, int capacity) {
        if (energy <= 0 || capacity <= 0) {
            return 0;
        }
        if (energy >= capacity) {
            return 15;
        }
        return Math.min(15, 1 + (int) Math.floor(14.0 * energy / capacity));
    }

    private void syncVisualAndComparatorState() {
        if (world == null || world.isClient) {
            return;
        }

        int capacity = getMaxEnergyStored();
        int desiredChargeLevel = computeChargeLevel(energy, capacity);
        BlockState currentState = getCachedState();
        if (currentState.contains(SimpleBatteryBlock.CHARGE_LEVEL)
                && currentState.get(SimpleBatteryBlock.CHARGE_LEVEL) != desiredChargeLevel) {
            world.setBlockState(pos, currentState.with(SimpleBatteryBlock.CHARGE_LEVEL, desiredChargeLevel), Block.NOTIFY_LISTENERS);
        }

        int comparator = computeComparatorOutput(energy, capacity);
        if (comparator != lastComparatorOutput) {
            lastComparatorOutput = comparator;
            world.updateComparators(pos, currentState.getBlock());
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt(NBT_ENERGY, energy);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        energy = Math.max(0, Math.min(nbt.getInt(NBT_ENERGY), getMaxEnergyStored()));
        lastComparatorOutput = computeComparatorOutput(energy, getMaxEnergyStored());
    }
}
