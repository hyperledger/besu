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
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.util.bytes.BytesValue;
import org.hyperledger.besu.util.uint.UInt256;

import java.util.List;
import java.util.Optional;

public class WorldStateArchive {
  private final WorldStateStorage worldStateStorage;
  private final WorldStatePreimageStorage preimageStorage;
  private final WorldStateProofProvider worldStateProof;

  private static final Hash EMPTY_ROOT_HASH = Hash.wrap(MerklePatriciaTrie.EMPTY_TRIE_NODE_HASH);

  public WorldStateArchive(
      final WorldStateStorage worldStateStorage, final WorldStatePreimageStorage preimageStorage) {
    this.worldStateStorage = worldStateStorage;
    this.preimageStorage = preimageStorage;
    this.worldStateProof = new WorldStateProofProvider(worldStateStorage);
  }

  public Optional<WorldState> get(final Hash rootHash) {
    return getMutable(rootHash).map(state -> state);
  }

  public boolean isWorldStateAvailable(final Hash rootHash) {
    return worldStateStorage.isWorldStateAvailable(rootHash);
  }

  public Optional<MutableWorldState> getMutable(final Hash rootHash) {
    if (!worldStateStorage.isWorldStateAvailable(rootHash)) {
      return Optional.empty();
    }
    return Optional.of(new DefaultMutableWorldState(rootHash, worldStateStorage, preimageStorage));
  }

  public WorldState get() {
    return get(EMPTY_ROOT_HASH).get();
  }

  public MutableWorldState getMutable() {
    return getMutable(EMPTY_ROOT_HASH).get();
  }

  public Optional<BytesValue> getNodeData(final Hash hash) {
    return worldStateStorage.getNodeData(hash);
  }

  public WorldStateStorage getWorldStateStorage() {
    return worldStateStorage;
  }

  public Optional<WorldStateProof> getAccountProof(
      final Hash worldStateRoot,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys) {
    return worldStateProof.getAccountProof(worldStateRoot, accountAddress, accountStorageKeys);
  }
}
