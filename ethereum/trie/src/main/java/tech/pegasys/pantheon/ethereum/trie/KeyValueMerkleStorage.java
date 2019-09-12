/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.trie;

import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorage;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorageTransaction;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

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
            : keyValueStorage.get(hash.getArrayUnsafe()).map(BytesValue::wrap);
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
    final KeyValueStorageTransaction kvTx = keyValueStorage.startTransaction();
    for (final Map.Entry<Bytes32, BytesValue> entry : pendingUpdates.entrySet()) {
      kvTx.put(entry.getKey().getArrayUnsafe(), entry.getValue().getArrayUnsafe());
    }
    kvTx.commit();

    pendingUpdates.clear();
  }

  @Override
  public void rollback() {
    pendingUpdates.clear();
  }
}
