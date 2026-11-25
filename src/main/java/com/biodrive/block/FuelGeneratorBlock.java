package com.biodrive.block;

import com.biodrive.block.entity.FuelGeneratorBlockEntity;
import com.biodrive.config.BioDriveConfig;
import com.biodrive.registry.ModBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Furnace-fuel generator with hopper support and Redstone-controlled auto-standby.
 */
public class FuelGeneratorBlock extends BlockWithEntity {

    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty LIT = Properties.LIT;

    public FuelGeneratorBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(LIT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
                .with(LIT, false);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof FuelGeneratorBlockEntity generator) {
                ItemScatterer.spawn(world, pos, generator);
                world.updateComparators(pos, this);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof FuelGeneratorBlockEntity generator)) {
            return ActionResult.PASS;
        }

        ItemStack held = player.getStackInHand(hand);
        ItemStack slotStack = generator.getStack(0);

        // Allow direct insertion of valid fuel items via right-click
        if (!held.isEmpty() && FuelGeneratorBlockEntity.isFuel(held)) {
            if (slotStack.isEmpty()) {
                int insertCount = Math.min(held.getCount(), held.getMaxCount());
                generator.setStack(0, held.copyWithCount(insertCount));
                if (!player.getAbilities().creativeMode) {
                    held.decrement(insertCount);
                }
                player.sendMessage(Text.translatable("message.biodrive.fuel_generator.inserted"), true);
                return ActionResult.CONSUME;
            } else if (ItemStack.canCombine(slotStack, held) && slotStack.getCount() < slotStack.getMaxCount()) {
                int space = slotStack.getMaxCount() - slotStack.getCount();
                int insertCount = Math.min(space, held.getCount());
                slotStack.increment(insertCount);
                if (!player.getAbilities().creativeMode) {
                    held.decrement(insertCount);
                }
                generator.markDirty();
                player.sendMessage(Text.translatable("message.biodrive.fuel_generator.inserted"), true);
                return ActionResult.CONSUME;
            }
        }

        // Sneak-click with empty hand extracts stored item (e.g. empty bucket or fuel)
        if (player.isSneaking() && held.isEmpty() && !slotStack.isEmpty()) {
            player.getInventory().offerOrDrop(generator.removeStack(0));
            return ActionResult.SUCCESS;
        }

        // Otherwise show concise status in action bar
        boolean standby = world.isReceivingRedstonePower(pos);
        if (standby) {
            player.sendMessage(Text.translatable("message.biodrive.fuel_generator.standby"), true);
        } else if (generator.getBurnTime() > 0) {
            int gen = BioDriveConfig.get().energy.generatorGenerationPerTick;
            player.sendMessage(Text.translatable("message.biodrive.fuel_generator.running", gen, slotStack.getCount()), true);
        } else {
            player.sendMessage(Text.translatable("message.biodrive.fuel_generator.idle", slotStack.getCount()), true);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FuelGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient) {
            return null;
        }
        return checkType(type, ModBlockEntities.FUEL_GENERATOR, FuelGeneratorBlockEntity::tick);
    }
}
