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
package tech.pegasys.pantheon.ethereum.storage;

import tech.pegasys.pantheon.ethereum.chain.BlockchainStorage;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.privacy.PrivateStateStorage;
import tech.pegasys.pantheon.ethereum.privacy.PrivateTransactionStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStatePreimageStorage;
import tech.pegasys.pantheon.ethereum.worldstate.WorldStateStorage;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorage;

import java.io.Closeable;

public interface StorageProvider extends Closeable {

  BlockchainStorage createBlockchainStorage(ProtocolSchedule<?> protocolSchedule);

  WorldStateStorage createWorldStateStorage();

  WorldStatePreimageStorage createWorldStatePreimageStorage();

  PrivateTransactionStorage createPrivateTransactionStorage();

  PrivateStateStorage createPrivateStateStorage();

  KeyValueStorage createPruningStorage();

  boolean isWorldStateIterable();
}
