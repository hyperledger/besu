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
import org.hyperledger.besu.ethereum.bonsai.BonsaiWorldStateKeyValueStorage.BonsaiUpdater;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTrieLogManager<T extends MutableWorldState>
    implements TrieLogManager {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractTrieLogManager.class);
  public static final long RETAINED_LAYERS = 512; // at least 256 + typical rollbacks

  protected final Blockchain blockchain;
  protected final BonsaiWorldStateKeyValueStorage rootWorldStateStorage;

  protected final Map<Bytes32, CachedWorldState<T>> cachedWorldStatesByHash;
  protected final long maxLayersToLoad;

  AbstractTrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final Map<Bytes32, CachedWorldState<T>> cachedWorldStatesByHash) {
    this.blockchain = blockchain;
    this.rootWorldStateStorage = worldStateStorage;
    this.cachedWorldStatesByHash = cachedWorldStatesByHash;
    this.maxLayersToLoad = maxLayersToLoad;
  }

  @Override
  public synchronized void saveTrieLog(
      final BonsaiWorldStateArchive worldStateArchive,
      final BonsaiWorldStateUpdater localUpdater,
      final Hash forWorldStateRootHash,
      final BlockHeader forBlockHeader,
      final BonsaiPersistedWorldState forWorldState) {
    // do not overwrite a trielog layer that already exists in the database.
    // if it's only in memory we need to save it
    // for example, in case of reorg we don't replace a trielog layer
    if (rootWorldStateStorage.getTrieLog(forBlockHeader.getHash()).isEmpty()) {
      final BonsaiUpdater stateUpdater = forWorldState.getWorldStateStorage().updater();
      boolean success = false;
      try {
        final TrieLogLayer trieLog =
            prepareTrieLog(
                forBlockHeader,
                forWorldStateRootHash,
                localUpdater,
                worldStateArchive,
                forWorldState);
        persistTrieLog(forBlockHeader, forWorldStateRootHash, trieLog, stateUpdater);
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

  protected abstract void addCachedLayer(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog,
      final BonsaiWorldStateArchive worldStateArchive,
      final BonsaiPersistedWorldState forWorldState);

  @VisibleForTesting
  TrieLogLayer prepareTrieLog(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final BonsaiWorldStateUpdater localUpdater,
      final BonsaiWorldStateArchive worldStateArchive,
      final BonsaiPersistedWorldState forWorldState) {
    debugLambda(LOG, "Adding layered world state for {}", blockHeader::toLogString);
    final TrieLogLayer trieLog = localUpdater.generateTrieLog(blockHeader.getBlockHash());
    trieLog.freeze();
    addCachedLayer(blockHeader, worldStateRootHash, trieLog, worldStateArchive, forWorldState);
    scrubCachedLayers(blockHeader.getNumber());
    return trieLog;
  }

  synchronized void scrubCachedLayers(final long newMaxHeight) {
    if (cachedWorldStatesByHash.size() > RETAINED_LAYERS) {
      final long waterline = newMaxHeight - RETAINED_LAYERS;
      cachedWorldStatesByHash.values().stream()
          .filter(layer -> layer.getHeight() < waterline)
          .collect(Collectors.toList())
          .stream()
          .forEach(
              layer -> {
                cachedWorldStatesByHash.remove(layer.getTrieLog().getBlockHash());
                layer.dispose();
                Optional.ofNullable(layer.getMutableWorldState())
                    .ifPresent(
                        ws -> {
                          try {
                            ws.close();
                          } catch (Exception e) {
                            LOG.warn("Error closing bonsai worldstate layer", e);
                          }
                        });
              });
    }
  }

  private void persistTrieLog(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog,
      final BonsaiUpdater stateUpdater) {
    debugLambda(
        LOG,
        "Persisting trie log for block hash {} and world state root {}",
        blockHeader::toLogString,
        worldStateRootHash::toHexString);
    final BytesValueRLPOutput rlpLog = new BytesValueRLPOutput();
    trieLog.writeTo(rlpLog);
    stateUpdater
        .getTrieLogStorageTransaction()
        .put(blockHeader.getHash().toArrayUnsafe(), rlpLog.encoded().toArrayUnsafe());
  }

  @Override
  public Optional<MutableWorldState> getBonsaiCachedWorldState(final Hash blockHash) {
    if (cachedWorldStatesByHash.containsKey(blockHash)) {
      return Optional.ofNullable(cachedWorldStatesByHash.get(blockHash))
          .map(CachedWorldState::getMutableWorldState);
    }
    return Optional.empty();
  }

  @Override
  public long getMaxLayersToLoad() {
    return maxLayersToLoad;
  }

  @Override
  public Optional<TrieLogLayer> getTrieLogLayer(final Hash blockHash) {
    if (cachedWorldStatesByHash.containsKey(blockHash)) {
      return Optional.of(cachedWorldStatesByHash.get(blockHash).getTrieLog());
    } else {
      return rootWorldStateStorage.getTrieLog(blockHash).map(TrieLogLayer::fromBytes);
    }
  }
}
