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
 *
 */

package org.hyperledger.besu.ethereum.bonsai;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;

public class BonsaiWorldStateArchive implements WorldStateArchive {

  private final BonsaiPersistedWorldState persistedState;
  private final Map<Bytes32, BonsaiLayeredWorldState> layeredWorldStates;

  public BonsaiWorldStateArchive(final StorageProvider provider) {
    persistedState =
        new BonsaiPersistedWorldState(
            this,
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE),
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.CODE_STORAGE),
            provider.getStorageBySegmentIdentifier(
                KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE),
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE),
            provider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.TRIE_LOG_STORAGE));
    layeredWorldStates = new HashMap<>();
  }

  @Override
  public Optional<WorldState> get(final Hash rootHash) {
    if (layeredWorldStates.containsKey(rootHash)) {
      return Optional.of(layeredWorldStates.get(rootHash));
    } else if (rootHash.equals(persistedState.rootHash())) {
      return Optional.of(persistedState);
    } else {
      return Optional.empty();
    }
  }

  public void addLayeredWorldState(final BonsaiLayeredWorldState worldState) {
    layeredWorldStates.put(worldState.rootHash(), worldState);
  }

  @Override
  public boolean isWorldStateAvailable(final Hash rootHash) {
    return layeredWorldStates.containsKey(rootHash)
        || persistedState.rootHash().equals(rootHash) /* || check disk storage */;
  }

  @Override
  public Optional<MutableWorldState> getMutable(final Hash rootHash) {
    if (rootHash.equals(persistedState.rootHash())) {
      return Optional.of(persistedState);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public MutableWorldState getMutable() {
    return persistedState;
  }

  @Override
  public Optional<Bytes> getNodeData(final Hash hash) {
    return Optional.empty();
  }

  @Override
  public Optional<WorldStateProof> getAccountProof(
      final Hash worldStateRoot,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys) {
    return Optional.empty();
  }
}
