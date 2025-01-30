package org.hyperledger.besu.util.e2;

public interface E2StoreReaderListener {
    void handleBeaconState(E2BeaconState beaconState);
    void handleSlotIndex(E2SlotIndex slotIndex);
    void handleSignedBeaconSlot(E2SignedBeaconBlock signedBeaconBlock);
}
