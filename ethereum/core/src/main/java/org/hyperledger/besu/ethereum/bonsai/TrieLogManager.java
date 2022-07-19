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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrieLogManager {

  private static final long RETAINED_LAYERS = 512; // at least 256 + typical rollbacks

  private static final Logger LOG = LoggerFactory.getLogger(TrieLogManager.class);

  private final Blockchain blockchain;
  private final BonsaiWorldStateKeyValueStorage worldStateStorage;

  private final Map<Bytes32, BonsaiLayeredWorldState> layeredWorldStatesByHash;
  private final long maxLayersToLoad;

  public TrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final Map<Bytes32, BonsaiLayeredWorldState> layeredWorldStatesByHash) {
    this.blockchain = blockchain;
    this.worldStateStorage = worldStateStorage;
    this.layeredWorldStatesByHash = layeredWorldStatesByHash;
    this.maxLayersToLoad = maxLayersToLoad;
  }

  public TrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad) {
    this(blockchain, worldStateStorage, maxLayersToLoad, new HashMap<>());
  }

  public TrieLogManager(
      final Blockchain blockchain, final BonsaiWorldStateKeyValueStorage worldStateStorage) {
    this(blockchain, worldStateStorage, RETAINED_LAYERS, new HashMap<>());
  }

  public synchronized void saveTrieLog(
      final BonsaiWorldStateArchive worldStateArchive,
      final BonsaiWorldStateUpdater localUpdater,
      final Hash worldStateRootHash,
      final BlockHeader blockHeader) {
    // do not overwrite a trielog layer that already exists in the database.
    // if it's only in memory we need to save it
    // for example, like that in case of reorg we don't replace a trielog layer
    if (worldStateStorage.getTrieLog(blockHeader.getHash()).isEmpty()) {
      final BonsaiWorldStateKeyValueStorage.Updater stateUpdater = worldStateStorage.updater();
      boolean success = false;
      try {
        final TrieLogLayer trieLog =
            prepareTrieLog(blockHeader, worldStateRootHash, localUpdater, worldStateArchive);
        persistTrieLog(blockHeader, worldStateRootHash, trieLog, stateUpdater);
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

  public synchronized void addLayeredWorldState(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog,
      final BonsaiWorldStateArchive worldStateArchive) {

    final BonsaiLayeredWorldState bonsaiLayeredWorldState =
        new BonsaiLayeredWorldState(
            blockchain,
            worldStateArchive,
            Optional.of((BonsaiPersistedWorldState) worldStateArchive.getMutable()),
            blockHeader.getNumber(),
            worldStateRootHash,
            trieLog);
    debugLambda(
        LOG,
        "adding layered world state for block {}, state root hash {}",
        blockHeader::toLogString,
        worldStateRootHash::toHexString);
    layeredWorldStatesByHash.put(blockHeader.getHash(), bonsaiLayeredWorldState);
    scrubLayeredCache(blockHeader.getNumber());
  }

  public synchronized void updateLayeredWorldState(
      final Hash blockParentHash, final Hash blockHash) {
    layeredWorldStatesByHash.computeIfPresent(
        blockParentHash,
        (parentHash, bonsaiLayeredWorldState) -> {
          if (layeredWorldStatesByHash.containsKey(blockHash)) {
            bonsaiLayeredWorldState.setNextWorldView(
                Optional.of(layeredWorldStatesByHash.get(blockHash)));
          }
          return bonsaiLayeredWorldState;
        });
  }

  public synchronized void scrubLayeredCache(final long newMaxHeight) {
    final long waterline = newMaxHeight - RETAINED_LAYERS;
    layeredWorldStatesByHash.entrySet().removeIf(entry -> entry.getValue().getHeight() < waterline);
  }

  public long getMaxLayersToLoad() {
    return maxLayersToLoad;
  }

  public Optional<TrieLogLayer> getTrieLogLayer(final Hash blockHash) {
    if (layeredWorldStatesByHash.containsKey(blockHash)) {
      return Optional.of(layeredWorldStatesByHash.get(blockHash).getTrieLog());
    } else {
      return worldStateStorage.getTrieLog(blockHash).map(TrieLogLayer::fromBytes);
    }
  }

  public Optional<MutableWorldState> getBonsaiLayeredWorldState(final Hash blockHash) {
    if (layeredWorldStatesByHash.containsKey(blockHash)) {
      return Optional.of(layeredWorldStatesByHash.get(blockHash));
    }
    return Optional.empty();
  }

  private TrieLogLayer prepareTrieLog(
      final BlockHeader blockHeader,
      final Hash currentWorldStateRootHash,
      final BonsaiWorldStateUpdater localUpdater,
      final BonsaiWorldStateArchive worldStateArchive) {
    debugLambda(LOG, "Adding layered world state for {}", blockHeader::toLogString);
    final TrieLogLayer trieLog = localUpdater.generateTrieLog(blockHeader.getBlockHash());
    trieLog.freeze();
    addLayeredWorldState(blockHeader, currentWorldStateRootHash, trieLog, worldStateArchive);
    return trieLog;
  }

  private void persistTrieLog(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLogLayer trieLog,
      final BonsaiWorldStateKeyValueStorage.Updater stateUpdater) {
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
}
