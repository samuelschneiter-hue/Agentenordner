package com.biodrive.energy;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents a cached connected component of {@code energy_cable} blocks and its adjacent
 * energy endpoints.
 *
 * <p>During each server tick, {@link #tick(EnergyNodeLookup)} iterates exclusively over the
 * cached endpoints and performs zero graph or neighbor traversal across cable blocks.
 * Topology updates happen only when cables or adjacent blocks are placed, broken, or unloaded.
 */
public class EnergyNetwork {

    /**
     * Resolves an energy node (typically a {@code BlockEntity}) at a cached endpoint position.
     * Returning {@code null} indicates the position is currently unloaded or no longer holds an energy block.
     */
    @FunctionalInterface
    public interface EnergyNodeLookup {
        @Nullable Object getNode(BlockPos pos);
    }

    private final Set<BlockPos> cables = new HashSet<>();
    private final Map<BlockPos, EnumSet<Direction>> cachedEndpoints = new HashMap<>();
    private int roundRobinOffset = 0;

    public void addCable(BlockPos pos) {
        cables.add(pos.toImmutable());
    }

    public boolean removeCable(BlockPos pos) {
        return cables.remove(pos);
    }

    public boolean containsCable(BlockPos pos) {
        return cables.contains(pos);
    }

    public boolean isEmpty() {
        return cables.isEmpty();
    }

    public int getCableCount() {
        return cables.size();
    }

    public Set<BlockPos> getCables() {
        return Collections.unmodifiableSet(cables);
    }

    /**
     * Registers a cached energy endpoint touching a cable via {@code sideOnEndpoint}.
     */
    public void addEndpoint(BlockPos endpointPos, Direction sideOnEndpoint) {
        cachedEndpoints
                .computeIfAbsent(endpointPos.toImmutable(), ignored -> EnumSet.noneOf(Direction.class))
                .add(sideOnEndpoint);
    }

    /**
     * Removes a single side connection for an endpoint; removes the endpoint entry entirely if no sides remain.
     */
    public void removeEndpointSide(BlockPos endpointPos, Direction sideOnEndpoint) {
        EnumSet<Direction> sides = cachedEndpoints.get(endpointPos);
        if (sides != null) {
            sides.remove(sideOnEndpoint);
            if (sides.isEmpty()) {
                cachedEndpoints.remove(endpointPos);
            }
        }
    }

    /**
     * Removes all cached side connections for an endpoint position.
     */
    public void removeEndpoint(BlockPos endpointPos) {
        cachedEndpoints.remove(endpointPos);
    }

    public void clearEndpoints() {
        cachedEndpoints.clear();
    }

    public int getEndpointCount() {
        return cachedEndpoints.size();
    }

    public Map<BlockPos, Set<Direction>> getCachedEndpoints() {
        Map<BlockPos, Set<Direction>> view = new HashMap<>(cachedEndpoints.size());
        for (Map.Entry<BlockPos, EnumSet<Direction>> entry : cachedEndpoints.entrySet()) {
            view.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(view);
    }

    /**
     * Merges another network's cables and cached endpoints into this network.
     */
    public void mergeFrom(EnergyNetwork other) {
        if (other == this) {
            return;
        }
        cables.addAll(other.cables);
        for (Map.Entry<BlockPos, EnumSet<Direction>> entry : other.cachedEndpoints.entrySet()) {
            cachedEndpoints
                    .computeIfAbsent(entry.getKey(), ignored -> EnumSet.noneOf(Direction.class))
                    .addAll(entry.getValue());
        }
    }

    /**
     * Distributes energy across the cached endpoints of this network for a single tick.
     *
     * <p>Distribution priority:
     * <ol>
     *   <li>Pure providers ({@link EnergyProvider}, not {@link EnergyStorage}) -&gt; Pure consumers ({@link EnergyConsumer}, not {@link EnergyStorage})</li>
     *   <li>Remaining pure providers -&gt; Storages ({@link EnergyStorage})</li>
     *   <li>Storages -&gt; Remaining pure consumers</li>
     * </ol>
     *
     * @return total energy {@code E} transferred during this tick
     */
    public int tick(EnergyNodeLookup lookup) {
        if (cachedEndpoints.size() < 2) {
            return 0;
        }

        List<EnergyProvider> pureProviders = new ArrayList<>();
        List<EnergyStorage> storageExtractors = new ArrayList<>();
        List<EnergyStorage> storageReceivers = new ArrayList<>();
        List<EnergyConsumer> pureConsumers = new ArrayList<>();

        for (Map.Entry<BlockPos, EnumSet<Direction>> entry : cachedEndpoints.entrySet()) {
            Object node = lookup.getNode(entry.getKey());
            if (node == null) {
                continue;
            }
            EnumSet<Direction> sides = entry.getValue();

            if (node instanceof EnergyStorage storage) {
                if (canExtractOnAnySide(storage, sides)) {
                    storageExtractors.add(storage);
                }
                if (canReceiveOnAnySide(storage, sides)) {
                    storageReceivers.add(storage);
                }
            } else {
                if (node instanceof EnergyProvider provider && canExtractOnAnySide(provider, sides)) {
                    pureProviders.add(provider);
                }
                if (node instanceof EnergyConsumer consumer && canReceiveOnAnySide(consumer, sides)) {
                    pureConsumers.add(consumer);
                }
            }
        }

        int transferred = distribute(pureProviders, storageExtractors, storageReceivers, pureConsumers, roundRobinOffset);
        roundRobinOffset = (roundRobinOffset + 1) & 0x7FFFFFFF;
        return transferred;
    }

    /**
     * Convenience entry point for distributing energy when storages are both extractable and receivable.
     */
    public static int distribute(
            List<EnergyProvider> pureProviders,
            List<EnergyStorage> storages,
            List<EnergyConsumer> pureConsumers
    ) {
        return distribute(pureProviders, storages, storages, pureConsumers, 0);
    }

    private static int distribute(
            List<EnergyProvider> pureProviders,
            List<EnergyStorage> storageExtractors,
            List<EnergyStorage> storageReceivers,
            List<EnergyConsumer> pureConsumers,
            int offset
    ) {
        long totalTransferred = 0;

        // Phase 1: Pure providers supply pure consumers directly
        if (!pureProviders.isEmpty() && !pureConsumers.isEmpty()) {
            totalTransferred += transferFairly(pureProviders, pureConsumers, offset);
        }

        // Phase 2: Surplus energy from pure providers charges storages (batteries)
        if (!pureProviders.isEmpty() && !storageReceivers.isEmpty()) {
            totalTransferred += transferFairly(pureProviders, storageReceivers, offset);
        }

        // Phase 3: Storages discharge to satisfy remaining consumer demand
        if (!storageExtractors.isEmpty() && !pureConsumers.isEmpty()) {
            totalTransferred += transferFairly(storageExtractors, pureConsumers, offset);
        }

        return (int) Math.min(Integer.MAX_VALUE, totalTransferred);
    }

    /**
     * Transfers energy fairly (water-filling) from a group of providers to a group of consumers
     * with strict conservation of energy.
     */
    static int transferFairly(
            List<? extends EnergyProvider> providers,
            List<? extends EnergyConsumer> consumers,
            int offset
    ) {
        if (providers.isEmpty() || consumers.isEmpty()) {
            return 0;
        }

        int providerCount = providers.size();
        int[] supplies = new int[providerCount];
        long totalAvailable = 0;
        for (int i = 0; i < providerCount; i++) {
            int available = Math.max(0, providers.get(i).extractEnergy(Integer.MAX_VALUE, true));
            supplies[i] = available;
            totalAvailable += available;
        }
        if (totalAvailable <= 0) {
            return 0;
        }

        int consumerCount = consumers.size();
        int[] demands = new int[consumerCount];
        long totalDemand = 0;
        for (int i = 0; i < consumerCount; i++) {
            int demand = Math.max(0, consumers.get(i).receiveEnergy(Integer.MAX_VALUE, true));
            demands[i] = demand;
            totalDemand += demand;
        }
        if (totalDemand <= 0) {
            return 0;
        }

        long targetTransfer = Math.min(totalAvailable, totalDemand);

        // Step 1: Compute fair extraction shares across providers and actually extract
        int[] extractShares = allocateFairly(supplies, targetTransfer, offset);
        long actualExtracted = 0;
        for (int i = 0; i < providerCount; i++) {
            if (extractShares[i] > 0) {
                actualExtracted += Math.max(0, providers.get(i).extractEnergy(extractShares[i], false));
            }
        }
        if (actualExtracted <= 0) {
            return 0;
        }

        // Step 2: Compute fair delivery shares for the exact extracted amount and deliver
        int[] deliveryShares = allocateFairly(demands, actualExtracted, offset);
        long actualDelivered = 0;
        for (int i = 0; i < consumerCount; i++) {
            if (deliveryShares[i] > 0) {
                actualDelivered += Math.max(0, consumers.get(i).receiveEnergy(deliveryShares[i], false));
            }
        }

        return (int) Math.min(Integer.MAX_VALUE, actualDelivered);
    }

    /**
     * Water-filling allocation: distributes {@code totalBudget} as evenly as possible across buckets
     * bounded by {@code capacities}, using {@code offset} to rotate remainder units fairly.
     */
    static int[] allocateFairly(int[] capacities, long totalBudget, int offset) {
        int n = capacities.length;
        int[] allocated = new int[n];
        if (n == 0 || totalBudget <= 0) {
            return allocated;
        }

        int[] remaining = capacities.clone();
        List<Integer> active = new ArrayList<>(n);
        for (int step = 0; step < n; step++) {
            int idx = (step + Math.floorMod(offset, n)) % n;
            if (remaining[idx] > 0) {
                active.add(idx);
            }
        }

        long budget = totalBudget;
        while (budget > 0 && !active.isEmpty()) {
            int count = active.size();
            long equalShare = budget / count;

            if (equalShare == 0) {
                // Fewer units left than active buckets: hand out 1 unit each in rotated order
                for (int idx : active) {
                    if (budget <= 0) {
                        break;
                    }
                    allocated[idx] += 1;
                    remaining[idx] -= 1;
                    budget -= 1;
                }
                break;
            }

            Iterator<Integer> it = active.iterator();
            while (it.hasNext() && budget > 0) {
                int idx = it.next();
                int give = (int) Math.min(remaining[idx], equalShare);
                allocated[idx] += give;
                remaining[idx] -= give;
                budget -= give;
                if (remaining[idx] <= 0) {
                    it.remove();
                }
            }
        }

        return allocated;
    }

    private static boolean canExtractOnAnySide(EnergyProvider provider, EnumSet<Direction> sides) {
        for (Direction side : sides) {
            if (provider.canExtract(side)) {
                return true;
            }
        }
        return false;
    }

    private static boolean canReceiveOnAnySide(EnergyConsumer consumer, EnumSet<Direction> sides) {
        for (Direction side : sides) {
            if (consumer.canReceive(side)) {
                return true;
            }
        }
        return false;
    }
}
