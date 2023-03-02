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
package org.hyperledger.besu.ethereum.bonsai.trielog;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.infoLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiSnapshotWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage.BonsaiStorageSubscriber;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateLayerStorage;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedWorldStorageManager extends AbstractTrieLogManager
    implements BonsaiStorageSubscriber {
  private static final Logger LOG = LoggerFactory.getLogger(CachedWorldStorageManager.class);
  private final BonsaiWorldStateProvider archive;

  CachedWorldStorageManager(
      final BonsaiWorldStateProvider archive,
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final Map<Bytes32, CachedBonsaiWorldView> cachedWorldStatesByHash) {
    super(blockchain, worldStateStorage, maxLayersToLoad, cachedWorldStatesByHash);
    worldStateStorage.subscribe(this);
    this.archive = archive;
  }

  public CachedWorldStorageManager(
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

    if (forWorldState.isPersisted()) {
      // if this is the persisted worldstate, add a snapshot of it to the cache
      cachedWorldStatesByHash.put(
          blockHeader.getHash(),
          new CachedBonsaiWorldView(
              blockHeader,
              new BonsaiSnapshotWorldStateKeyValueStorage(forWorldState.worldStateStorage)));
    } else {
      // otherwise, add the layer to the cache
      cachedWorldStatesByHash.put(
          blockHeader.getHash(),
          new CachedBonsaiWorldView(blockHeader, forWorldState.getWorldStateStorage()));
    }
    scrubCachedLayers(blockHeader.getNumber());
  }

  /**
   * This method when called with a persisted world state will replace a cached layered worldstate
   * with a snapshot of the persisted storage.
   *
   * @param blockHash block hash of the world state
   * @param persistedState the persisted world state
   */
  @Override
  public void updateCachedLayer(final Hash blockHash, final BonsaiWorldState persistedState) {
    if (persistedState.isPersisted()) {
      Optional.ofNullable(this.cachedWorldStatesByHash.get(blockHash))
          .filter(storage -> storage.getWorldstateStorage() instanceof BonsaiWorldStateLayerStorage)
          .ifPresent(
              cachedWorldState ->
                  cachedWorldState.updateWorldStateStorage(
                      new BonsaiSnapshotWorldStateKeyValueStorage(
                          persistedState.worldStateStorage)));
    }
  }

  @Override
  public Optional<BonsaiWorldState> getWorldState(final Hash blockHash) {
    if (cachedWorldStatesByHash.containsKey(blockHash)) {
      // return a new worldstate using worldstate storage and an isolated copy of the updater
      return Optional.ofNullable(cachedWorldStatesByHash.get(blockHash))
          .map(
              cached ->
                  new BonsaiWorldState(
                      archive, new BonsaiWorldStateLayerStorage(cached.getWorldstateStorage())));
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
}
