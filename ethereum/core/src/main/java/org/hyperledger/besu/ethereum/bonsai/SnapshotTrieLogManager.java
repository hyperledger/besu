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
package org.hyperledger.besu.ethereum.bonsai;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapshotTrieLogManager
    extends AbstractTrieLogManager<SnapshotTrieLogManager.CachedSnapshotWorldState> {
  private static final Logger LOG = LoggerFactory.getLogger(SnapshotTrieLogManager.class);

  public SnapshotTrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad) {
    this(blockchain, worldStateStorage, maxLayersToLoad, new HashMap<>());
  }

  public SnapshotTrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final Map<Bytes32, CachedSnapshotWorldState> cachedWorldStatesByHash) {
    super(blockchain, worldStateStorage, maxLayersToLoad, cachedWorldStatesByHash);
  }

  @Override
  public void addCachedLayer(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog,
      final BonsaiWorldStateArchive worldStateArchive) {

    debugLambda(
        LOG,
        "adding snapshot world state for block {}, state root hash {}",
        blockHeader::toLogString,
        worldStateRootHash::toHexString);
    cachedWorldStatesByHash.put(
        blockHeader.getHash(),
        new CachedSnapshotWorldState(
            () ->
                worldStateArchive
                    .getMutableSnapshot(blockHeader.getHash())
                    .map(BonsaiSnapshotWorldState.class::cast)
                    .orElse(null),
            trieLog,
            blockHeader.getNumber()));
  }

  @Override
  public Optional<MutableWorldState> getBonsaiCachedWorldState(final Hash blockHash) {
    if (cachedWorldStatesByHash.containsKey(blockHash)) {
      return Optional.ofNullable(cachedWorldStatesByHash.get(blockHash))
          .map(CachedSnapshotWorldState::getMutableWorldState)
          .map(MutableWorldState::copy);
    }
    return Optional.empty();
  }

  @Override
  public void updateCachedLayers(final Hash blockParentHash, final Hash blockHash) {
    // fetch the snapshot supplier as soon as its block has been added:
    Optional.ofNullable(cachedWorldStatesByHash.get(blockHash))
        .ifPresent(CachedSnapshotWorldState::getMutableWorldState);
  }

  public static class CachedSnapshotWorldState implements CachedWorldState {

    final Supplier<BonsaiSnapshotWorldState> snapshot;
    final TrieLogLayer trieLog;
    final long height;

    public CachedSnapshotWorldState(
        final Supplier<BonsaiSnapshotWorldState> snapshotSupplier,
        final TrieLogLayer trieLog,
        final long height) {
      this.snapshot = Suppliers.memoize(snapshotSupplier::get);
      this.trieLog = trieLog;
      this.height = height;
    }

    @Override
    public long getHeight() {
      return height;
    }

    @Override
    public TrieLogLayer getTrieLog() {
      return trieLog;
    }

    @Override
    public MutableWorldState getMutableWorldState() {
      return snapshot.get();
    }
  }
}
