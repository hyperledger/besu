/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.privacy;

import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.Optional;

public class PrivateStateKeyValueStorage implements PrivateStateStorage {

  private final KeyValueStorage keyValueStorage;

  public PrivateStateKeyValueStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  @Override
  public Optional<Hash> getPrivateAccountState(final BytesValue privacyId) {
    final byte[] id = privacyId.getArrayUnsafe();

    if (keyValueStorage.get(id).isPresent()) {
      return Optional.of(Hash.wrap(Bytes32.wrap(keyValueStorage.get(id).get())));
    } else {
      return Optional.empty();
    }
  }

  @Override
  public boolean isWorldStateAvailable(final Bytes32 rootHash) {
    return false;
  }

  @Override
  public PrivateStateStorage.Updater updater() {
    return new PrivateStateKeyValueStorage.Updater(keyValueStorage.startTransaction());
  }

  public static class Updater implements PrivateStateStorage.Updater {

    private final KeyValueStorageTransaction transaction;

    private Updater(final KeyValueStorageTransaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public PrivateStateStorage.Updater putPrivateAccountState(
        final BytesValue privacyId, final Hash privateStateHash) {
      transaction.put(privacyId.getArrayUnsafe(), privateStateHash.extractArray());
      return this;
    }

    @Override
    public void commit() {
      transaction.commit();
    }
  }
}
