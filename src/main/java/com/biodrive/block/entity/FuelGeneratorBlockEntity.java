package com.biodrive.block.entity;

import com.biodrive.block.FuelGeneratorBlock;
import com.biodrive.config.BioDriveConfig;
import com.biodrive.energy.DirectEnergyTransfer;
import com.biodrive.energy.EnergyProvider;
import com.biodrive.registry.ModBlockEntities;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Burns vanilla furnace fuels (via Fabric {@link FuelRegistry}) to generate BioDrive energy.
 *
 * <p>Supports hoppers via {@link SidedInventory} (fuel accepted from top and sides; empty remainders
 * such as buckets extractable from the bottom) and automatically enters standby mode while powered
 * by Redstone without wasting burn time.
 */
public class FuelGeneratorBlockEntity extends BlockEntity implements EnergyProvider, SidedInventory {

    private static final String NBT_ENERGY = "Energy";
    private static final String NBT_BURN_TIME = "BurnTime";
    private static final String NBT_FUEL_TOTAL_TIME = "FuelTotalTime";

    private static final int[] FUEL_SLOTS = new int[]{0};

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(1, ItemStack.EMPTY);
    private int energyBuffer = 0;
    private int burnTime = 0;
    private int fuelTotalTime = 0;

    public FuelGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_GENERATOR, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, FuelGeneratorBlockEntity blockEntity) {
        if (world.isClient) {
            return;
        }

        boolean dirty = false;
        boolean redstoneStandby = world.isReceivingRedstonePower(pos);
        int generation = BioDriveConfig.get().energy.generatorGenerationPerTick;
        int maxBuffer = getBufferCapacity(generation);

        if (!redstoneStandby) {
            // Consume a new fuel item if idle and buffer has space
            if (blockEntity.burnTime <= 0 && generation > 0 && blockEntity.energyBuffer < maxBuffer) {
                ItemStack fuelStack = blockEntity.inventory.get(0);
                int fuelDuration = getFuelTime(fuelStack);
                if (fuelDuration > 0) {
                    blockEntity.burnTime = fuelDuration;
                    blockEntity.fuelTotalTime = fuelDuration;

                    Item remainder = fuelStack.getItem().getRecipeRemainder();
                    fuelStack.decrement(1);
                    if (fuelStack.isEmpty() && remainder != null) {
                        blockEntity.inventory.set(0, new ItemStack(remainder));
                    }
                    dirty = true;
                }
            }

            // Generate energy while actively burning
            if (blockEntity.burnTime > 0) {
                blockEntity.burnTime--;
                if (generation > 0 && blockEntity.energyBuffer < maxBuffer) {
                    blockEntity.energyBuffer = Math.min(maxBuffer, blockEntity.energyBuffer + generation);
                }
                dirty = true;
            }
        }

        boolean shouldBeLit = !redstoneStandby && blockEntity.burnTime > 0;
        if (state.get(FuelGeneratorBlock.LIT) != shouldBeLit) {
            world.setBlockState(pos, state.with(FuelGeneratorBlock.LIT, shouldBeLit), Block.NOTIFY_ALL);
            dirty = true;
        }

        if (blockEntity.energyBuffer > 0) {
            DirectEnergyTransfer.pushToAdjacent(world, pos, blockEntity);
        }

        if (dirty) {
            blockEntity.markDirty();
        }
    }

    public static int getFuelTime(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Integer time = FuelRegistry.INSTANCE.get(stack.getItem());
        return time != null && time > 0 ? time : 0;
    }

    public static boolean isFuel(ItemStack stack) {
        return getFuelTime(stack) > 0;
    }

    private static int getBufferCapacity(int generationPerTick) {
        return Math.max(200, generationPerTick * 10);
    }

    public int getBurnTime() {
        return burnTime;
    }

    public int getFuelTotalTime() {
        return fuelTotalTime;
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

    // --- SidedInventory implementation ---

    @Override
    public int[] getAvailableSlots(Direction side) {
        return FUEL_SLOTS;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable Direction dir) {
        // Accept valid furnace fuels from top and horizontal sides (hopper compatible)
        return slot == 0 && dir != Direction.DOWN && isFuel(stack);
    }

    @Override
    public boolean canExtract(int slot, ItemStack stack, Direction dir) {
        // Allow bottom hoppers to pull non-fuel remainders (e.g., empty buckets from lava buckets)
        return slot == 0 && dir == Direction.DOWN && !isFuel(stack);
    }

    @Override
    public int size() {
        return inventory.size();
    }

    @Override
    public boolean isEmpty() {
        return inventory.get(0).isEmpty();
    }

    @Override
    public ItemStack getStack(int slot) {
        return inventory.get(slot);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(inventory, slot, amount);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        ItemStack result = Inventories.removeStack(inventory, slot);
        if (!result.isEmpty()) {
            markDirty();
        }
        return result;
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        inventory.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) {
            stack.setCount(getMaxCountPerStack());
        }
        markDirty();
    }

    @Override
    public boolean isValid(int slot, ItemStack stack) {
        return slot == 0 && isFuel(stack);
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        if (world == null || world.getBlockEntity(pos) != this) {
            return false;
        }
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clear() {
        inventory.clear();
        markDirty();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, inventory);
        nbt.putInt(NBT_ENERGY, energyBuffer);
        nbt.putInt(NBT_BURN_TIME, burnTime);
        nbt.putInt(NBT_FUEL_TOTAL_TIME, fuelTotalTime);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        inventory.clear();
        Inventories.readNbt(nbt, inventory);
        energyBuffer = Math.max(0, nbt.getInt(NBT_ENERGY));
        burnTime = Math.max(0, nbt.getInt(NBT_BURN_TIME));
        fuelTotalTime = Math.max(0, nbt.getInt(NBT_FUEL_TOTAL_TIME));
    }
}
