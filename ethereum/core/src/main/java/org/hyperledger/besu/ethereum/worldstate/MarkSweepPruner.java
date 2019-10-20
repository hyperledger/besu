/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.worldstate;

import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;
import org.hyperledger.besu.util.bytes.Bytes32;
import org.hyperledger.besu.util.bytes.BytesValue;

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
  private final Set<Bytes32> pendingMarks = Collections.newSetFromMap(new ConcurrentHashMap<>());

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
            BesuMetricCategory.PRUNER,
            "marked_nodes_total",
            "Total number of nodes marked as in use");
    markOperationCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.PRUNER,
            "mark_operations_total",
            "Total number of mark operations performed");

    sweptNodesCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.PRUNER, "swept_nodes_total", "Total number of unused nodes removed");
    sweepOperationCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.PRUNER,
            "sweep_operations_total",
            "Total number of sweep operations performed");
  }

  public void prepare() {
    // Optimization for the case where the previous cycle was interrupted (like the node was shut
    // down). If the previous cycle was interrupted, there will be marks in the mark storage from
    // last time, causing the first sweep to be smaller than it needs to be.
    clearMarks();

    nodeAddedListenerId = worldStateStorage.addNodeAddedListener(this::markNodes);
  }

  public void mark(final Hash rootHash) {
    markOperationCounter.inc();
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
    LOG.debug("Completed marking used nodes for pruning");
  }

  public void sweepBefore(final long markedBlockNumber) {
    sweepOperationCounter.inc();
    LOG.debug("Sweeping unused nodes");
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

      if (!isMarked(candidateStateRootHash)) {
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
    prunedNodeCount += worldStateStorage.prune(this::isMarked);
    sweptNodesCounter.inc(prunedNodeCount);
    clearMarks();
    LOG.debug("Completed sweeping unused nodes");
  }

  public void cleanup() {
    worldStateStorage.removeNodeAddedListener(nodeAddedListenerId);
    clearMarks();
  }

  public void clearMarks() {
    markStorage.clear();
    pendingMarks.clear();
  }

  private boolean isMarked(final Bytes32 key) {
    return pendingMarks.contains(key) || markStorage.containsKey(key.getArrayUnsafe());
  }

  private boolean isMarked(final byte[] key) {
    return pendingMarks.contains(Bytes32.wrap(key)) || markStorage.containsKey(key);
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

  private void markNodes(final Collection<Bytes32> nodeHashes) {
    markedNodesCounter.inc(nodeHashes.size());
    markLock.lock();
    try {
      pendingMarks.addAll(nodeHashes);
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

  private void flushPendingMarks() {
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
}
