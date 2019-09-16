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
package org.hyperledger.besu.ethereum.storage.keyvalue;

import org.hyperledger.besu.ethereum.chain.BlockchainStorage;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.privacy.PrivateStateKeyValueStorage;
import org.hyperledger.besu.ethereum.privacy.PrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionKeyValueStorage;
import org.hyperledger.besu.ethereum.privacy.PrivateTransactionStorage;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;

import java.io.IOException;

public class KeyValueStorageProvider implements StorageProvider {

  private final KeyValueStorage blockchainStorage;
  private final KeyValueStorage worldStateStorage;
  private final KeyValueStorage worldStatePreimageStorage;
  private final KeyValueStorage privateTransactionStorage;
  private final KeyValueStorage privateStateStorage;
  private final KeyValueStorage pruningStorage;
  private final boolean isWorldStateIterable;

  public KeyValueStorageProvider(
      final KeyValueStorage blockchainStorage,
      final KeyValueStorage worldStateStorage,
      final KeyValueStorage worldStatePreimageStorage,
      final KeyValueStorage privateTransactionStorage,
      final KeyValueStorage privateStateStorage,
      final KeyValueStorage pruningStorage,
      final boolean isWorldStateIterable) {
    this.blockchainStorage = blockchainStorage;
    this.worldStateStorage = worldStateStorage;
    this.worldStatePreimageStorage = worldStatePreimageStorage;
    this.privateTransactionStorage = privateTransactionStorage;
    this.privateStateStorage = privateStateStorage;
    this.pruningStorage = pruningStorage;
    this.isWorldStateIterable = isWorldStateIterable;
  }

  @Override
  public BlockchainStorage createBlockchainStorage(final ProtocolSchedule<?> protocolSchedule) {
    return new KeyValueStoragePrefixedKeyBlockchainStorage(
        blockchainStorage, ScheduleBasedBlockHeaderFunctions.create(protocolSchedule));
  }

  @Override
  public WorldStateStorage createWorldStateStorage() {
    return new WorldStateKeyValueStorage(worldStateStorage);
  }

  @Override
  public WorldStatePreimageStorage createWorldStatePreimageStorage() {
    return new WorldStatePreimageKeyValueStorage(worldStatePreimageStorage);
  }

  @Override
  public PrivateTransactionStorage createPrivateTransactionStorage() {
    return new PrivateTransactionKeyValueStorage(privateTransactionStorage);
  }

  @Override
  public PrivateStateStorage createPrivateStateStorage() {
    return new PrivateStateKeyValueStorage(privateStateStorage);
  }

  @Override
  public KeyValueStorage createPruningStorage() {
    return pruningStorage;
  }

  @Override
  public boolean isWorldStateIterable() {
    return isWorldStateIterable;
  }

  @Override
  public void close() throws IOException {
    blockchainStorage.close();
    worldStateStorage.close();
    privateTransactionStorage.close();
    privateStateStorage.close();
    pruningStorage.close();
  }
}
