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
package org.hyperledger.besu.ethereum.goquorum;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class GoQuorumPrivateKeyValueStorage implements GoQuorumPrivateStorage {

  private static final Bytes PRIVATE_STATEROOT_SUFFIX = Bytes.of("PRIVSTATEROOT".getBytes(UTF_8));

  private final KeyValueStorage keyValueStorage;

  public GoQuorumPrivateKeyValueStorage(final KeyValueStorage keyValueStorage) {
    this.keyValueStorage = keyValueStorage;
  }

  @Override
  public Optional<Hash> getPrivateStateRootHash(final Hash publicStateRootHash) {
    return get(publicStateRootHash, PRIVATE_STATEROOT_SUFFIX).map(Bytes32::wrap).map(Hash::wrap);
  }

  private Optional<Bytes> get(final Bytes key, final Bytes keySuffix) {
    return keyValueStorage.get(Bytes.concatenate(key, keySuffix).toArrayUnsafe()).map(Bytes::wrap);
  }

  @Override
  public GoQuorumPrivateStorage.Updater updater() {
    return new Updater(keyValueStorage.startTransaction());
  }

  public static class Updater implements GoQuorumPrivateStorage.Updater {

    private final KeyValueStorageTransaction transaction;

    private Updater(final KeyValueStorageTransaction transaction) {
      this.transaction = transaction;
    }

    @Override
    public GoQuorumPrivateStorage.Updater putPrivateStateRootHashMapping(
        final Hash publicStateRootHash, final Hash privateStateRootHash) {
      set(publicStateRootHash, PRIVATE_STATEROOT_SUFFIX, privateStateRootHash);
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

    private void set(final Bytes key, final Bytes keySuffix, final Bytes value) {
      transaction.put(Bytes.concatenate(key, keySuffix).toArrayUnsafe(), value.toArrayUnsafe());
    }
  }
}
