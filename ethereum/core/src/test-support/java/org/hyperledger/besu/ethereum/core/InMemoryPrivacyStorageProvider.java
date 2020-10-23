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
package org.hyperledger.besu.ethereum.core;

import org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateKeyValueStorage;
import org.hyperledger.besu.ethereum.privacy.storage.LegacyPrivateStateStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateKeyValueStorage;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateStateStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DefaultMutableWorldState;
import org.hyperledger.besu.ethereum.worldstate.DefaultWorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

public class InMemoryPrivacyStorageProvider implements PrivacyStorageProvider {

  public static WorldStateArchive createInMemoryWorldStateArchive() {
    return new DefaultWorldStateArchive(
        new WorldStateKeyValueStorage(new InMemoryKeyValueStorage()),
        new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage()));
  }

  public static MutableWorldState createInMemoryWorldState() {
    final InMemoryPrivacyStorageProvider provider = new InMemoryPrivacyStorageProvider();
    return new DefaultMutableWorldState(
        provider.createWorldStateStorage(), provider.createWorldStatePreimageStorage());
  }

  @Override
  public WorldStateStorage createWorldStateStorage() {
    return new WorldStateKeyValueStorage(new InMemoryKeyValueStorage());
  }

  @Override
  public WorldStatePreimageStorage createWorldStatePreimageStorage() {
    return new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage());
  }

  @Override
  public PrivateStateStorage createPrivateStateStorage() {
    return new PrivateStateKeyValueStorage(new InMemoryKeyValueStorage());
  }

  @Override
  public LegacyPrivateStateStorage createLegacyPrivateStateStorage() {
    return new LegacyPrivateStateKeyValueStorage(new InMemoryKeyValueStorage());
  }

  @Override
  public int getFactoryVersion() {
    return 1;
  }

  @Override
  public void close() {}
}
