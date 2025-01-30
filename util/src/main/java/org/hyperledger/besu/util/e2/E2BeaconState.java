package org.hyperledger.besu.util.e2;

public class E2BeaconState {
    private final byte[] beaconState;
    private final int slot;

    public E2BeaconState(final byte[] beaconState, final int slot) {
        this.beaconState = beaconState;
        this.slot = slot;
    }

    public byte[] getBeaconState() {
        return beaconState;
    }

    public int getSlot() {
        return slot;
    }
}
