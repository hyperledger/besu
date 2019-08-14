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
package tech.pegasys.pantheon.ethereum.worldstate;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.WorldState;
import tech.pegasys.pantheon.ethereum.proof.WorldStateProof;
import tech.pegasys.pantheon.ethereum.proof.WorldStateProofProvider;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

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
