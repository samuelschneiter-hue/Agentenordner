package com.biodrive.energy;

import com.biodrive.block.entity.SimpleBatteryBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class EnergyNetworkTest {

    private static final class MockProvider implements EnergyProvider {
        int energy;
        final Direction blockedSide;

        MockProvider(int energy) {
            this(energy, null);
        }

        MockProvider(int energy, @Nullable Direction blockedSide) {
            this.energy = energy;
            this.blockedSide = blockedSide;
        }

        @Override
        public int extractEnergy(int maxAmount, boolean simulate) {
            int amount = Math.min(Math.max(0, maxAmount), energy);
            if (!simulate) {
                energy -= amount;
            }
            return amount;
        }

        @Override
        public boolean canExtract(@Nullable Direction side) {
            return side == null || side != blockedSide;
        }
    }

    private static final class MockConsumer implements EnergyConsumer {
        int received;
        final int capacity;

        MockConsumer(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public int receiveEnergy(int maxAmount, boolean simulate) {
            int free = Math.max(0, capacity - received);
            int amount = Math.min(Math.max(0, maxAmount), free);
            if (!simulate) {
                received += amount;
            }
            return amount;
        }
    }

    private static final class MockBattery implements EnergyStorage {
        int stored;
        final int capacity;

        MockBattery(int stored, int capacity) {
            this.stored = stored;
            this.capacity = capacity;
        }

        @Override
        public int getEnergyStored() {
            return stored;
        }

        @Override
        public int getMaxEnergyStored() {
            return capacity;
        }

        @Override
        public int extractEnergy(int maxAmount, boolean simulate) {
            int amount = Math.min(Math.max(0, maxAmount), stored);
            if (!simulate) {
                stored -= amount;
            }
            return amount;
        }

        @Override
        public int receiveEnergy(int maxAmount, boolean simulate) {
            int amount = Math.min(Math.max(0, maxAmount), getFreeEnergySpace());
            if (!simulate) {
                stored += amount;
            }
            return amount;
        }
    }

    @Test
    void distributesEnergyFairlyWithWaterFilling() {
        MockProvider solar = new MockProvider(20);
        MockConsumer smallMachine = new MockConsumer(5);
        MockConsumer largeMachine = new MockConsumer(30);

        int transferred = EnergyNetwork.distribute(
                List.of(solar),
                List.of(),
                List.of(smallMachine, largeMachine)
        );

        assertEquals(20, transferred);
        assertEquals(0, solar.energy);
        assertEquals(5, smallMachine.received);
        assertEquals(15, largeMachine.received);
    }

    @Test
    void prioritizesConsumersBeforeChargingBatteries() {
        MockProvider generator = new MockProvider(100);
        MockConsumer machine = new MockConsumer(40);
        MockBattery batteryA = new MockBattery(0, 1000);
        MockBattery batteryB = new MockBattery(0, 1000);

        int transferred = EnergyNetwork.distribute(
                List.of(generator),
                List.of(batteryA, batteryB),
                List.of(machine)
        );

        assertEquals(100, transferred);
        assertEquals(0, generator.energy);
        assertEquals(40, machine.received);
        assertEquals(30, batteryA.stored);
        assertEquals(30, batteryB.stored);
    }

    @Test
    void batteryDischargesWhenProviderCannotMeetDemand() {
        MockProvider solar = new MockProvider(20);
        MockBattery battery = new MockBattery(500, 1000);
        MockConsumer machineA = new MockConsumer(30);
        MockConsumer machineB = new MockConsumer(30);

        int transferred = EnergyNetwork.distribute(
                List.of(solar),
                List.of(battery),
                List.of(machineA, machineB)
        );

        assertEquals(60, transferred);
        assertEquals(0, solar.energy);
        assertEquals(30, machineA.received);
        assertEquals(30, machineB.received);
        assertEquals(460, battery.stored);
    }

    @Test
    void cachedCableNetworkAvoidsPerTickCableTraversalAndSupportsMergeAndSplit() {
        EnergyNetworkManager manager = new EnergyNetworkManager();
        Map<BlockPos, Object> worldNodes = new HashMap<>();

        MockProvider provider = new MockProvider(50);
        MockConsumer consumer = new MockConsumer(100);
        BlockPos providerPos = new BlockPos(0, 64, 0);
        BlockPos consumerPos = new BlockPos(201, 64, 0);
        worldNodes.put(providerPos, provider);
        worldNodes.put(consumerPos, consumer);

        // Place two disconnected cable segments: x=1..100 and x=102..200
        for (int x = 1; x <= 100; x++) {
            manager.onCableAdded(new BlockPos(x, 64, 0), worldNodes::get);
        }
        for (int x = 102; x <= 200; x++) {
            manager.onCableAdded(new BlockPos(x, 64, 0), worldNodes::get);
        }

        assertEquals(2, manager.getNetworks().size());
        assertNotSame(
                manager.getNetworkAt(new BlockPos(1, 64, 0)),
                manager.getNetworkAt(new BlockPos(200, 64, 0))
        );
        // Disconnected -> zero energy transferred
        assertEquals(0, manager.tick(worldNodes::get));

        // Place bridge cable at x=101 -> merges into 1 single network
        manager.onCableAdded(new BlockPos(101, 64, 0), worldNodes::get);
        assertEquals(1, manager.getNetworks().size());
        EnergyNetwork merged = manager.getNetworkAt(new BlockPos(1, 64, 0));
        assertSame(merged, manager.getNetworkAt(new BlockPos(200, 64, 0)));
        assertEquals(200, merged.getCableCount());
        assertEquals(2, merged.getEndpointCount());

        // Verify ticking ONLY looks up the 2 cached endpoints, NOT the 200 cables
        AtomicInteger lookupCount = new AtomicInteger(0);
        int transferred = manager.tick(pos -> {
            lookupCount.incrementAndGet();
            return worldNodes.get(pos);
        });

        assertEquals(50, transferred);
        assertEquals(50, consumer.received);
        assertEquals(2, lookupCount.get(), "Tick must only resolve cached endpoints, never traverse cables");

        // Break bridge cable at x=101 -> splits cleanly back into 2 networks
        provider.energy = 50;
        manager.onCableRemoved(new BlockPos(101, 64, 0), worldNodes::get);
        assertEquals(2, manager.getNetworks().size());
        assertEquals(0, manager.tick(worldNodes::get));
        assertEquals(50, provider.energy);
    }

    @Test
    void respectsDirectionalSideRestrictions() {
        EnergyNetworkManager manager = new EnergyNetworkManager();
        Map<BlockPos, Object> worldNodes = new HashMap<>();

        // Like SolarPanelBlockEntity: cannot extract from UP face
        MockProvider solar = new MockProvider(20, Direction.UP);
        MockConsumer consumer = new MockConsumer(50);

        BlockPos solarPos = new BlockPos(0, 64, 0);
        BlockPos cableAboveSolar = new BlockPos(0, 65, 0);
        BlockPos consumerPos = new BlockPos(1, 65, 0);

        worldNodes.put(solarPos, solar);
        worldNodes.put(consumerPos, consumer);

        manager.onCableAdded(cableAboveSolar, worldNodes::get);
        assertEquals(0, manager.tick(worldNodes::get), "Should not extract from blocked UP face");
        assertEquals(20, solar.energy);

        // Connecting on EAST side of solar panel allows extraction
        BlockPos cableBesideSolar = new BlockPos(1, 64, 0);
        manager.onCableAdded(cableBesideSolar, worldNodes::get);
        assertEquals(20, manager.tick(worldNodes::get));
        assertEquals(20, consumer.received);
    }

    @Test
    void batteryChargeLevelAndComparatorBoundaries() {
        assertEquals(0, SimpleBatteryBlockEntity.computeChargeLevel(0, 1000));
        assertEquals(1, SimpleBatteryBlockEntity.computeChargeLevel(1, 1000));
        assertEquals(1, SimpleBatteryBlockEntity.computeChargeLevel(250, 1000));
        assertEquals(2, SimpleBatteryBlockEntity.computeChargeLevel(251, 1000));
        assertEquals(2, SimpleBatteryBlockEntity.computeChargeLevel(500, 1000));
        assertEquals(3, SimpleBatteryBlockEntity.computeChargeLevel(501, 1000));
        assertEquals(3, SimpleBatteryBlockEntity.computeChargeLevel(750, 1000));
        assertEquals(4, SimpleBatteryBlockEntity.computeChargeLevel(751, 1000));
        assertEquals(4, SimpleBatteryBlockEntity.computeChargeLevel(1000, 1000));

        assertEquals(0, SimpleBatteryBlockEntity.computeComparatorOutput(0, 1000));
        assertEquals(1, SimpleBatteryBlockEntity.computeComparatorOutput(1, 1000));
        assertEquals(8, SimpleBatteryBlockEntity.computeComparatorOutput(500, 1000));
        assertEquals(15, SimpleBatteryBlockEntity.computeComparatorOutput(1000, 1000));
    }
}
