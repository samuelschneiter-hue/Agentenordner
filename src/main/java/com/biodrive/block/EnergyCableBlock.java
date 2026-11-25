package com.biodrive.block;

import com.biodrive.energy.EnergyConsumer;
import com.biodrive.energy.EnergyNetworkManager;
import com.biodrive.energy.EnergyProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * 6-directional energy conduit that connects dynamically to adjacent cables and energy blocks
 * and maintains cached {@link com.biodrive.energy.EnergyNetwork} topology on placement/removal.
 */
public class EnergyCableBlock extends Block {

    public static final BooleanProperty NORTH = Properties.NORTH;
    public static final BooleanProperty SOUTH = Properties.SOUTH;
    public static final BooleanProperty EAST = Properties.EAST;
    public static final BooleanProperty WEST = Properties.WEST;
    public static final BooleanProperty UP = Properties.UP;
    public static final BooleanProperty DOWN = Properties.DOWN;

    public static final Map<Direction, BooleanProperty> DIRECTION_PROPERTIES = createDirectionMap();

    private static final VoxelShape CORE_SHAPE = Block.createCuboidShape(6.0, 6.0, 6.0, 10.0, 10.0, 10.0);
    private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(6.0, 6.0, 0.0, 10.0, 10.0, 6.0);
    private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(6.0, 6.0, 10.0, 10.0, 10.0, 16.0);
    private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(0.0, 6.0, 6.0, 6.0, 10.0, 10.0);
    private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(10.0, 6.0, 6.0, 16.0, 10.0, 10.0);
    private static final VoxelShape DOWN_SHAPE = Block.createCuboidShape(6.0, 0.0, 6.0, 10.0, 6.0, 10.0);
    private static final VoxelShape UP_SHAPE = Block.createCuboidShape(6.0, 10.0, 6.0, 10.0, 16.0, 10.0);

    private static final VoxelShape[] SHAPE_CACHE = buildShapeCache();

    public EnergyCableBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
                .with(NORTH, false)
                .with(SOUTH, false)
                .with(EAST, false)
                .with(WEST, false)
                .with(UP, false)
                .with(DOWN, false));
    }

    private static Map<Direction, BooleanProperty> createDirectionMap() {
        EnumMap<Direction, BooleanProperty> map = new EnumMap<>(Direction.class);
        map.put(Direction.NORTH, NORTH);
        map.put(Direction.SOUTH, SOUTH);
        map.put(Direction.EAST, EAST);
        map.put(Direction.WEST, WEST);
        map.put(Direction.UP, UP);
        map.put(Direction.DOWN, DOWN);
        return map;
    }

    private static VoxelShape[] buildShapeCache() {
        VoxelShape[] cache = new VoxelShape[64];
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CORE_SHAPE;
            if ((mask & (1 << 0)) != 0) shape = VoxelShapes.union(shape, NORTH_SHAPE);
            if ((mask & (1 << 1)) != 0) shape = VoxelShapes.union(shape, SOUTH_SHAPE);
            if ((mask & (1 << 2)) != 0) shape = VoxelShapes.union(shape, EAST_SHAPE);
            if ((mask & (1 << 3)) != 0) shape = VoxelShapes.union(shape, WEST_SHAPE);
            if ((mask & (1 << 4)) != 0) shape = VoxelShapes.union(shape, UP_SHAPE);
            if ((mask & (1 << 5)) != 0) shape = VoxelShapes.union(shape, DOWN_SHAPE);
            cache[mask] = shape.simplify();
        }
        return cache;
    }

    private static int getShapeMask(BlockState state) {
        int mask = 0;
        if (state.get(NORTH)) mask |= (1 << 0);
        if (state.get(SOUTH)) mask |= (1 << 1);
        if (state.get(EAST)) mask |= (1 << 2);
        if (state.get(WEST)) mask |= (1 << 3);
        if (state.get(UP)) mask |= (1 << 4);
        if (state.get(DOWN)) mask |= (1 << 5);
        return mask;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE_CACHE[getShapeMask(state)];
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        return getDefaultState()
                .with(NORTH, canConnectTo(world, pos, Direction.NORTH))
                .with(SOUTH, canConnectTo(world, pos, Direction.SOUTH))
                .with(EAST, canConnectTo(world, pos, Direction.EAST))
                .with(WEST, canConnectTo(world, pos, Direction.WEST))
                .with(UP, canConnectTo(world, pos, Direction.UP))
                .with(DOWN, canConnectTo(world, pos, Direction.DOWN));
    }

    @Override
    public BlockState getStateForNeighborUpdate(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            WorldAccess world,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        BooleanProperty prop = DIRECTION_PROPERTIES.get(direction);
        if (prop != null) {
            return state.with(prop, canConnectTo(world, pos, direction));
        }
        return state;
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient && !oldState.isOf(this) && world instanceof ServerWorld serverWorld) {
            EnergyNetworkManager.get(serverWorld).onCableAdded(serverWorld, pos);
        }
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!world.isClient && !newState.isOf(this) && world instanceof ServerWorld serverWorld) {
            EnergyNetworkManager.get(serverWorld).onCableRemoved(serverWorld, pos);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (!world.isClient && world instanceof ServerWorld serverWorld) {
            EnergyNetworkManager.get(serverWorld).onCableNeighborChanged(serverWorld, pos, sourcePos);
        }
    }

    /**
     * Determines whether this cable should visually connect in the given direction.
     */
    public boolean canConnectTo(BlockView world, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.offset(direction);
        BlockState neighborState = world.getBlockState(neighborPos);
        if (neighborState.isOf(this)) {
            return true;
        }

        BlockEntity be = world.getBlockEntity(neighborPos);
        Direction sideOnNeighbor = direction.getOpposite();
        if (be instanceof EnergyProvider provider && provider.canExtract(sideOnNeighbor)) {
            return true;
        }
        if (be instanceof EnergyConsumer consumer && consumer.canReceive(sideOnNeighbor)) {
            return true;
        }
        return false;
    }
}
