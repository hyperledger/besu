/*
 * Copyright Hyperledger Besu Contributors.
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
package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.forest.storage.ForestWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.forest.worldview.ForestMutableWorldState;
import org.hyperledger.besu.ethereum.storage.keyvalue.WorldStatePreimageKeyValueStorage;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;

public class DefaultReferenceTestWorldState extends ForestMutableWorldState
    implements ReferenceTestWorldState {

  DefaultReferenceTestWorldState() {
    super(
        new ForestWorldStateKeyValueStorage(new InMemoryKeyValueStorage()),
        new WorldStatePreimageKeyValueStorage(new InMemoryKeyValueStorage()),
        EvmConfiguration.DEFAULT);
  }

  public DefaultReferenceTestWorldState(final WorldState worldState) {
    super(worldState, EvmConfiguration.DEFAULT);
  }

  @Override
  public ReferenceTestWorldState copy() {
    return new DefaultReferenceTestWorldState(this);
  }

  @JsonCreator
  public static ReferenceTestWorldState create(final Map<String, AccountMock> accounts) {
    final ReferenceTestWorldState worldState = new DefaultReferenceTestWorldState();
    final WorldUpdater updater = worldState.updater();

    for (final Map.Entry<String, AccountMock> entry : accounts.entrySet()) {
      ReferenceTestWorldState.insertAccount(
          updater, Address.fromHexString(entry.getKey()), entry.getValue());
    }

    updater.commit();
    return worldState;
  }
}
