/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Synchronizer;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode.ArchiveTrieNodeFrontierRoller;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode.ArchiveTrieNodeStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.code.CodeHashCodeStorageStrategy;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.io.Closeable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle handle for the Bonsai archive's background components: trie-node capture, the flat-DB
 * archive migrator and, when archive proofs are enabled, the trie-node frontier roller. Built by
 * {@link BonsaiArchiveFactory}.
 */
public final class BonsaiArchiveService implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiArchiveService.class);

  private final BonsaiArchiveWorldStateProvider provider;
  private final BonsaiWorldStateKeyValueStorage worldStateStorage;
  private final Blockchain blockchain;
  private final MetricsSystem metricsSystem;
  private final long maxLayersToLoad;
  private final Optional<ArchiveTrieNodeStrategy> trieNodeStrategy;
  private final Optional<ArchiveTrieNodeFrontierRoller> roller;
  private final List<ExecutorService> executors;
  private final ScheduledExecutorService migrationExecutor;
  private final AtomicBoolean started = new AtomicBoolean(false);
  private volatile BonsaiFlatDbToArchiveMigrator migrator;

  BonsaiArchiveService(
      final BonsaiArchiveWorldStateProvider provider,
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Blockchain blockchain,
      final MetricsSystem metricsSystem,
      final long maxLayersToLoad,
      final Optional<ArchiveTrieNodeStrategy> trieNodeStrategy,
      final Optional<ArchiveTrieNodeFrontierRoller> roller,
      final ScheduledExecutorService migrationExecutor,
      final List<ExecutorService> executors) {
    this.provider = provider;
    this.worldStateStorage = worldStateStorage;
    this.blockchain = blockchain;
    this.metricsSystem = metricsSystem;
    this.maxLayersToLoad = maxLayersToLoad;
    this.trieNodeStrategy = trieNodeStrategy;
    this.roller = roller;
    this.migrationExecutor = migrationExecutor;
    this.executors = List.copyOf(executors);
  }

  /**
   * Starts the background components. Must be called exactly once, after the synchronizer exists.
   *
   * @param synchronizer the node's synchronizer
   * @throws IllegalStateException if called more than once
   */
  public void start(final Synchronizer synchronizer) {
    if (!started.compareAndSet(false, true)) {
      throw new IllegalStateException("Bonsai archive already started");
    }
    trieNodeStrategy.ifPresent(
        strategy -> {
          strategy.setHasChainEstimate(() -> synchronizer.getBestPeerChainHead().isPresent());
          synchronizer.subscribeInSync(strategy, maxLayersToLoad);
          roller.ifPresent(r -> startRoller(synchronizer, strategy, r));
        });
    startMigration(synchronizer);
  }

  private void startRoller(
      final Synchronizer synchronizer,
      final ArchiveTrieNodeStrategy strategy,
      final ArchiveTrieNodeFrontierRoller frontierRoller) {
    final AtomicBoolean rollerStarted = new AtomicBoolean(false);
    synchronizer.subscribeInSync(
        inSync -> {
          if (inSync
              && synchronizer.getBestPeerChainHead().isPresent()
              && rollerStarted.compareAndSet(false, true)) {
            LOG.info("Node is in sync, starting archive trie-node frontier roller");
            frontierRoller.startOngoing(
                Math.max(0L, strategy.getTrieNodeWriter().lastArchivedBlock()));
          }
        },
        maxLayersToLoad);
  }

  private void startMigration(final Synchronizer synchronizer) {
    final BonsaiFlatDbToArchiveMigrator archiveMigrator =
        new BonsaiFlatDbToArchiveMigrator(
            worldStateStorage,
            provider.getTrieLogManager(),
            blockchain,
            migrationExecutor,
            metricsSystem,
            new BonsaiArchiveFlatDbStrategy(metricsSystem, new CodeHashCodeStorageStrategy()));
    provider.setArchiveMigrationProgressSupplier(archiveMigrator::getMigratedBlockNumber);
    migrator = archiveMigrator;

    final FlatDbMode flatDbMode = worldStateStorage.getFlatDbMode();
    if (flatDbMode == FlatDbMode.FULL || flatDbMode == FlatDbMode.PARTIAL) {
      final AtomicBoolean migrationStarted = new AtomicBoolean(false);
      final AtomicLong syncSubscriptionId = new AtomicLong();
      syncSubscriptionId.set(
          synchronizer.subscribeInSync(
              inSync -> {
                if (inSync && migrationStarted.compareAndSet(false, true)) {
                  synchronizer.unsubscribeInSync(syncSubscriptionId.get());
                  LOG.info("Node is in sync, starting Bonsai archive migration");
                  archiveMigrator
                      .migrate()
                      .exceptionally(
                          error -> {
                            LOG.error(
                                "Archive migration failed, archiver will remain disabled until restart",
                                error);
                            return null;
                          });
                }
              },
              0));
    } else {
      // Already in ARCHIVE mode (restart after migration): register ongoing migration
      archiveMigrator.startOngoingMigration();
    }
  }

  /** Stops background work and shuts down executors. Must run before the storage closes. */
  @Override
  public void close() {
    // Roller and migrator first so in-flight callbacks finish before storage closes, then the
    // capture strategy (drains the trie-capture pool). shutdownNow afterwards is idempotent and
    // covers components that were never started.
    roller.ifPresent(ArchiveTrieNodeFrontierRoller::close);
    if (migrator != null) {
      migrator.close();
    }
    trieNodeStrategy.ifPresent(ArchiveTrieNodeStrategy::close);
    for (final ExecutorService executor : executors) {
      executor.shutdownNow();
    }
  }
}
