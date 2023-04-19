package org.hyperledger.besu.ethereum.bonsai.worldview;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Objects;
import java.util.Optional;

import org.apache.tuweni.units.bigints.UInt256;
import org.jetbrains.annotations.NotNull;

public record StorageSlotKey(Hash slotHash, Optional<UInt256> slotKey)
    implements Comparable<org.hyperledger.besu.ethereum.bonsai.worldview.StorageSlotKey> {

  public StorageSlotKey(final UInt256 slotKey) {
    this(Hash.hash(slotKey), Optional.of(slotKey));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StorageSlotKey that = (StorageSlotKey) o;
    return Objects.equals(slotHash, that.slotHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(slotHash.hashCode());
  }

  @Override
  public String toString() {
    return String.format(
        "StorageSlotKey{slotHash=%s, slotKey=%s}",
        slotHash, slotKey.map(UInt256::toString).orElse("null"));
  }

  @Override
  public int compareTo(
      @NotNull final org.hyperledger.besu.ethereum.bonsai.worldview.StorageSlotKey other) {
    return this.slotHash.compareTo(other.slotHash);
  }
}
