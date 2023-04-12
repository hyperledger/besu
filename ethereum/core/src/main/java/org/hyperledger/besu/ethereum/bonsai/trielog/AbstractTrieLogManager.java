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
package org.hyperledger.besu.ethereum.bonsai.trielog;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.cache.CachedBonsaiWorldView;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage.BonsaiUpdater;
import org.hyperledger.besu.ethereum.bonsai.trielog.TrieLogAddedEvent.TrieLogAddedObserver;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.util.Subscribers;

import java.util.Map;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTrieLogManager implements TrieLogManager {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractTrieLogManager.class);
  public static final long RETAINED_LAYERS = 512; // at least 256 + typical rollbacks
  protected final Blockchain blockchain;
  protected final BonsaiWorldStateKeyValueStorage rootWorldStateStorage;

  protected final Map<Bytes32, CachedBonsaiWorldView> cachedWorldStatesByHash;
  protected final long maxLayersToLoad;
  private final Subscribers<TrieLogAddedObserver> trieLogAddedObservers = Subscribers.create();

  // TODO plumb factory from plugin service:
  TrieLogFactory<TrieLogLayer> trieLogFactory = new TrieLogFactoryImpl();

  protected AbstractTrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final Map<Bytes32, CachedBonsaiWorldView> cachedWorldStatesByHash) {
    this.blockchain = blockchain;
    this.rootWorldStateStorage = worldStateStorage;
    this.cachedWorldStatesByHash = cachedWorldStatesByHash;
    this.maxLayersToLoad = maxLayersToLoad;
  }

  @Override
  public synchronized void saveTrieLog(
      final BonsaiWorldStateUpdateAccumulator localUpdater,
      final Hash forWorldStateRootHash,
      final BlockHeader forBlockHeader,
      final BonsaiWorldState forWorldState) {
    // do not overwrite a trielog layer that already exists in the database.
    // if it's only in memory we need to save it
    // for example, in case of reorg we don't replace a trielog layer
    if (rootWorldStateStorage.getTrieLog(forBlockHeader.getHash()).isEmpty()) {
      final BonsaiUpdater stateUpdater = forWorldState.getWorldStateStorage().updater();
      boolean success = false;
      try {
        final TrieLogLayer trieLog = prepareTrieLog(forBlockHeader, localUpdater);
        persistTrieLog(forBlockHeader, forWorldStateRootHash, trieLog, stateUpdater);

        // notify trie log added observers, synchronously
        trieLogAddedObservers.forEach(o -> o.onTrieLogAdded(new TrieLogAddedEvent(trieLog)));

        success = true;
      } finally {
        if (success) {
          stateUpdater.commit();
        } else {
          stateUpdater.rollback();
        }
      }
    }
  }

  @VisibleForTesting
  TrieLogLayer prepareTrieLog(
      final BlockHeader blockHeader, final BonsaiWorldStateUpdateAccumulator localUpdater) {
    LOG.atDebug()
        .setMessage("Adding layered world state for {}")
        .addArgument(blockHeader::toLogString)
        .log();
    final TrieLogLayer trieLog = localUpdater.generateTrieLog(blockHeader.getBlockHash());
    trieLog.freeze();
    return trieLog;
  }

  public synchronized void scrubCachedLayers(final long newMaxHeight) {
    if (cachedWorldStatesByHash.size() > RETAINED_LAYERS) {
      final long waterline = newMaxHeight - RETAINED_LAYERS;
      cachedWorldStatesByHash.values().stream()
          .filter(layer -> layer.getBlockNumber() < waterline)
          .toList()
          .forEach(
              layer -> {
                cachedWorldStatesByHash.remove(layer.getBlockHash());
                layer.close();
              });
    }
  }

  private void persistTrieLog(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog,
      final BonsaiUpdater stateUpdater) {
    LOG.atDebug()
        .setMessage("Persisting trie log for block hash {} and world state root {}")
        .addArgument(blockHeader::toLogString)
        .addArgument(worldStateRootHash::toHexString)
        .log();

    stateUpdater
        .getTrieLogStorageTransaction()
        .put(blockHeader.getHash().toArrayUnsafe(), trieLogFactory.serialize(trieLog));
  }

  @Override
  public boolean containWorldStateStorage(final Hash blockHash) {
    return cachedWorldStatesByHash.containsKey(blockHash);
  }

  @Override
  public long getMaxLayersToLoad() {
    return maxLayersToLoad;
  }

  @Override
  public Optional<TrieLogLayer> getTrieLogLayer(final Hash blockHash) {
    return rootWorldStateStorage.getTrieLog(blockHash).map(trieLogFactory::deserialize);
  }

  @Override
  public synchronized long subscribe(final TrieLogAddedObserver sub) {
    return trieLogAddedObservers.subscribe(sub);
  }

  @Override
  public synchronized void unsubscribe(final long id) {
    trieLogAddedObservers.unsubscribe(id);
  }
}
