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
package org.hyperledger.besu.ethereum.trie.pathbased.bonsaiarchive;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_INFO_STATE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.ACCOUNT_STORAGE_STORAGE;
import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.VARIABLES;
import static org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.flat.BonsaiArchiveFlatDbStrategy.calculateArchiveKeyWithMinSuffix;
import static org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.flat.BonsaiArchiveFlatDbStrategy.calculateNaturalSlotKey;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.BonsaiContext;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.TrieLogManager;
import org.hyperledger.besu.metrics.BesuMetricCategory;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorageTransaction;
import org.hyperledger.besu.plugin.services.trielogs.TrieLog;
import org.hyperledger.besu.util.log.LogUtil;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Migrates a Bonsai storage to Bonsai archive storage format. */
public class BonsaiFlatDbToArchiveMigrator {

  private static final Logger LOG = LoggerFactory.getLogger(BonsaiFlatDbToArchiveMigrator.class);
  private static final int LOG_INTERVAL_SECONDS = 60;
  @VisibleForTesting static final int TAIL_THRESHOLD = 64;

  private static final byte[] MIGRATION_PROGRESS_KEY =
      "ARCHIVE_MIGRATION_PROGRESS".getBytes(StandardCharsets.UTF_8);

  private final BonsaiWorldStateKeyValueStorage worldStateStorage;
  private final TrieLogManager trieLogManager;
  private final Blockchain blockchain;
  private final ScheduledExecutorService executorService;
  private final Counter migratedBlocksCounter;
  private final AtomicBoolean shouldLogProgress = new AtomicBoolean(true);
  protected final AtomicBoolean migrationRunning = new AtomicBoolean(false);

  /**
   * Creates a new BonsaiFlatDbToArchiveMigrator.
   *
   * @param worldStateStorage the Bonsai world state storage
   * @param trieLogManager the trie log manager for reading trie logs
   * @param blockchain the blockchain for reading block headers
   * @param executorService the executor service for running migration on a separate thread
   * @param metricsSystem the metrics system for tracking migration progress
   */
  public BonsaiFlatDbToArchiveMigrator(
      final BonsaiWorldStateKeyValueStorage worldStateStorage,
      final TrieLogManager trieLogManager,
      final Blockchain blockchain,
      final ScheduledExecutorService executorService,
      final MetricsSystem metricsSystem) {
    this.worldStateStorage = worldStateStorage;
    this.trieLogManager = trieLogManager;
    this.blockchain = blockchain;
    this.executorService = executorService;
    this.migratedBlocksCounter =
        metricsSystem.createCounter(
            BesuMetricCategory.BLOCKCHAIN,
            "archive_migration_count",
            "Number of blocks migrated from Bonsai to Bonsai Archive storage");
  }

  /**
   * Migrates Bonsai flat DB to Bonsai archive format by processing trie logs sequentially. Resumes
   * from saved progress if available, otherwise starts from block 0. The target block is
   * continuously updated as new blocks are imported, so the migrator chases the chain head until it
   * converges.
   *
   * @return a CompletableFuture that completes when migration finishes
   */
  public CompletableFuture<Void> migrate() {
    if (!migrationRunning.compareAndSet(false, true)) {
      LOG.warn("Bonsai migration already in progress, ignoring");
      return CompletableFuture.completedFuture(null);
    }

    final long endBlock = blockchain.getChainHeadBlockNumber();
    final AtomicLong target = new AtomicLong(endBlock);
    final long blockObserverId =
        blockchain.observeBlockAdded(event -> target.set(event.getHeader().getNumber()));
    return CompletableFuture.runAsync(
        () -> migrateBlocks(endBlock, target, blockObserverId), executorService);
  }

  private void migrateBlocks(
      final long endBlock, final AtomicLong target, final long blockObserverId) {
    try {
      final Instant migrationStartTime = Instant.now();

      final long startBlock = getMigrationProgress().orElse(0L);
      final SegmentedKeyValueStorage storage = worldStateStorage.getComposedWorldStateStorage();
      LOG.info("Starting Bonsai Archive migration from block {} to {}", startBlock, endBlock);
      long migratedCount = 0;
      long skippedCount = 0;

      for (long blockNumber = startBlock; blockNumber <= target.get(); blockNumber++) {
        final Optional<TrieLog> maybeTrieLog =
            blockchain
                .getBlockHeader(blockNumber)
                .flatMap(header -> trieLogManager.getTrieLogLayer(header.getHash()));

        final SegmentedKeyValueStorageTransaction tx = storage.startTransaction();
        try {
          if (maybeTrieLog.isPresent()) {
            processBlock(maybeTrieLog.get(), blockNumber, tx);
            migratedCount++;
            migratedBlocksCounter.inc();
          } else {
            if (blockNumber > 0) {
              LOG.debug("No trie log found for block {}, skipping", blockNumber);
            }
            skippedCount++;
          }
          // Always save progress, even for blocks with no trie log
          saveProgress(blockNumber, tx);
          tx.commit();
        } catch (final Exception e) {
          LOG.error("Failed to process block {}, rolling back transaction", blockNumber, e);
          try {
            tx.rollback();
          } catch (final Exception rollbackException) {
            LOG.error(
                "Failed to rollback transaction for block {}", blockNumber, rollbackException);
          }
          throw new IllegalStateException(
              "Bonsai migration failed at block " + blockNumber + ": " + e.getMessage(), e);
        }

        logProgress(blockNumber, startBlock, target.get(), migratedCount, skippedCount);
      }

      worldStateStorage.upgradeToFullFlatDbMode();
      logCompletion(startBlock, target.get(), migrationStartTime, migratedCount, skippedCount);

    } catch (final Exception e) {
      LOG.error("Bonsai Archive migration failed", e);
      throw new RuntimeException(e);
    } finally {
      blockchain.removeObserver(blockObserverId);
      migrationRunning.set(false);
    }
  }

  @VisibleForTesting
  protected Optional<Long> getMigrationProgress() {
    return worldStateStorage
        .getComposedWorldStateStorage()
        .get(VARIABLES, MIGRATION_PROGRESS_KEY)
        .map(Bytes::wrap)
        .map(Bytes::toLong);
  }

  private void logProgress(
      final long blockNumber,
      final long startBlock,
      final long endBlock,
      final long migratedCount,
      final long skippedCount) {
    final long totalBlocks = endBlock - startBlock;
    LogUtil.throttledLog(
        () -> {
          long progressPercent =
              totalBlocks > 0 ? ((blockNumber - startBlock) * 100) / totalBlocks : 100;
          LOG.info(
              "Bonsai Archive migration progress: {}% (block {}/{}, migrated: {}, skipped: {})",
              progressPercent, blockNumber, endBlock, migratedCount, skippedCount);
        },
        shouldLogProgress,
        LOG_INTERVAL_SECONDS);
  }

  private void logCompletion(
      final long startBlock,
      final long endBlock,
      final Instant migrationStartTime,
      final long migratedCount,
      final long skippedCount) {
    final Duration migrationDuration = Duration.between(migrationStartTime, Instant.now());
    final String formatedDuration =
        DurationFormatUtils.formatDurationWords(migrationDuration.toMillis(), true, true);
    LOG.info(
        "Bonsai Archive migration completed. Processed {} blocks ({} migrated, {} skipped) in {}.",
        endBlock - startBlock + 1,
        migratedCount,
        skippedCount,
        formatedDuration);
  }

  private void processBlock(
      final TrieLog trieLog, final long blockNumber, final SegmentedKeyValueStorageTransaction tx) {
    processAccountChanges(trieLog, blockNumber, tx);
    processStorageChanges(trieLog, blockNumber, tx);
  }

  private void processAccountChanges(
      final TrieLog trieLog, final long blockNumber, final SegmentedKeyValueStorageTransaction tx) {
    final BonsaiContext context = new BonsaiContext(blockNumber);
    trieLog
        .getAccountChanges()
        .forEach(
            (address, accountChange) -> {
              if (accountChange.getUpdated() != null) {
                final BytesValueRLPOutput out = new BytesValueRLPOutput();
                accountChange.getUpdated().writeTo(out);
                final byte[] key =
                    calculateArchiveKeyWithMinSuffix(
                        context, address.addressHash().getBytes().toArrayUnsafe());
                tx.put(ACCOUNT_INFO_STATE, key, out.encoded().toArrayUnsafe());
              }
            });
  }

  private void processStorageChanges(
      final TrieLog trieLog, final long blockNumber, final SegmentedKeyValueStorageTransaction tx) {
    final BonsaiContext context = new BonsaiContext(blockNumber);
    trieLog
        .getStorageChanges()
        .forEach(
            (address, storageMap) ->
                storageMap.forEach(
                    (slotKey, storageChange) -> {
                      if (storageChange.getUpdated() != null) {
                        final byte[] naturalKey =
                            calculateNaturalSlotKey(address.addressHash(), slotKey.getSlotHash());
                        final byte[] key = calculateArchiveKeyWithMinSuffix(context, naturalKey);
                        tx.put(
                            ACCOUNT_STORAGE_STORAGE,
                            key,
                            storageChange.getUpdated().toBytes().toArrayUnsafe());
                      }
                    }));
  }

  private void saveProgress(final long blockNumber, final SegmentedKeyValueStorageTransaction tx) {
    tx.put(VARIABLES, MIGRATION_PROGRESS_KEY, Bytes.ofUnsignedLong(blockNumber).toArrayUnsafe());
  }
}
