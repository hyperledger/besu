/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.storage.keyvalue;

import tech.pegasys.pantheon.ethereum.worldstate.WorldStatePreimageStorage;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage;
import tech.pegasys.pantheon.services.kvstore.KeyValueStorage.Transaction;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.util.Optional;

public class WorldStatePreimageKeyValueStorage implements WorldStatePreimageStorage {
  private final KeyValueStorage keyValueStorage;

  public WorldStatePreimageKeyValueStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  @Override
  public Optional<UInt256> getStorageTrieKeyPreimage(final Bytes32 trieKey) {
    return keyValueStorage.get(trieKey).map(Bytes32::wrap).map(UInt256::wrap);
  }

  @Override
  public Updater updater() {
    return new Updater(keyValueStorage.startTransaction());
  }

  public static class Updater implements WorldStatePreimageStorage.Updater {
    private final KeyValueStorage.Transaction transaction;

    public Updater(final Transaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public WorldStatePreimageStorage.Updater putStorageTrieKeyPreimage(
        final Bytes32 trieKey, final UInt256 preimage) {
      transaction.put(trieKey, preimage.getBytes());
      return this;
    }

    @Override
    public void commit() {
      transaction.commit();
    }

    @Override
    public void rollback() {
      transaction.rollback();
    }
  }
}
