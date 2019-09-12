/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.worldstate;

import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.trie.MerklePatriciaTrie;
import tech.pegasys.pantheon.ethereum.trie.StoredMerklePatriciaTrie;
import tech.pegasys.pantheon.metrics.ObservableMetricsSystem;
import tech.pegasys.pantheon.metrics.PantheonMetricCategory;
import tech.pegasys.pantheon.plugin.services.metrics.Counter;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorage;
import tech.pegasys.pantheon.plugin.services.storage.KeyValueStorageTransaction;
import tech.pegasys.pantheon.util.bytes.Bytes32;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MarkSweepPruner {

  private static final int DEFAULT_OPS_PER_TRANSACTION = 1000;
  private static final Logger LOG = LogManager.getLogger();
  private static final byte[] IN_USE = BytesValue.of(1).getArrayUnsafe();

  private final int operationsPerTransaction;
  private final WorldStateStorage worldStateStorage;
  private final MutableBlockchain blockchain;
  private final KeyValueStorage markStorage;
  private final Counter markedNodesCounter;
  private final Counter markOperationCounter;
  private final Counter sweepOperationCounter;
  private final Counter sweptNodesCounter;
  private volatile long nodeAddedListenerId;
  private final ReentrantLock markLock = new ReentrantLock(true);
  private final Set<BytesValue> pendingMarks = Collections.newSetFromMap(new ConcurrentHashMap<>());

  public MarkSweepPruner(
      final WorldStateStorage worldStateStorage,
      final MutableBlockchain blockchain,
      final KeyValueStorage markStorage,
      final ObservableMetricsSystem metricsSystem) {
    this(worldStateStorage, blockchain, markStorage, metricsSystem, DEFAULT_OPS_PER_TRANSACTION);
  }

  public MarkSweepPruner(
      final WorldStateStorage worldStateStorage,
      final MutableBlockchain blockchain,
      final KeyValueStorage markStorage,
      final ObservableMetricsSystem metricsSystem,
      final int operationsPerTransaction) {
    this.worldStateStorage = worldStateStorage;
    this.markStorage = markStorage;
    this.blockchain = blockchain;
    this.operationsPerTransaction = operationsPerTransaction;

    markedNodesCounter =
        metricsSystem.createCounter(
            PantheonMetricCategory.PRUNER,
            "marked_nodes_total",
            "Total number of nodes marked as in use");
    markOperationCounter =
        metricsSystem.createCounter(
            PantheonMetricCategory.PRUNER,
            "mark_operations_total",
            "Total number of mark operations performed");

    sweptNodesCounter =
        metricsSystem.createCounter(
            PantheonMetricCategory.PRUNER,
            "swept_nodes_total",
            "Total number of unused nodes removed");
    sweepOperationCounter =
        metricsSystem.createCounter(
            PantheonMetricCategory.PRUNER,
            "sweep_operations_total",
            "Total number of sweep operations performed");
  }

  public void prepare() {
    worldStateStorage.removeNodeAddedListener(nodeAddedListenerId); // Just in case.
    nodeAddedListenerId = worldStateStorage.addNodeAddedListener(this::markNewNodes);
  }

  public void cleanup() {
    worldStateStorage.removeNodeAddedListener(nodeAddedListenerId);
  }

  public void mark(final Hash rootHash) {
    markOperationCounter.inc();
    markStorage.clear();
    createStateTrie(rootHash)
        .visitAll(
            node -> {
              if (Thread.interrupted()) {
                // Since we don't expect to abort marking ourselves,
                // our abort process consists only of handling interrupts
                throw new RuntimeException("Interrupted while marking");
              }
              markNode(node.getHash());
              node.getValue().ifPresent(this::processAccountState);
            });
    LOG.info("Completed marking used nodes for pruning");
  }

  public void sweepBefore(final long markedBlockNumber) {
    flushPendingMarks();
    sweepOperationCounter.inc();
    LOG.info("Sweeping unused nodes");
    // Sweep state roots first, walking backwards until we get to a state root that isn't in the
    // storage
    long prunedNodeCount = 0;
    WorldStateStorage.Updater updater = worldStateStorage.updater();
    for (long blockNumber = markedBlockNumber - 1; blockNumber >= 0; blockNumber--) {
      final Hash candidateStateRootHash =
          blockchain.getBlockHeader(blockNumber).get().getStateRoot();

      if (!worldStateStorage.isWorldStateAvailable(candidateStateRootHash)) {
        break;
      }

      if (!markStorage.containsKey(candidateStateRootHash.getArrayUnsafe())) {
        updater.removeAccountStateTrieNode(candidateStateRootHash);
        prunedNodeCount++;
        if (prunedNodeCount % operationsPerTransaction == 0) {
          updater.commit();
          updater = worldStateStorage.updater();
        }
      }
    }
    updater.commit();
    // Sweep non-state-root nodes
    prunedNodeCount += worldStateStorage.prune(markStorage::containsKey);
    sweptNodesCounter.inc(prunedNodeCount);
    worldStateStorage.removeNodeAddedListener(nodeAddedListenerId);
    markStorage.clear();
    LOG.info("Completed sweeping unused nodes");
  }

  private MerklePatriciaTrie<Bytes32, BytesValue> createStateTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStateTrieNode,
        rootHash,
        Function.identity(),
        Function.identity());
  }

  private MerklePatriciaTrie<Bytes32, BytesValue> createStorageTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStorageTrieNode,
        rootHash,
        Function.identity(),
        Function.identity());
  }

  private void processAccountState(final BytesValue value) {
    final StateTrieAccountValue accountValue = StateTrieAccountValue.readFrom(RLP.input(value));
    markNode(accountValue.getCodeHash());

    createStorageTrie(accountValue.getStorageRoot())
        .visitAll(storageNode -> markNode(storageNode.getHash()));
  }

  @VisibleForTesting
  void markNode(final Bytes32 hash) {
    markedNodesCounter.inc();
    markLock.lock();
    try {
      pendingMarks.add(hash);
      maybeFlushPendingMarks();
    } finally {
      markLock.unlock();
    }
  }

  private void maybeFlushPendingMarks() {
    if (pendingMarks.size() > operationsPerTransaction) {
      flushPendingMarks();
    }
  }

  void flushPendingMarks() {
    markLock.lock();
    try {
      final KeyValueStorageTransaction transaction = markStorage.startTransaction();
      pendingMarks.forEach(node -> transaction.put(node.getArrayUnsafe(), IN_USE));
      transaction.commit();
      pendingMarks.clear();
    } finally {
      markLock.unlock();
    }
  }

  private void markNewNodes(final Collection<Bytes32> nodeHashes) {
    markedNodesCounter.inc(nodeHashes.size());
    markLock.lock();
    try {
      pendingMarks.addAll(nodeHashes);
      maybeFlushPendingMarks();
    } finally {
      markLock.unlock();
    }
  }
}
