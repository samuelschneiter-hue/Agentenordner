package com.biodrive.block.entity;

import com.biodrive.config.BioDriveConfig;
import com.biodrive.energy.DirectEnergyTransfer;
import com.biodrive.energy.EnergyProvider;
import com.biodrive.registry.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Generates BioDrive energy during daytime under a clear, unobstructed sky.
 */
public class SolarPanelBlockEntity extends BlockEntity implements EnergyProvider {

    private static final String NBT_ENERGY = "Energy";

    private int energyBuffer = 0;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, SolarPanelBlockEntity blockEntity) {
        if (world.isClient) {
            return;
        }

        int generation = BioDriveConfig.get().energy.solarGenerationPerTick;
        if (generation > 0 && canGenerate(world, pos)) {
            int maxBuffer = getBufferCapacity(generation);
            if (blockEntity.energyBuffer < maxBuffer) {
                blockEntity.energyBuffer = Math.min(maxBuffer, blockEntity.energyBuffer + generation);
                blockEntity.markDirty();
            }
        }

        if (blockEntity.energyBuffer > 0) {
            DirectEnergyTransfer.pushToAdjacent(world, pos, blockEntity);
        }
    }

    /**
     * Checks whether the solar panel can currently generate energy at the given position:
     * requires sky light dimension, daytime, no rain/thunder, and clear sky directly above.
     */
    public static boolean canGenerate(World world, BlockPos pos) {
        return world.getDimension().hasSkyLight()
                && world.isDay()
                && !world.isRaining()
                && !world.isThundering()
                && world.isSkyVisible(pos.up());
    }

    private static int getBufferCapacity(int generationPerTick) {
        return Math.max(100, generationPerTick * 4);
    }

    public int getBufferedEnergy() {
        return energyBuffer;
    }

    @Override
    public int extractEnergy(int maxAmount, boolean simulate) {
        if (maxAmount <= 0 || energyBuffer <= 0) {
            return 0;
        }
        int extracted = Math.min(maxAmount, energyBuffer);
        if (!simulate) {
            energyBuffer -= extracted;
            markDirty();
        }
        return extracted;
    }

    @Override
    public boolean canExtract(@Nullable Direction side) {
        // Cables and adjacent blocks connect to the sides and bottom, not the top photovoltaic glass.
        return side != Direction.UP;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt(NBT_ENERGY, energyBuffer);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        energyBuffer = Math.max(0, nbt.getInt(NBT_ENERGY));
    }
}
