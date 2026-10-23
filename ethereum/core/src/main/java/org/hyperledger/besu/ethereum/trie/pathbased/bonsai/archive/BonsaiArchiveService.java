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
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode.ArchiveTrieNodeStrategy;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.archive.trienode.TrailingTrieNodeArchiver;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.FlatDbMode;

import java.io.Closeable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lifecycle handle for the Bonsai archive's background components: trie-node capture, the flat-DB
 * archive migrator and, when archive proofs are enabled, the trailing trie-node archiver. Built by
 * {@link BonsaiArchiveFactory}.
 */
public final class BonsaiArchiveService implements Closeable {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiArchiveService.class);

  /**
   * The trie-node capture strategy and archiver that exist only when archive proofs are enabled.
   */
  record ProofCapture(ArchiveTrieNodeStrategy strategy, TrailingTrieNodeArchiver archiver) {}

  private final BonsaiWorldStateKeyValueStorage worldStateStorage;
  private final Blockchain blockchain;
  private final BonsaiFlatDbToArchiveMigrator migrator;
  private final BonsaiArchiveTrailingSync trailingSync;
  private final long maxLayersToLoad;
  private final Optional<ProofCapture> proofCapture;
  private final List<ExecutorService> executors;
  private final AtomicBoolean started = new AtomicBoolean(false);

  BonsaiArchiveService(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final Blockchain blockchain,
      final BonsaiFlatDbToArchiveMigrator migrator,
      final BonsaiArchiveTrailingSync trailingSync,
      final long maxLayersToLoad,
      final Optional<ProofCapture> proofCapture,
      final List<ExecutorService> executors) {
    this.worldStateStorage = worldStateStorage;
    this.blockchain = blockchain;
    this.migrator = migrator;
    this.trailingSync = trailingSync;
    this.maxLayersToLoad = maxLayersToLoad;
    this.proofCapture = proofCapture;
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
    trailingSync.start();
    proofCapture.ifPresent(
        capture -> {
          final ArchiveTrieNodeStrategy strategy = capture.strategy();
          strategy.setHasFinalizedBlock(this::hasImportedFinalizedBlock);
          strategy.setCatchUpTo(trailingSync::catchUpProofsTo);
          synchronizer.subscribeInSync(strategy, maxLayersToLoad);
          // The trailing archiver runs only while the capture mode is trailing; the first block
          // decides.
          strategy.setCaptureModeListener(
              mode ->
                  trailingSync.setProofsArchiving(
                      mode == ArchiveTrieNodeStrategy.CaptureMode.TRAILING));
        });
    startMigration(synchronizer);
  }

  @VisibleForTesting
  BonsaiArchiveTrailingSync trailingSync() {
    return trailingSync;
  }

  /**
   * Whether a finalized block is imported. False when there is no finality signal (BFT, Clique) or
   * when the finalized block is ahead of every block being imported.
   */
  @VisibleForTesting
  boolean hasImportedFinalizedBlock() {
    return blockchain.getFinalized().flatMap(blockchain::getBlockHeader).isPresent();
  }

  private void startMigration(final Synchronizer synchronizer) {
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
                  migrator
                      .migrate()
                      .thenRun(this::startFlatArchiveIfUpgraded)
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
      // Already in ARCHIVE mode (restart after migration): resume trailing the head
      trailingSync.startFlatArchive();
    }
  }

  // Bulk migration also completes normally when it stops early on close, without upgrading.
  private void startFlatArchiveIfUpgraded() {
    if (worldStateStorage.getFlatDbMode() == FlatDbMode.ARCHIVE) {
      trailingSync.startFlatArchive();
    } else {
      LOG.debug("Flat DB is not in archive mode after migration; not starting flat archive");
    }
  }

  /** Stops background work and shuts down executors. Must run before the storage closes. */
  @Override
  public void close() {
    // Coordinator and migrator first so in-flight callbacks finish before storage closes, then the
    // capture strategy (drains the trie-capture pool). shutdownNow afterwards is idempotent and
    // covers components that were never started.
    trailingSync.close();
    migrator.close();
    proofCapture.ifPresent(capture -> capture.strategy().close());
    for (final ExecutorService executor : executors) {
      executor.shutdownNow();
    }
  }
}
