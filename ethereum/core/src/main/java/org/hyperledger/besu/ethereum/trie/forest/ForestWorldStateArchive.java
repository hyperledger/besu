/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.trie.forest;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.proof.WorldStateProof;
import org.hyperledger.besu.ethereum.proof.WorldStateProofProvider;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.forest.storage.ForestWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.forest.worldview.ForestMutableWorldState;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.WorldStatePreimageStorage;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorageCoordinator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.WorldState;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

public class ForestWorldStateArchive implements WorldStateArchive {
  private final ForestWorldStateKeyValueStorage worldStateKeyValueStorage;
  private final WorldStatePreimageStorage preimageStorage;
  private final WorldStateProofProvider worldStateProof;
  private final EvmConfiguration evmConfiguration;

  private static final Hash EMPTY_ROOT_HASH = Hash.wrap(MerkleTrie.EMPTY_TRIE_NODE_HASH);

  public ForestWorldStateArchive(
      final WorldStateStorageCoordinator worldStateStorageCoordinator,
      final WorldStatePreimageStorage preimageStorage,
      final EvmConfiguration evmConfiguration) {
    this.worldStateKeyValueStorage =
        worldStateStorageCoordinator.getStrategy(ForestWorldStateKeyValueStorage.class);
    this.preimageStorage = preimageStorage;
    this.worldStateProof = new WorldStateProofProvider(worldStateStorageCoordinator);
    this.evmConfiguration = evmConfiguration;
  }

  @Override
  public Optional<WorldState> get(final Hash rootHash, final Hash blockHash) {
    return getMutable(rootHash, blockHash).map(state -> state);
  }

  @Override
  public boolean isWorldStateAvailable(final Hash rootHash, final Hash blockHash) {
    return worldStateKeyValueStorage.isWorldStateAvailable(rootHash);
  }

  @Override
  public Optional<MutableWorldState> getMutable(
      final BlockHeader blockHeader, final boolean isPersistingState) {
    return getMutable(blockHeader.getStateRoot(), blockHeader.getHash());
  }

  @Override
  public Optional<MutableWorldState> getMutable(final Hash rootHash, final Hash blockHash) {
    if (!worldStateKeyValueStorage.isWorldStateAvailable(rootHash)) {
      return Optional.empty();
    }
    return Optional.of(
        new ForestMutableWorldState(
            rootHash, worldStateKeyValueStorage, preimageStorage, evmConfiguration));
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
    return worldStateKeyValueStorage.getNodeData(hash);
  }

  public ForestWorldStateKeyValueStorage getWorldStateStorage() {
    return worldStateKeyValueStorage;
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
  public void heal(final Optional<Address> maybeAccountToRepair, final Bytes location) {
    // no heal needed for Forest
  }

  @Override
  public void close() {
    // no op
  }
}
