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
package org.hyperledger.besu.ethereum.privacy.storage.keyvalue;

import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;

import java.io.IOException;

public class PrivacyKeyValueStorageProvider implements PrivacyStorageProvider {

  private final KeyValueStorage privateWorldStateStorage;
  private final KeyValueStorage privateWorldStatePreimageStorage;
  private final KeyValueStorage privateStateStorage;

  private int factoryVersion;

  public PrivacyKeyValueStorageProvider(
      final KeyValueStorage privateWorldStateStorage,
      final KeyValueStorage privateWorldStatePreimageStorage,
      final KeyValueStorage privateStateStorage,
      final int factoryVersion) {
    this.privateWorldStateStorage = privateWorldStateStorage;
    this.privateWorldStatePreimageStorage = privateWorldStatePreimageStorage;
    this.privateStateStorage = privateStateStorage;
    this.factoryVersion = factoryVersion;
  }

  @Override
  public WorldStateStorage createWorldStateStorage() {
    return new WorldStateKeyValueStorage(privateWorldStateStorage);
  }

  @Override
  public PrivateStateStorage createPrivateStateStorage() {
    return new PrivateStateKeyValueStorage(privateStateStorage);
  }

  @Override
  public WorldStatePreimageStorage createWorldStatePreimageStorage() {
    return new WorldStatePreimageKeyValueStorage(privateWorldStatePreimageStorage);
  }

  @Override
  public int getFactoryVersion() {
    return factoryVersion;
  }

  @Override
  public void close() throws IOException {
    privateWorldStateStorage.close();
    privateWorldStatePreimageStorage.close();
    privateStateStorage.close();
  }
}
