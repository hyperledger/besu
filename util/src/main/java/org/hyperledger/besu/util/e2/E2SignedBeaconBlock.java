package org.hyperledger.besu.util.e2;

public class E2SignedBeaconBlock {
    private final byte[] signedBeaconBlock;
    private final int slot;

    public E2SignedBeaconBlock(final byte[] signedBeaconBlock, final int slot) {
        this.signedBeaconBlock = signedBeaconBlock;
        this.slot = slot;
    }

    public byte[] getSignedBeaconBlock() {
        return signedBeaconBlock;
    }

    public int getSlot() {
        return slot;
    }
}
