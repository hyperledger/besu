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
 *
 */
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class DefaultWorldStateArchive implements WorldStateArchive {
  private final WorldStateStorage worldStateStorage;
  private final WorldStatePreimageStorage preimageStorage;
  private final WorldStateProofProvider worldStateProof;

  private static final Hash EMPTY_ROOT_HASH = Hash.wrap(MerkleTrie.EMPTY_TRIE_NODE_HASH);

  public DefaultWorldStateArchive(
      final WorldStateStorage worldStateStorage, final WorldStatePreimageStorage preimageStorage) {
    this.worldStateStorage = worldStateStorage;
    this.preimageStorage = preimageStorage;
    this.worldStateProof = new WorldStateProofProvider(worldStateStorage);
  }

  @Override
  public Optional<WorldState> get(final Hash rootHash, final Hash blockHash) {
    return getMutable(rootHash, blockHash).map(state -> state);
  }

  @Override
  public boolean isWorldStateAvailable(final Hash rootHash, final Hash blockHash) {
    return worldStateStorage.isWorldStateAvailable(rootHash, blockHash);
  }

  @Override
  public Optional<MutableWorldState> getMutable(
      final BlockHeader blockHeader, final boolean isPersistingState) {
    return getMutable(blockHeader.getStateRoot(), blockHeader.getHash());
  }

  @Override
  public Optional<MutableWorldState> getMutable(final Hash rootHash, final Hash blockHash) {
    if (!worldStateStorage.isWorldStateAvailable(rootHash, blockHash)) {
      return Optional.empty();
    }
    return Optional.of(new DefaultMutableWorldState(rootHash, worldStateStorage, preimageStorage));
  }

  @Override
  public MutableWorldState getMutable() {
    return getMutable(EMPTY_ROOT_HASH, null).get();
  }

  @Override
  public void resetArchiveStateTo(final BlockHeader blockHeader) {
    // ignore for forest
  }

  @Override
  public Optional<Bytes> getNodeData(final Hash hash) {
    // query by location is not supported, only query by content
    return worldStateStorage.getNodeData(null, hash);
  }

  public WorldStateStorage getWorldStateStorage() {
    return worldStateStorage;
  }

  @Override
  public <U> Optional<U> getAccountProof(
      final BlockHeader blockHeader,
      final Address accountAddress,
      final List<UInt256> accountStorageKeys,
      final Function<Optional<WorldStateProof>, ? extends Optional<U>> mapper) {
    return mapper.apply(
        worldStateProof.getAccountProof(
            blockHeader.getStateRoot(), accountAddress, accountStorageKeys));
  }

  @Override
  public void close() {
    // no op
  }
}
