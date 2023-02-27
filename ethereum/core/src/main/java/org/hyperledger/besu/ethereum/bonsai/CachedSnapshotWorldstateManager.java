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
package org.hyperledger.besu.ethereum.bonsai;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.infoLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedSnapshotWorldstateManager
    extends AbstractTrieLogManager<CachedSnapshotWorldstateManager.CachedWorldStateTuple>
    implements BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber {
  private static final Logger LOG = LoggerFactory.getLogger(CachedSnapshotWorldstateManager.class);
  private final BonsaiWorldStateProvider archive;

  CachedSnapshotWorldstateManager(
      final BonsaiWorldStateProvider archive,
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final Map<Bytes32, CachedSnapshotWorldstateManager.CachedWorldStateTuple>
          cachedWorldStatesByHash) {
    super(blockchain, worldStateStorage, maxLayersToLoad, cachedWorldStatesByHash);
    worldStateStorage.subscribe(this);
    this.archive = archive;
  }

  public CachedSnapshotWorldstateManager(
      final BonsaiWorldStateProvider archive,
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad) {
    this(archive, blockchain, worldStateStorage, maxLayersToLoad, new ConcurrentHashMap<>());
  }

  @Override
  public synchronized void addCachedLayer(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final BonsaiWorldState forWorldState) {
    infoLambda(
        LOG,
        "adding layered world state for block {}, state root hash {}",
        blockHeader::toLogString,
        worldStateRootHash::toShortHexString);
    if (forWorldState.worldStateStorage instanceof BonsaiSnapshotWorldStateKeyValueStorage) {
      cachedWorldStatesByHash.put(
          blockHeader.getHash(),
          new CachedWorldStateTuple(
              blockHeader,
              ((BonsaiSnapshotWorldStateKeyValueStorage) forWorldState.worldStateStorage).clone(),
              ((BonsaiWorldStateUpdateAccumulator) forWorldState.updater())));
    } else {
      // TODO: if it isn't a snapshot, this SHOULD be the one and only persisted worldstate.  in
      // theory we should
      //       be able to snap it and use an empty update accumulator here.
      cachedWorldStatesByHash.put(
          blockHeader.getHash(),
          new CachedWorldStateTuple(
              blockHeader,
              new BonsaiSnapshotWorldStateKeyValueStorage(forWorldState.worldStateStorage),
              (BonsaiWorldStateUpdateAccumulator) forWorldState.updater()));
    }
    scrubCachedLayers(blockHeader.getNumber());
  }

  @Override
  public Optional<BonsaiWorldState> getWorldState(final Hash blockHash) {
    if (cachedWorldStatesByHash.containsKey(blockHash)) {
      return Optional.ofNullable(cachedWorldStatesByHash.get(blockHash))
          .map(
              cached ->
                  new BonsaiWorldState(archive, cached.worldStateStorage, false, cached.updater));
    }
    return Optional.empty();
  }

  @Override
  public BonsaiWorldState getHeadWorldState() {
    return new BonsaiWorldState(
        archive, new BonsaiSnapshotWorldStateKeyValueStorage(rootWorldStateStorage));
  }

  @Override
  public void onClearStorage() {
    this.cachedWorldStatesByHash.clear();
  }

  @Override
  public void onClearFlatDatabaseStorage() {
    this.cachedWorldStatesByHash.clear();
  }

  @Override
  public void onClearTrieLog() {
    this.cachedWorldStatesByHash.clear();
  }

  @Override
  public void onCloseStorage() {
    this.cachedWorldStatesByHash.clear();
  }

  static class CachedWorldStateTuple implements CacheLayer {
    private final BlockHeader blockHeader;
    private final BonsaiSnapshotWorldStateKeyValueStorage worldStateStorage;
    private final BonsaiWorldStateUpdateAccumulator updater;

    public CachedWorldStateTuple(
        final BlockHeader blockHeader,
        final BonsaiSnapshotWorldStateKeyValueStorage worldStateStorage,
        final BonsaiWorldStateUpdateAccumulator updater) {
      this.blockHeader = blockHeader;
      this.worldStateStorage = worldStateStorage;
      this.updater = updater;
    }

    @Override
    public long getBlockNumber() {
      // TODO: need to plumb this somewhere, prob from header
      return blockHeader.getNumber();
    }

    @Override
    public Hash getWorldStateBlockHash() {
      return blockHeader.getBlockHash();
    }

    @Override
    public void close() {
      try {
        worldStateStorage.close();
      } catch (Exception ex) {
        LOG.warn("Failed to closed cached snapshot worldstate", ex);
      }
    }
  }
}
