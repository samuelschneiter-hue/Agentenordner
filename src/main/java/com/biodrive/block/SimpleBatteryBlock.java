package com.biodrive.block;

import com.biodrive.block.entity.SimpleBatteryBlockEntity;
import com.biodrive.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Energy storage block with a 5-stage visual {@code charge_level} (0-4) and 0-15 comparator output.
 */
public class SimpleBatteryBlock extends BlockWithEntity {

    public static final IntProperty CHARGE_LEVEL = IntProperty.of("charge_level", 0, 4);

    public SimpleBatteryBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(CHARGE_LEVEL, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(CHARGE_LEVEL);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof SimpleBatteryBlockEntity battery) {
            return SimpleBatteryBlockEntity.computeComparatorOutput(
                    battery.getEnergyStored(),
                    battery.getMaxEnergyStored()
            );
        }
        return 0;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient && hand == Hand.MAIN_HAND) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof SimpleBatteryBlockEntity battery) {
                int stored = battery.getEnergyStored();
                int capacity = battery.getMaxEnergyStored();
                int percent = capacity > 0 ? (int) Math.round(100.0 * stored / capacity) : 0;
                player.sendMessage(Text.translatable("message.biodrive.simple_battery.status", stored, capacity, percent), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new SimpleBatteryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) {
            return null;
        }
        return checkType(type, ModBlockEntities.SIMPLE_BATTERY, SimpleBatteryBlockEntity::tick);
    }
}
