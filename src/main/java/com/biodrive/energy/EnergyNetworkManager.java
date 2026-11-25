package com.biodrive.energy;

import com.biodrive.registry.ModBlocks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Manages all cached {@link EnergyNetwork} instances per {@link ServerWorld}.
 *
 * <p>Networks are updated incrementally on cable placement/removal and neighbor updates,
 * and split via BFS only when a cable break actually disconnects a network. During ticks,
 * no cable graph traversal is performed.
 */
public class EnergyNetworkManager {

    private static final Map<ServerWorld, EnergyNetworkManager> WORLD_MANAGERS = new WeakHashMap<>();

    private final Map<BlockPos, EnergyNetwork> cableToNetwork = new HashMap<>();
    private final Set<EnergyNetwork> networks = new HashSet<>();

    /**
     * Registers Fabric server lifecycle hooks for ticking, chunk load/unload, and world unload.
     */
    public static void registerEvents() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            EnergyNetworkManager manager = WORLD_MANAGERS.get(world);
            if (manager != null) {
                manager.tick(world);
            }
        });

        ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            get(world).onChunkLoaded(world, chunk);
        });

        ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            EnergyNetworkManager manager = WORLD_MANAGERS.get(world);
            if (manager != null) {
                manager.onChunkUnloaded(world, chunk);
            }
        });

        ServerWorldEvents.UNLOAD.register((server, world) -> {
            WORLD_MANAGERS.remove(world);
        });
    }

    public static EnergyNetworkManager get(ServerWorld world) {
        return WORLD_MANAGERS.computeIfAbsent(world, ignored -> new EnergyNetworkManager());
    }

    private static EnergyNetwork.EnergyNodeLookup worldLookup(ServerWorld world) {
        return pos -> {
            if (!world.isChunkLoaded(pos)) {
                return null;
            }
            return world.getBlockEntity(pos);
        };
    }

    /**
     * Ticks every cached network in this world using O(endpoints) lookups.
     */
    public void tick(ServerWorld world) {
        tick(worldLookup(world));
    }

    /**
     * Ticks every cached network using the given node lookup and returns total transferred energy.
     */
    public int tick(EnergyNetwork.EnergyNodeLookup lookup) {
        if (networks.isEmpty()) {
            return 0;
        }
        int totalTransferred = 0;
        for (EnergyNetwork network : networks) {
            totalTransferred += network.tick(lookup);
        }
        return totalTransferred;
    }

    /**
     * Called when an {@code energy_cable} is placed or discovered on chunk load.
     */
    public void onCableAdded(ServerWorld world, BlockPos pos) {
        onCableAdded(pos, worldLookup(world));
    }

    /**
     * Adds a cable position to the cached topology, merging adjacent networks if necessary.
     */
    public void onCableAdded(BlockPos pos, EnergyNetwork.EnergyNodeLookup lookup) {
        BlockPos immutablePos = pos.toImmutable();
        if (cableToNetwork.containsKey(immutablePos)) {
            refreshCableNeighbors(immutablePos, cableToNetwork.get(immutablePos), lookup);
            return;
        }

        // Find all distinct existing networks adjacent to this cable
        List<EnergyNetwork> adjacentNetworks = new ArrayList<>(6);
        for (Direction direction : Direction.values()) {
            EnergyNetwork neighborNet = cableToNetwork.get(immutablePos.offset(direction));
            if (neighborNet != null && !adjacentNetworks.contains(neighborNet)) {
                adjacentNetworks.add(neighborNet);
            }
        }

        EnergyNetwork targetNetwork;
        if (adjacentNetworks.isEmpty()) {
            targetNetwork = new EnergyNetwork();
            networks.add(targetNetwork);
        } else {
            // Merge into the largest adjacent network to minimize re-indexing work
            targetNetwork = adjacentNetworks.get(0);
            for (int i = 1; i < adjacentNetworks.size(); i++) {
                if (adjacentNetworks.get(i).getCableCount() > targetNetwork.getCableCount()) {
                    targetNetwork = adjacentNetworks.get(i);
                }
            }
            for (EnergyNetwork other : adjacentNetworks) {
                if (other != targetNetwork) {
                    mergeNetworks(targetNetwork, other);
                }
            }
        }

        targetNetwork.addCable(immutablePos);
        cableToNetwork.put(immutablePos, targetNetwork);
        refreshCableNeighbors(immutablePos, targetNetwork, lookup);
    }

    /**
     * Called when an {@code energy_cable} is broken or replaced.
     */
    public void onCableRemoved(ServerWorld world, BlockPos pos) {
        onCableRemoved(pos, worldLookup(world));
    }

    /**
     * Removes a cable position from the cached topology, splitting the network via BFS
     * only if the removed cable had two or more adjacent cables in the same network.
     */
    public void onCableRemoved(BlockPos pos, EnergyNetwork.EnergyNodeLookup lookup) {
        BlockPos immutablePos = pos.toImmutable();
        EnergyNetwork network = cableToNetwork.remove(immutablePos);
        if (network == null) {
            return;
        }

        network.removeCable(immutablePos);

        // Detach any cached endpoint faces that were touching this removed cable
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = immutablePos.offset(direction);
            network.removeEndpointSide(neighborPos, direction.getOpposite());
        }

        if (network.isEmpty()) {
            networks.remove(network);
            return;
        }

        // Count remaining adjacent cables belonging to this network.
        // Removing a leaf (<= 1 cable neighbor) can never split a connected graph.
        List<BlockPos> adjacentCables = new ArrayList<>(6);
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = immutablePos.offset(direction);
            if (network.containsCable(neighborPos)) {
                adjacentCables.add(neighborPos);
            }
        }

        if (adjacentCables.size() >= 2) {
            rebuildComponents(network, lookup);
        }
    }

    /**
     * Called when a neighbor of a cable updates (e.g. a generator, battery, or machine was placed or broken).
     */
    public void onCableNeighborChanged(ServerWorld world, BlockPos cablePos, BlockPos neighborPos) {
        EnergyNetwork network = cableToNetwork.get(cablePos);
        if (network == null) {
            if (world.getBlockState(cablePos).isOf(ModBlocks.ENERGY_CABLE)) {
                onCableAdded(world, cablePos);
            }
            return;
        }
        onCableNeighborChanged(cablePos, neighborPos, worldLookup(world));
    }

    /**
     * Updates cached endpoint state for a single neighbor adjacent to a known cable.
     */
    public void onCableNeighborChanged(BlockPos cablePos, BlockPos neighborPos, EnergyNetwork.EnergyNodeLookup lookup) {
        EnergyNetwork network = cableToNetwork.get(cablePos);
        if (network == null) {
            return;
        }

        Direction dirFromCable = directionBetween(cablePos, neighborPos);
        if (dirFromCable == null) {
            refreshCableNeighbors(cablePos, network, lookup);
            return;
        }

        Direction sideOnNeighbor = dirFromCable.getOpposite();
        if (isEnergyNode(lookup.getNode(neighborPos))) {
            network.addEndpoint(neighborPos, sideOnNeighbor);
        } else {
            network.removeEndpointSide(neighborPos, sideOnNeighbor);
        }
    }

    /**
     * Ensures a cable adjacent to a ticking energy block is registered (safety net).
     */
    public void ensureCableRegistered(ServerWorld world, BlockPos cablePos) {
        if (!cableToNetwork.containsKey(cablePos) && world.getBlockState(cablePos).isOf(ModBlocks.ENERGY_CABLE)) {
            onCableAdded(world, cablePos);
        }
    }

    /**
     * Registers all cables inside a newly loaded chunk using fast palette checks per section.
     */
    private void onChunkLoaded(ServerWorld world, WorldChunk chunk) {
        ChunkSection[] sections = chunk.getSectionArray();
        ChunkPos chunkPos = chunk.getPos();
        int bottomSectionCoord = chunk.getBottomSectionCoord();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        EnergyNetwork.EnergyNodeLookup lookup = worldLookup(world);

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) {
                continue;
            }
            if (!section.hasAny(state -> state.isOf(ModBlocks.ENERGY_CABLE))) {
                continue;
            }

            int baseY = (bottomSectionCoord + i) << 4;
            int baseX = chunkPos.getStartX();
            int baseZ = chunkPos.getStartZ();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isOf(ModBlocks.ENERGY_CABLE)) {
                            mutable.set(baseX + x, baseY + y, baseZ + z);
                            onCableAdded(mutable, lookup);
                        }
                    }
                }
            }
        }
    }

    /**
     * Removes cables belonging to an unloading chunk from active networks.
     */
    private void onChunkUnloaded(ServerWorld world, WorldChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        List<BlockPos> toRemove = new ArrayList<>();
        for (BlockPos cablePos : cableToNetwork.keySet()) {
            if ((cablePos.getX() >> 4) == chunkPos.x && (cablePos.getZ() >> 4) == chunkPos.z) {
                toRemove.add(cablePos);
            }
        }
        EnergyNetwork.EnergyNodeLookup lookup = worldLookup(world);
        for (BlockPos cablePos : toRemove) {
            onCableRemoved(cablePos, lookup);
        }
    }

    private void mergeNetworks(EnergyNetwork target, EnergyNetwork source) {
        target.mergeFrom(source);
        for (BlockPos cablePos : source.getCables()) {
            cableToNetwork.put(cablePos, target);
        }
        networks.remove(source);
    }

    /**
     * Splits a potentially disconnected cable network into its exact connected components via BFS.
     */
    private void rebuildComponents(EnergyNetwork original, EnergyNetwork.EnergyNodeLookup lookup) {
        Set<BlockPos> remaining = new HashSet<>(original.getCables());
        networks.remove(original);

        while (!remaining.isEmpty()) {
            BlockPos seed = remaining.iterator().next();
            EnergyNetwork component = new EnergyNetwork();
            Queue<BlockPos> queue = new ArrayDeque<>();
            queue.add(seed);
            remaining.remove(seed);

            while (!queue.isEmpty()) {
                BlockPos current = queue.poll();
                component.addCable(current);
                cableToNetwork.put(current, component);
                refreshCableNeighbors(current, component, lookup);

                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = current.offset(direction);
                    if (remaining.remove(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }

            networks.add(component);
        }
    }

    private void refreshCableNeighbors(BlockPos cablePos, EnergyNetwork network, EnergyNetwork.EnergyNodeLookup lookup) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = cablePos.offset(direction);
            Direction sideOnNeighbor = direction.getOpposite();
            if (isEnergyNode(lookup.getNode(neighborPos))) {
                network.addEndpoint(neighborPos, sideOnNeighbor);
            } else {
                network.removeEndpointSide(neighborPos, sideOnNeighbor);
            }
        }
    }

    public static boolean isEnergyNode(@Nullable Object node) {
        return node instanceof EnergyProvider || node instanceof EnergyConsumer;
    }

    @Nullable
    private static Direction directionBetween(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1) {
            return null;
        }
        return Direction.fromVector(dx, dy, dz);
    }

    @Nullable
    public EnergyNetwork getNetworkAt(BlockPos cablePos) {
        return cableToNetwork.get(cablePos);
    }

    public Set<EnergyNetwork> getNetworks() {
        return Collections.unmodifiableSet(networks);
    }
}
