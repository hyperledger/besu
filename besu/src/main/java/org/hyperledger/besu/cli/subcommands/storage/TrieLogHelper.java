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
import static org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration.Unstable.MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
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
  private static final String trieLogFile = "trieLogsToRetain.txt";
  private static final Logger LOG = LoggerFactory.getLogger(TrieLogHelper.class);

  static void prune(
      final PrintWriter out,
      final DataStorageConfiguration config,
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final MutableBlockchain blockchain) {

    TrieLogHelper.validatePruneConfiguration(config);
    final long layersToRetain = config.getUnstable().getBonsaiTrieLogRetentionThreshold();
    final long chainHeight = blockchain.getChainHeadBlockNumber();
    final long lastBlockNumberToRetainTrieLogsFor = chainHeight - layersToRetain;
    if (lastBlockNumberToRetainTrieLogsFor < 0) {
      LOG.error(
          "Trying to retain more trie logs than chain height ({}), skipping pruning", chainHeight);
      return;
    }
    final Optional<Hash> finalizedBlockHash = blockchain.getFinalized();

    if (finalizedBlockHash.isEmpty()) {
      LOG.error("No finalized block present, skipping pruning");
      return;
    } else {
      if (blockchain.getBlockHeader(finalizedBlockHash.get()).get().getNumber()
          < lastBlockNumberToRetainTrieLogsFor) {
        LOG.error("Trying to prune more layers than the finalized block height, skipping pruning");
        return;
      }
    }

    // retrieve the layersToRetains hashes from blockchain
    final List<Hash> trieLogKeys = new ArrayList<>();

    for (long i = chainHeight; i > lastBlockNumberToRetainTrieLogsFor; i--) {
      final Optional<BlockHeader> header = blockchain.getBlockHeader(i);
      header.ifPresentOrElse(
          blockHeader -> trieLogKeys.add(blockHeader.getHash()),
          () -> LOG.error("Error retrieving block"));
    }

    IdentityHashMap<byte[], byte[]> trieLogsToRetain;

    // TODO: maybe stop the method here if we don't find enough hashes to retain
    if ((long) trieLogKeys.size() == layersToRetain) {
      trieLogsToRetain = new IdentityHashMap<>();
      // save trielogs in a flatfile in case something goes wrong
      out.println("Obtaining trielogs to retain...");
      trieLogKeys.forEach(
          hash -> {
            rootWorldStateStorage
                .getTrieLog(hash)
                .ifPresent(trieLog -> trieLogsToRetain.put(hash.toArrayUnsafe(), trieLog));
          });
      out.println("Saving trielogs to retain in file...");
      try {
        saveTrieLogsInFile(trieLogsToRetain);
      } catch (IOException e) {
        LOG.error("Error saving trielogs to file: {}", e.getMessage());
        return;
      }
    } else {
      // in case something went wrong and we already pruned trielogs
      // users can re-un the subcommand and we will read trielogs from file
      try {
        trieLogsToRetain = readTrieLogsFromFile();
      } catch (Exception e) {
        LOG.error("Error reading trielogs from file: {}", e.getMessage());
        return;
      }
    }

    if (trieLogsToRetain.size() == layersToRetain) {
      out.println("Clear trielogs...");
      rootWorldStateStorage.clearTrieLog();
      out.println("Restoring trielogs retained into db...");
      recreateTrieLogs(rootWorldStateStorage, trieLogsToRetain);
    }

    if (rootWorldStateStorage.streamTrieLogKeys(layersToRetain).count() == layersToRetain) {
      out.println("Prune ran successfully. Deleting file...");
      deleteTrieLogFile();
      out.println("Enjoy some GBs of storage back!");
    } else {
      out.println("Prune failed. Re-run the subcommand to load the trielogs from file.");
    }
  }

  private static void recreateTrieLogs(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
      final IdentityHashMap<byte[], byte[]> trieLogsToRetain) {
    // process in chunk to avoid OOM
    final int chunkSize = 1000;
    List<byte[]> keys = new ArrayList<>(trieLogsToRetain.keySet());

    for (int startIndex = 0; startIndex < keys.size(); startIndex += chunkSize) {
      processChunk(startIndex, chunkSize, keys, trieLogsToRetain, rootWorldStateStorage);
    }
  }

  private static void processChunk(
      final int startIndex,
      final int chunkSize,
      final List<byte[]> keys,
      final IdentityHashMap<byte[], byte[]> trieLogsToRetain,
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage) {

    var updater = rootWorldStateStorage.updater();
    int endIndex = Math.min(startIndex + chunkSize, keys.size());

    for (int i = startIndex; i < endIndex; i++) {
      byte[] key = keys.get(i);
      byte[] value = trieLogsToRetain.get(key);
      updater.getTrieLogStorageTransaction().put(key, value);
      LOG.info("Key({}): {}", i, Bytes32.wrap(key));
    }

    updater.getTrieLogStorageTransaction().commit();
  }

  private static void validatePruneConfiguration(final DataStorageConfiguration config) {
    checkArgument(
        config.getUnstable().getBonsaiTrieLogRetentionThreshold()
            >= MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD,
        String.format(
            "--Xbonsai-trie-log-retention-threshold minimum value is %d",
            MINIMUM_BONSAI_TRIE_LOG_RETENTION_THRESHOLD));
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

  static TrieLogCount getCount(
      final BonsaiWorldStateKeyValueStorage rootWorldStateStorage,
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

  private static void saveTrieLogsInFile(final IdentityHashMap<byte[], byte[]> trieLogs)
      throws IOException {

    File file = new File(trieLogFile);
    if (file.exists()) {
      LOG.error("File {} already exists, something went terribly wrong", trieLogFile);
    }
    try (FileOutputStream fos = new FileOutputStream(trieLogFile)) {
      try {
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(trieLogs);
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    } catch (IOException e) {
      LOG.error(e.getMessage());
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  private static IdentityHashMap<byte[], byte[]> readTrieLogsFromFile()
      throws IOException, ClassNotFoundException {

    IdentityHashMap<byte[], byte[]> trieLogs;
    try (FileInputStream fis = new FileInputStream(trieLogFile);
        ObjectInputStream ois = new ObjectInputStream(fis)) {

      trieLogs = (IdentityHashMap<byte[], byte[]>) ois.readObject();

    } catch (IOException | ClassNotFoundException e) {

      LOG.error(e.getMessage());
      throw e;
    }

    return trieLogs;
  }

  private static void deleteTrieLogFile() {
    File file = new File(trieLogFile);
    if (file.exists()) {
      file.delete();
    }
  }

  static void printCount(final PrintWriter out, final TrieLogCount count) {
    out.printf(
        "trieLog count: %s\n - canonical count: %s\n - fork count: %s\n - orphaned count: %s\n",
        count.total, count.canonicalCount, count.forkCount, count.orphanCount);
  }

  record TrieLogCount(int total, int canonicalCount, int forkCount, int orphanCount) {}
}
