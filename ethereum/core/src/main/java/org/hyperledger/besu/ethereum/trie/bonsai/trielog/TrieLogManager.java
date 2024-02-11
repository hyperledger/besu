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
package org.hyperledger.besu.ethereum.trie.bonsai.trielog;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.bonsai.worldview.BonsaiWorldStateUpdateAccumulator;
import org.hyperledger.besu.plugin.BesuContext;
import org.hyperledger.besu.plugin.services.TrieLogService;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogEvent;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogFactory;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;
import org.hyperledger.besu.util.Subscribers;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrieLogManager {
  private static final Logger LOG = LoggerFactory.getLogger(TrieLogManager.class);
  public static final long LOG_RANGE_LIMIT = 1000; // restrict trielog range queries to 1k logs
  protected final Blockchain blockchain;
  protected final BonsaiWorldStateKeyValueStorage rootWorldStateStorage;

  protected final long maxLayersToLoad;
  protected final Subscribers<TrieLogEvent.TrieLogObserver> trieLogObservers = Subscribers.create();

  protected final TrieLogFactory trieLogFactory;

  public TrieLogManager(
      final Blockchain blockchain,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final long maxLayersToLoad,
      final BesuContext pluginContext) {
    this.blockchain = blockchain;
    this.rootWorldStateStorage = worldStateStorage;
    this.maxLayersToLoad = maxLayersToLoad;
    this.trieLogFactory = setupTrieLogFactory(pluginContext);
  }

  public synchronized void saveTrieLog(
      final BonsaiWorldStateUpdateAccumulator localUpdater,
      final Hash forWorldStateRootHash,
      final BlockHeader forBlockHeader,
      final BonsaiWorldState forWorldState) {
    // do not overwrite a trielog layer that already exists in the database.
    // if it's only in memory we need to save it
    // for example, in case of reorg we don't replace a trielog layer
    if (rootWorldStateStorage.getTrieLog(forBlockHeader.getHash()).isEmpty()) {
      final BonsaiWorldStateKeyValueStorage.BonsaiUpdater stateUpdater =
          forWorldState.getWorldStateStorage().updater();
      boolean success = false;
      try {
        final TrieLog trieLog = prepareTrieLog(forBlockHeader, localUpdater);
        persistTrieLog(forBlockHeader, forWorldStateRootHash, trieLog, stateUpdater);

        // notify trie log added observers, synchronously
        trieLogObservers.forEach(o -> o.onTrieLogAdded(new TrieLogAddedEvent(trieLog)));

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

  private TrieLog prepareTrieLog(
      final BlockHeader blockHeader, final BonsaiWorldStateUpdateAccumulator localUpdater) {
    LOG.atDebug()
        .setMessage("Adding layered world state for {}")
        .addArgument(blockHeader::toLogString)
        .log();
    final TrieLog trieLog = trieLogFactory.create(localUpdater, blockHeader);
    trieLog.freeze();
    return trieLog;
  }

  private void persistTrieLog(
      final BlockHeader blockHeader,
      final Hash worldStateRootHash,
      final TrieLog trieLog,
      final BonsaiWorldStateKeyValueStorage.BonsaiUpdater stateUpdater) {
    LOG.atDebug()
        .setMessage("Persisting trie log for block hash {} and world state root {}")
        .addArgument(blockHeader::toLogString)
        .addArgument(worldStateRootHash::toHexString)
        .log();

    stateUpdater
        .getTrieLogStorageTransaction()
        .put(blockHeader.getHash().toArrayUnsafe(), trieLogFactory.serialize(trieLog));
  }

  public long getMaxLayersToLoad() {
    return maxLayersToLoad;
  }

  public Optional<TrieLog> getTrieLogLayer(final Hash blockHash) {
    return rootWorldStateStorage.getTrieLog(blockHash).map(trieLogFactory::deserialize);
  }

  public synchronized long subscribe(final TrieLogEvent.TrieLogObserver sub) {
    return trieLogObservers.subscribe(sub);
  }

  public synchronized void unsubscribe(final long id) {
    trieLogObservers.unsubscribe(id);
  }

  private TrieLogFactory setupTrieLogFactory(final BesuContext pluginContext) {
    // if we have a TrieLogService from pluginContext, use it.
    var trieLogServicez =
        Optional.ofNullable(pluginContext)
            .flatMap(context -> context.getService(TrieLogService.class));

    if (trieLogServicez.isPresent()) {
      var trieLogService = trieLogServicez.get();
      // push the TrieLogProvider into the TrieLogService
      trieLogService.configureTrieLogProvider(getTrieLogProvider());

      // configure plugin observers:
      trieLogService.getObservers().forEach(trieLogObservers::subscribe);

      // return the TrieLogFactory implementation from the TrieLogService
      return trieLogService.getTrieLogFactory();
    } else {
      // Otherwise default to TrieLogFactoryImpl
      return new TrieLogFactoryImpl();
    }
  }

  private TrieLogProvider getTrieLogProvider() {
    return new TrieLogProvider() {
      @Override
      public Optional<TrieLog> getTrieLogLayer(final Hash blockHash) {
        return TrieLogManager.this.getTrieLogLayer(blockHash);
      }

      @Override
      public Optional<TrieLog> getTrieLogLayer(final long blockNumber) {
        return TrieLogManager.this
            .blockchain
            .getBlockHeader(blockNumber)
            .map(BlockHeader::getHash)
            .flatMap(TrieLogManager.this::getTrieLogLayer);
      }

      @Override
      public List<TrieLogRangeTuple> getTrieLogsByRange(
          final long fromBlockNumber, final long toBlockNumber) {
        return rangeAsStream(fromBlockNumber, toBlockNumber)
            .map(blockchain::getBlockHeader)
            .map(
                headerOpt ->
                    headerOpt.flatMap(
                        header ->
                            TrieLogManager.this
                                .getTrieLogLayer(header.getBlockHash())
                                .map(
                                    layer ->
                                        new TrieLogRangeTuple(
                                            header.getBlockHash(), header.getNumber(), layer))))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
      }

      Stream<Long> rangeAsStream(final long fromBlockNumber, final long toBlockNumber) {
        if (Math.abs(toBlockNumber - fromBlockNumber) > LOG_RANGE_LIMIT) {
          throw new IllegalArgumentException("Requested Range too large");
        }
        long left = Math.min(fromBlockNumber, toBlockNumber);
        long right = Math.max(fromBlockNumber, toBlockNumber);
        return LongStream.range(left, right).boxed();
      }
    };
  }
}
