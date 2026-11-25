package com.biodrive.energy;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for direct face-to-face energy transfer between adjacent energy blocks
 * when no {@code energy_cable} sits between them.
 */
public final class DirectEnergyTransfer {

    private DirectEnergyTransfer() {
    }

    /**
     * Pushes available energy from {@code source} at {@code pos} directly to immediately
     * adjacent energy blocks.
     *
     * <p>Pure consumers always receive energy before {@link EnergyStorage} neighbors.
     * When {@code source} itself is an {@link EnergyStorage}, adjacent storages are only
     * equalized gently (when {@code source} holds more energy) to avoid oscillation loops.
     */
    public static int pushToAdjacent(World world, BlockPos pos, EnergyProvider source) {
        if (world == null || world.isClient) {
            return 0;
        }
        if (source.extractEnergy(Integer.MAX_VALUE, true) <= 0) {
            return 0;
        }

        boolean sourceIsStorage = source instanceof EnergyStorage;
        List<EnergyConsumer> adjacentPureConsumers = new ArrayList<>(6);
        List<EnergyStorage> adjacentStorages = new ArrayList<>(6);

        for (Direction direction : Direction.values()) {
            if (!source.canExtract(direction)) {
                continue;
            }
            BlockPos neighborPos = pos.offset(direction);
            if (!world.isChunkLoaded(neighborPos)) {
                continue;
            }
            BlockEntity neighborBe = world.getBlockEntity(neighborPos);
            if (neighborBe == null || neighborBe == source) {
                continue;
            }

            Direction receiveSide = direction.getOpposite();
            if (neighborBe instanceof EnergyStorage neighborStorage) {
                if (neighborStorage.canReceive(receiveSide)) {
                    adjacentStorages.add(neighborStorage);
                }
            } else if (neighborBe instanceof EnergyConsumer neighborConsumer) {
                if (neighborConsumer.canReceive(receiveSide)) {
                    adjacentPureConsumers.add(neighborConsumer);
                }
            }
        }

        int totalTransferred = 0;

        // 1. Always feed adjacent pure consumers first
        if (!adjacentPureConsumers.isEmpty()) {
            totalTransferred += EnergyNetwork.transferFairly(List.of(source), adjacentPureConsumers, 0);
        }

        // 2. Feed adjacent storages
        if (!adjacentStorages.isEmpty()) {
            if (!sourceIsStorage) {
                totalTransferred += EnergyNetwork.transferFairly(List.of(source), adjacentStorages, 0);
            } else {
                EnergyStorage sourceStorage = (EnergyStorage) source;
                for (EnergyStorage target : adjacentStorages) {
                    int diff = sourceStorage.getEnergyStored() - target.getEnergyStored();
                    if (diff > 1) {
                        int halfDiff = diff / 2;
                        int canPush = Math.min(
                                sourceStorage.extractEnergy(halfDiff, true),
                                target.receiveEnergy(halfDiff, true)
                        );
                        if (canPush > 0) {
                            int extracted = sourceStorage.extractEnergy(canPush, false);
                            int accepted = target.receiveEnergy(extracted, false);
                            totalTransferred += accepted;
                        }
                    }
                }
            }
        }

        return totalTransferred;
    }
}
