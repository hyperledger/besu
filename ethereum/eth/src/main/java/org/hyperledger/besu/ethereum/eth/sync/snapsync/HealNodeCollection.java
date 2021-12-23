package org.hyperledger.besu.ethereum.eth.sync.snapsync;

import org.hyperledger.besu.datatypes.Hash;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes;
import org.jetbrains.annotations.NotNull;

public class HealNodeCollection extends ConcurrentHashMap<Bytes, Bytes> {

  private static final int MAX_SIZE = 1_000_000;

  @Override
  public Bytes put(@NotNull final Bytes key, @NotNull final Bytes value) {
    if (size() > MAX_SIZE) {
      return null;
    }
    return super.put(key, value);
  }

  public Optional<Bytes> get(@NotNull final Bytes key, @NotNull final Bytes hash) {
    return Optional.ofNullable(super.get(key))
        .filter(bytes -> Hash.hash(bytes).compareTo(hash) == 0);
  }
}
