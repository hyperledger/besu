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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.metrics.ObservableMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorageTransaction;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarkSweepPruner {

  private static final Logger LOG = LoggerFactory.getLogger(MarkSweepPruner.class);
  private static final byte[] IN_USE = Bytes.of(1).toArrayUnsafe();

  private static final int DEFAULT_OPS_PER_TRANSACTION = 10_000;
  private static final int MAX_MARKING_THREAD_POOL_SIZE = 2;

  private final int operationsPerTransaction;
  private final WorldStateStorage worldStateStorage;
  private final MutableBlockchain blockchain;
  private final KeyValueStorage markStorage;
  private final Counter markedNodesCounter;
  private final Counter markOperationCounter;
  private final Counter sweepOperationCounter;
  private final Counter sweptNodesCounter;
  private final Stopwatch markStopwatch;
  private volatile long nodeAddedListenerId;
  private final ReadWriteLock pendingMarksLock = new ReentrantReadWriteLock();
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

    markStopwatch = Stopwatch.createUnstarted();
    metricsSystem.createLongGauge(
        BesuMetricCategory.PRUNER,
        "mark_time_duration",
        "Cumulative number of seconds spent marking the state trie across all pruning cycles",
        () -> markStopwatch.elapsed(TimeUnit.SECONDS));

    LOG.debug("Using {} pruner threads", MAX_MARKING_THREAD_POOL_SIZE);
  }

  public void prepare() {
    // Optimization for the case where the previous cycle was interrupted (like the node was shut
    // down). If the previous cycle was interrupted, there will be marks in the mark storage from
    // last time, causing the first sweep to be smaller than it needs to be.
    clearMarks();

    nodeAddedListenerId = worldStateStorage.addNodeAddedListener(this::markNodes);
  }

  /**
   * This is a parallel mark implementation.
   *
   * <p>The parallel task production is by sub-trie, so calling `visitAll` on a root node will
   * eventually spawn up to 16 tasks (for a hexary trie).
   *
   * <p>If we marked each sub-trie in its own thread, with no common queue of tasks, our mark speed
   * would be limited by the sub-trie with the maximum number of nodes. In practice for the Ethereum
   * mainnet, we see a large imbalance in sub-trie size so without a common task pool the time in
   * which there is only 1 thread left marking its big sub-trie would be substantial.
   *
   * <p>If we were to leave all threads to produce mark tasks before starting to mark, we would run
   * out of memory quickly.
   *
   * <p>If we were to have a constant number of threads producing the mark tasks with the others
   * consuming them, we would have to optimize the production/consumption balance.
   *
   * <p>To get the best of both worlds, the marking executor has a {@link
   * ThreadPoolExecutor.CallerRunsPolicy} which causes the producing tasks to essentially consume
   * their own mark task immediately when the task queue is full. The resulting behavior is threads
   * that mark their own sub-trie until they finish that sub-trie, at which point they switch to
   * marking the sub-trie tasks produced by another thread.
   *
   * @param rootHash The root hash of the whole state trie. Roots of storage tries will be
   *     discovered though traversal.
   */
  public void mark(final Hash rootHash) {
    markOperationCounter.inc();
    markStopwatch.start();
    final ExecutorService markingExecutorService =
        new ThreadPoolExecutor(
            0,
            MAX_MARKING_THREAD_POOL_SIZE,
            5L,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<>(16),
            new ThreadFactoryBuilder()
                .setDaemon(true)
                .setPriority(Thread.MIN_PRIORITY)
                .setNameFormat(this.getClass().getSimpleName() + "-%d")
                .build(),
            new ThreadPoolExecutor.CallerRunsPolicy());
    createStateTrie(rootHash)
        .visitAll(
            node -> {
              markNode(node.getHash());
              node.getValue()
                  .ifPresent(value -> processAccountState(value, markingExecutorService));
            },
            markingExecutorService)
        .join() /* This will block on all the marking tasks to be _produced_ but doesn't guarantee that the marking tasks have been completed. */;
    markingExecutorService.shutdown();
    try {
      // This ensures that the marking tasks complete.
      markingExecutorService.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      LOG.info("Interrupted while marking", e);
    }
    markStopwatch.stop();
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
      final BlockHeader blockHeader = blockchain.getBlockHeader(blockNumber).get();
      final Hash candidateStateRootHash = blockHeader.getStateRoot();
      if (!worldStateStorage.isWorldStateAvailable(candidateStateRootHash, null)) {
        break;
      }

      if (!isMarked(candidateStateRootHash)) {
        updater.removeAccountStateTrieNode(null, candidateStateRootHash);
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
    return pendingMarks.contains(key) || markStorage.containsKey(key.toArrayUnsafe());
  }

  private boolean isMarked(final byte[] key) {
    return pendingMarks.contains(Bytes32.wrap(key)) || markStorage.containsKey(key);
  }

  private MerklePatriciaTrie<Bytes32, Bytes> createStateTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        worldStateStorage::getAccountStateTrieNode,
        rootHash,
        Function.identity(),
        Function.identity());
  }

  private MerklePatriciaTrie<Bytes32, Bytes> createStorageTrie(final Bytes32 rootHash) {
    return new StoredMerklePatriciaTrie<>(
        (location, hash) -> worldStateStorage.getAccountStorageTrieNode(null, location, hash),
        rootHash,
        Function.identity(),
        Function.identity());
  }

  private void processAccountState(final Bytes value, final ExecutorService executorService) {
    final StateTrieAccountValue accountValue = StateTrieAccountValue.readFrom(RLP.input(value));
    markNode(accountValue.getCodeHash());

    createStorageTrie(accountValue.getStorageRoot())
        .visitAll(storageNode -> markNode(storageNode.getHash()), executorService);
  }

  @VisibleForTesting
  void markNode(final Bytes32 hash) {
    markThenMaybeFlush(() -> pendingMarks.add(hash), 1);
  }

  private void markNodes(final Collection<Bytes32> nodeHashes) {
    markThenMaybeFlush(() -> pendingMarks.addAll(nodeHashes), nodeHashes.size());
  }

  private void markThenMaybeFlush(final Runnable nodeMarker, final int numberOfNodes) {
    // We use the read lock here because pendingMarks is threadsafe and we want to allow all the
    // marking threads access simultaneously.
    final Lock markLock = pendingMarksLock.readLock();
    markLock.lock();
    try {
      nodeMarker.run();
    } finally {
      markLock.unlock();
    }
    markedNodesCounter.inc(numberOfNodes);

    // However, when the size of pendingMarks grows too large, we want all the threads to stop
    // adding because we're going to clear the set.
    // Therefore, we need to take out a write lock.
    if (pendingMarks.size() >= operationsPerTransaction) {
      final Lock flushLock = pendingMarksLock.writeLock();
      flushLock.lock();
      try {
        // Check once again that the condition holds. If it doesn't, that means another thread
        // already flushed them.
        if (pendingMarks.size() >= operationsPerTransaction) {
          flushPendingMarks();
        }
      } finally {
        flushLock.unlock();
      }
    }
  }

  private void flushPendingMarks() {
    final KeyValueStorageTransaction transaction = markStorage.startTransaction();
    pendingMarks.forEach(node -> transaction.put(node.toArrayUnsafe(), IN_USE));
    transaction.commit();
    pendingMarks.clear();
  }
}
