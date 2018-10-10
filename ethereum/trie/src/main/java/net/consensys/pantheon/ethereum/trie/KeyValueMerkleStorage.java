package net.consensys.pantheon.ethereum.trie;

import net.consensys.pantheon.services.kvstore.KeyValueStorage;
import net.consensys.pantheon.util.bytes.Bytes32;
import net.consensys.pantheon.util.bytes.BytesValue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class KeyValueMerkleStorage implements MerkleStorage {

  private final KeyValueStorage keyValueStorage;
  private final Map<Bytes32, BytesValue> pendingUpdates = new HashMap<>();

  public KeyValueMerkleStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  @Override
  public Optional<BytesValue> get(final Bytes32 hash) {
    final Optional<BytesValue> value =
        pendingUpdates.containsKey(hash)
            ? Optional.of(pendingUpdates.get(hash))
            : keyValueStorage.get(hash);
    return value;
  }

  @Override
  public void put(final Bytes32 hash, final BytesValue value) {
    pendingUpdates.put(hash, value);
  }

  @Override
  public void commit() {
    if (pendingUpdates.size() == 0) {
      // Nothing to do
      return;
    }
    final KeyValueStorage.Transaction kvTx = keyValueStorage.getStartTransaction();
    for (final Map.Entry<Bytes32, BytesValue> entry : pendingUpdates.entrySet()) {
      kvTx.put(entry.getKey(), entry.getValue());
    }
    kvTx.commit();

    pendingUpdates.clear();
  }

  @Override
  public void rollback() {
    pendingUpdates.clear();
  }
}
