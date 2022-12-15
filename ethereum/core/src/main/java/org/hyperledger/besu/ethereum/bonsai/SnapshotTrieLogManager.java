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
import org.hyperledger.besu.ethereum.bonsai.BonsaiPersistedWorldState.BonsaiWorldStateSubscriber;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnapshotTrieLogManager extends AbstractTrieLogManager<BonsaiSnapshotWorldState>
    implements BonsaiStorageSubscriber {
  private static final Logger LOG = LoggerFactory.getLogger(SnapshotTrieLogManager.class);

  public SnapshotTrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad) {
    this(blockchain, worldStateStorage, maxLayersToLoad, new ConcurrentHashMap<>());
  }

  SnapshotTrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final Map<Bytes32, CachedWorldState<BonsaiSnapshotWorldState>> cachedWorldStatesByHash) {
    super(blockchain, worldStateStorage, maxLayersToLoad, cachedWorldStatesByHash);
    worldStateStorage.subscribe(this);
  }

  @Override
  protected void addCachedLayer(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog,
      final BonsaiWorldStateArchive worldStateArchive,
      final BonsaiPersistedWorldState worldState) {

    debugLambda(
        LOG,
        "adding snapshot world state for block {}, state root hash {}",
        blockHeader::toLogString,
        worldStateRootHash::toShortHexString);

    // TODO: add a generic param so we don't have to cast:
    BonsaiSnapshotWorldState snapshotWorldState;
    if (worldState instanceof BonsaiSnapshotWorldState) {
      snapshotWorldState = (BonsaiSnapshotWorldState) worldState;
    } else {
      snapshotWorldState = BonsaiSnapshotWorldState.create(worldStateArchive, worldStateStorage);
    }
    snapshotWorldState.getWorldStateStorage().subscribe(this);

    cachedWorldStatesByHash.put(
        blockHeader.getHash(),
        CachedSnapshotWorldState.create(snapshotWorldState, trieLog, blockHeader.getNumber()));
  }

  @Override
  public void updateCachedLayers(final Hash blockParentHash, final Hash blockHash) {
    // no-op.
  }

  @Override
  public synchronized Optional<MutableWorldState> getBonsaiCachedWorldState(final Hash blockHash) {
    if (cachedWorldStatesByHash.containsKey(blockHash)) {
      return Optional.ofNullable(cachedWorldStatesByHash.get(blockHash))
          .map(CachedWorldState::getMutableWorldState)
          .map(MutableWorldState::copy);
    }
    return Optional.empty();
  }

  @Override
  public synchronized void onClearStorage() {
    dropArchive();
  }

  @Override
  public synchronized void onClearFlatDatabaseStorage() {
    dropArchive();
  }

  private void dropArchive() {
    // drop all cached snapshot worldstates, they are unsafe when the db has been truncated
    LOG.info("Key-value storage truncated, dropping cached worldstates");
    cachedWorldStatesByHash.clear();
  }

  public static class CachedSnapshotWorldState
      implements CachedWorldState<BonsaiSnapshotWorldState>, BonsaiWorldStateSubscriber {

    final BonsaiSnapshotWorldState snapshot;
    final TrieLogLayer trieLog;
    final long height;
    final AtomicLong snapshotSubscriberId = new AtomicLong();
    final AtomicBoolean isClosed = new AtomicBoolean(false);

    private CachedSnapshotWorldState(
        final BonsaiSnapshotWorldState snapshot, final TrieLogLayer trieLog, final long height) {
      this.snapshot = snapshot;
      this.trieLog = trieLog;
      this.height = height;
    }

    public static CachedSnapshotWorldState create(
        final BonsaiSnapshotWorldState snapshot, final TrieLogLayer trieLog, final long height) {
      return new CachedSnapshotWorldState(snapshot, trieLog, height)
              .subscribeToSnapshot();
    }

    private CachedSnapshotWorldState subscribeToSnapshot() {
      snapshotSubscriberId.set(snapshot.subscribe(this));
      return this;
    }

    @Override
    public synchronized void onCloseWorldState() {
      if (!isClosed.compareAndExchange(false, true)) {
        snapshot.unSubscribe(snapshotSubscriberId.get());
      }
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
    public synchronized BonsaiSnapshotWorldState getMutableWorldState() {
      if (isClosed.get()) {
        return null;
      }
      return snapshot;
    }
  }
}
