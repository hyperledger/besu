package org.hyperledger.besu.util.e2;

import java.util.List;

public class E2SlotIndex {
    private final long startingSlot;
    private final List<Long> indexes;

    public E2SlotIndex(final long startingSlot, final List<Long> indexes) {
        this.startingSlot = startingSlot;
        this.indexes = indexes;
    }

    public long getStartingSlot() {
        return startingSlot;
    }

    public List<Long> getIndexes() {
        return indexes;
    }
}
