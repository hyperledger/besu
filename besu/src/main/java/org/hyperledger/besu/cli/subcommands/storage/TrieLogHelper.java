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

package org.hyperledger.besu.cli.subcommands.storage;

import static com.google.common.base.Preconditions.checkArgument;
import static org.hyperledger.besu.controller.BesuController.DATABASE_PATH;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.diffbased.common.storage.DiffBasedWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Helper class for counting and pruning trie logs */
public class TrieLogHelper {
  private static final String TRIE_LOG_FILE = "trieLogsToRetain";
  private static final long BATCH_SIZE = 20_000;
  private static final int ROCKSDB_MAX_INSERTS_PER_TRANSACTION = 1000;
  private static final Logger LOG = LoggerFactory.getLogger(TrieLogHelper.class);

  static void prune(
      final DataStorageConfiguration config,
      final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage,
      final MutableBlockchain blockchain,
      final Path dataDirectoryPath) {
    final String batchFileNameBase =
        dataDirectoryPath.resolve(DATABASE_PATH).resolve(TRIE_LOG_FILE).toString();

    validatePruneConfiguration(config);

    final long layersToRetain = config.getUnstable().getBonsaiTrieLogRetentionThreshold();

    final long chainHeight = blockchain.getChainHeadBlockNumber();

    final long lastBlockNumberToRetainTrieLogsFor = chainHeight - layersToRetain + 1;

    if (!validPruneRequirements(blockchain, chainHeight, lastBlockNumberToRetainTrieLogsFor)) {
      return;
    }

    final long numberOfBatches = calculateNumberofBatches(layersToRetain);

    processTrieLogBatches(
        rootWorldStateStorage,
        blockchain,
        chainHeight,
        lastBlockNumberToRetainTrieLogsFor,
        numberOfBatches,
        batchFileNameBase);

    if (rootWorldStateStorage.streamTrieLogKeys(layersToRetain).count() == layersToRetain) {
      deleteFiles(batchFileNameBase, numberOfBatches);
      LOG.info("Prune ran successfully. Enjoy some disk space back! \uD83D\uDE80");
    } else {
      LOG.error("Prune failed. Re-run the subcommand to load the trie logs from file.");
    }
  }

  private static void processTrieLogBatches(
      final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage,
      final MutableBlockchain blockchain,
      final long chainHeight,
      final long lastBlockNumberToRetainTrieLogsFor,
      final long numberOfBatches,
      final String batchFileNameBase) {

    for (long batchNumber = 1; batchNumber <= numberOfBatches; batchNumber++) {

      final long firstBlockOfBatch = chainHeight - ((batchNumber - 1) * BATCH_SIZE);

      final long lastBlockOfBatch =
          Math.max(chainHeight - (batchNumber * BATCH_SIZE), lastBlockNumberToRetainTrieLogsFor);

      final List<Hash> trieLogKeys =
          getTrieLogKeysForBlocks(blockchain, firstBlockOfBatch, lastBlockOfBatch);

      saveTrieLogBatches(batchFileNameBase, rootWorldStateStorage, batchNumber, trieLogKeys);
    }

    LOG.info("Clear trie logs...");
    rootWorldStateStorage.clearTrieLog();

    for (long batchNumber = 1; batchNumber <= numberOfBatches; batchNumber++) {
      restoreTrieLogBatches(rootWorldStateStorage, batchNumber, batchFileNameBase);
    }
  }

  private static void saveTrieLogBatches(
      final String batchFileNameBase,
      final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage,
      final long batchNumber,
      final List<Hash> trieLogKeys) {

    LOG.info("Saving trie logs to retain in file (batch {})...", batchNumber);

    try {
      saveTrieLogsInFile(trieLogKeys, rootWorldStateStorage, batchNumber, batchFileNameBase);
    } catch (IOException e) {
      LOG.error("Error saving trie logs to file: {}", e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private static void restoreTrieLogBatches(
      final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage,
      final long batchNumber,
      final String batchFileNameBase) {

    try {
      LOG.info("Restoring trie logs retained from batch {}...", batchNumber);
      recreateTrieLogs(rootWorldStateStorage, batchNumber, batchFileNameBase);
    } catch (IOException e) {
      LOG.error("Error recreating trie logs from batch {}: {}", batchNumber, e.getMessage());
      throw new RuntimeException(e);
    }
  }

  private static void deleteFiles(final String batchFileNameBase, final long numberOfBatches) {

    LOG.info("Deleting files...");

    for (long batchNumber = 1; batchNumber <= numberOfBatches; batchNumber++) {
      File file = new File(batchFileNameBase + "-" + batchNumber);
      if (file.exists()) {
        file.delete();
      }
    }
  }

  private static List<Hash> getTrieLogKeysForBlocks(
      final MutableBlockchain blockchain,
      final long firstBlockOfBatch,
      final long lastBlockOfBatch) {
    final List<Hash> trieLogKeys = new ArrayList<>();
    for (long i = firstBlockOfBatch; i >= lastBlockOfBatch; i--) {
      final Optional<BlockHeader> header = blockchain.getBlockHeader(i);
      header.ifPresentOrElse(
          blockHeader -> trieLogKeys.add(blockHeader.getHash()),
          () -> LOG.error("Error retrieving block"));
    }
    return trieLogKeys;
  }

  private static long calculateNumberofBatches(final long layersToRetain) {
    return layersToRetain / BATCH_SIZE + ((layersToRetain % BATCH_SIZE == 0) ? 0 : 1);
  }

  private static boolean validPruneRequirements(
      final MutableBlockchain blockchain,
      final long chainHeight,
      final long lastBlockNumberToRetainTrieLogsFor) {
    if (lastBlockNumberToRetainTrieLogsFor < 0) {
      throw new IllegalArgumentException(
          "Trying to retain more trie logs than chain length ("
              + chainHeight
              + "), skipping pruning");
    }

    final Optional<Hash> finalizedBlockHash = blockchain.getFinalized();

    if (finalizedBlockHash.isEmpty()) {
      throw new RuntimeException("No finalized block present, can't safely run trie log prune");
    } else {
      final Hash finalizedHash = finalizedBlockHash.get();
      final Optional<BlockHeader> finalizedBlockHeader = blockchain.getBlockHeader(finalizedHash);
      if (finalizedBlockHeader.isPresent()
          && finalizedBlockHeader.get().getNumber() < lastBlockNumberToRetainTrieLogsFor) {
        throw new IllegalArgumentException(
            "Trying to prune more layers than the finalized block height, skipping pruning");
      }
    }
    return true;
  }

  private static void recreateTrieLogs(
      final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage,
      final long batchNumber,
      final String batchFileNameBase)
      throws IOException {
    // process in chunk to avoid OOM

    IdentityHashMap<byte[], byte[]> trieLogsToRetain =
        readTrieLogsFromFile(batchFileNameBase, batchNumber);
    final int chunkSize = ROCKSDB_MAX_INSERTS_PER_TRANSACTION;
    List<byte[]> keys = new ArrayList<>(trieLogsToRetain.keySet());

    for (int startIndex = 0; startIndex < keys.size(); startIndex += chunkSize) {
      processTransactionChunk(startIndex, chunkSize, keys, trieLogsToRetain, rootWorldStateStorage);
    }
  }

  private static void processTransactionChunk(
      final int startIndex,
      final int chunkSize,
      final List<byte[]> keys,
      final IdentityHashMap<byte[], byte[]> trieLogsToRetain,
      final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage) {

    var updater = rootWorldStateStorage.updater();
    int endIndex = Math.min(startIndex + chunkSize, keys.size());

    for (int i = startIndex; i < endIndex; i++) {
      byte[] key = keys.get(i);
      byte[] value = trieLogsToRetain.get(key);
      updater.getTrieLogStorageTransaction().put(key, value);
      LOG.info("Key({}): {}", i, Bytes32.wrap(key).toShortHexString());
    }

    updater.getTrieLogStorageTransaction().commit();
  }

  private static void validatePruneConfiguration(final DataStorageConfiguration config) {
    checkArgument(
        config.getUnstable().getBonsaiTrieLogRetentionThreshold()
            >= config.getBonsaiMaxLayersToLoad(),
        String.format(
            "--Xbonsai-trie-log-retention-threshold minimum value is %d",
            config.getBonsaiMaxLayersToLoad()));
    checkArgument(
        config.getUnstable().getBonsaiTrieLogPruningLimit() > 0,
        String.format(
            "--Xbonsai-trie-log-pruning-limit=%d must be greater than 0",
            config.getUnstable().getBonsaiTrieLogPruningLimit()));
    checkArgument(
        config.getUnstable().getBonsaiTrieLogPruningLimit()
            > config.getUnstable().getBonsaiTrieLogRetentionThreshold(),
        String.format(
            "--Xbonsai-trie-log-pruning-limit=%d must greater than --Xbonsai-trie-log-retention-threshold=%d",
            config.getUnstable().getBonsaiTrieLogPruningLimit(),
            config.getUnstable().getBonsaiTrieLogRetentionThreshold()));
  }

  private static void saveTrieLogsInFile(
      final List<Hash> trieLogsKeys,
      final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage,
      final long batchNumber,
      final String batchFileNameBase)
      throws IOException {

    File file = new File(batchFileNameBase + "-" + batchNumber);
    if (file.exists()) {
      LOG.error("File already exists, skipping file creation");
      return;
    }

    try (FileOutputStream fos = new FileOutputStream(file)) {
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      oos.writeObject(getTrieLogs(trieLogsKeys, rootWorldStateStorage));
    } catch (IOException e) {
      LOG.error(e.getMessage());
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("unchecked")
  private static IdentityHashMap<byte[], byte[]> readTrieLogsFromFile(
      final String batchFileNameBase, final long batchNumber) {

    IdentityHashMap<byte[], byte[]> trieLogs;
    try (FileInputStream fis = new FileInputStream(batchFileNameBase + "-" + batchNumber);
        ObjectInputStream ois = new ObjectInputStream(fis)) {

      trieLogs = (IdentityHashMap<byte[], byte[]>) ois.readObject();

    } catch (IOException | ClassNotFoundException e) {

      LOG.error(e.getMessage());
      throw new RuntimeException(e);
    }

    return trieLogs;
  }

  private static IdentityHashMap<byte[], byte[]> getTrieLogs(
      final List<Hash> trieLogKeys, final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage) {
    IdentityHashMap<byte[], byte[]> trieLogsToRetain = new IdentityHashMap<>();

    LOG.info("Obtaining trielogs from db, this may take a few minutes...");
    trieLogKeys.forEach(
        hash ->
            rootWorldStateStorage
                .getTrieLog(hash)
                .ifPresent(trieLog -> trieLogsToRetain.put(hash.toArrayUnsafe(), trieLog)));
    return trieLogsToRetain;
  }

  static TrieLogCount getCount(
      final DiffBasedWorldStateKeyValueStorage rootWorldStateStorage,
      final int limit,
      final Blockchain blockchain) {
    final AtomicInteger total = new AtomicInteger();
    final AtomicInteger canonicalCount = new AtomicInteger();
    final AtomicInteger forkCount = new AtomicInteger();
    final AtomicInteger orphanCount = new AtomicInteger();
    rootWorldStateStorage
        .streamTrieLogKeys(limit)
        .map(Bytes32::wrap)
        .map(Hash::wrap)
        .forEach(
            hash -> {
              total.getAndIncrement();
              blockchain
                  .getBlockHeader(hash)
                  .ifPresentOrElse(
                      (header) -> {
                        long number = header.getNumber();
                        final Optional<BlockHeader> headerByNumber =
                            blockchain.getBlockHeader(number);
                        if (headerByNumber.isPresent()
                            && headerByNumber.get().getHash().equals(hash)) {
                          canonicalCount.getAndIncrement();
                        } else {
                          forkCount.getAndIncrement();
                        }
                      },
                      orphanCount::getAndIncrement);
            });

    return new TrieLogCount(total.get(), canonicalCount.get(), forkCount.get(), orphanCount.get());
  }

  static void printCount(final PrintWriter out, final TrieLogCount count) {
    out.printf(
        "trieLog count: %s\n - canonical count: %s\n - fork count: %s\n - orphaned count: %s\n",
        count.total, count.canonicalCount, count.forkCount, count.orphanCount);
  }

  record TrieLogCount(int total, int canonicalCount, int forkCount, int orphanCount) {}
}
