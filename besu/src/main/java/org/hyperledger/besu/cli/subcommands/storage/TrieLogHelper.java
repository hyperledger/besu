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

import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Splitter;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.util.encoders.Base64;
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
      final MutableBlockchain blockchain) throws IOException {

    TrieLogHelper.validatePruneConfiguration(config);
    final long layersToRetain = config.getUnstable().getBonsaiTrieLogRetentionThreshold();
    final long chainHeight = blockchain.getChainHeadBlockNumber();
    final long lastBlockToRetainTrieLogsFor = chainHeight - layersToRetain;
    final Optional<Hash> finalizedBlockHash = blockchain.getFinalized();
    if (finalizedBlockHash.isEmpty()) {
      LOG.error("No finalized block present, skipping pruning");
      return;
    } else {
      if (blockchain.getBlockHeader(finalizedBlockHash.get()).get().getNumber()
          < lastBlockToRetainTrieLogsFor) {
        LOG.error("Trying to prune more layers than the finalized block height, skipping pruning");
        return;
      }
    }

    // retrieve the layersToRetains hashes from blockchain
    final List<Hash> hashesToRetain = new ArrayList<>();

    for (long i = chainHeight; i > lastBlockToRetainTrieLogsFor; i--) {
      final Optional<BlockHeader> header = blockchain.getBlockHeader(i);
      header.ifPresent(blockHeader -> hashesToRetain.add(blockHeader.getHash()));
    }

    IdentityHashMap<byte[], byte[]> trieLogsToRetain;
    if ((long) hashesToRetain.size() == layersToRetain) {
      trieLogsToRetain = new IdentityHashMap<>();
      // save trielogs in a flatfile as a fail-safe
      out.println("Obtaining trielogs to retain...");
      hashesToRetain.forEach(
          hash -> {
            rootWorldStateStorage
                .getTrieLog(hash)
                .ifPresent(trieLog -> trieLogsToRetain.put(hash.toArrayUnsafe(), trieLog));
          });
      out.println("Saving trielogs to retain in file...");
      saveTrieLogsInFile(trieLogsToRetain);
    } else {
      // try to read the triLogs from the flatfile
      trieLogsToRetain = readTrieLogsFromFile();
    }
    out.println("Clear trielogs...");
    // clear trielogs storage
    rootWorldStateStorage.clearTrieLog();

    // get an update and insert the trielogs we retained
    var updater = rootWorldStateStorage.updater();
    out.println("restore trielogs retained into db...");
    trieLogsToRetain.forEach(
        (key, value) -> {
          updater.getTrieLogStorageTransaction().put(key, value);
        });
    updater.getTrieLogStorageTransaction().commit();

    if (rootWorldStateStorage.streamTrieLogKeys(layersToRetain).count() == layersToRetain) {
      out.println("Prune ran successfully. Deleting file...");
      deleteTrieLogFile();
    }
    out.println("Enjoy some GBs of storage back!...");
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

  private static void saveTrieLogsInFile(final Map<byte[], byte[]> trieLogs) throws IOException {

    File file = new File(trieLogFile);

      try (BufferedWriter bf = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
          for (Map.Entry<byte[], byte[]> entry : trieLogs.entrySet()) {
              bf.write(Bytes.of(entry.getKey()) + ":" + Base64.toBase64String(entry.getValue()));
              bf.newLine();
          }
          bf.flush();
      } catch (IOException e) {
          LOG.error(e.getMessage());
          throw e;
      }
  }

  private static IdentityHashMap<byte[], byte[]> readTrieLogsFromFile() throws IOException {

    File file = new File(trieLogFile);
    IdentityHashMap<byte[], byte[]> trieLogs = new IdentityHashMap<>();
      try (BufferedReader br = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
          String line;
          while ((line = br.readLine()) != null) {
              List<String> parts = Splitter.on(':').splitToList(line);
              byte[] key = Bytes.fromHexString(parts.get(0)).toArrayUnsafe();
              byte[] value = Base64.decode(parts.get(1));
              trieLogs.put(key, value);
          }
      } catch (IOException e) {
          LOG.error(e.getMessage());
          throw e;
      }

    return trieLogs;
  }

  private static void deleteTrieLogFile() {
    File file = new File(trieLogFile);
    file.delete();
  }

  static void printCount(final PrintWriter out, final TrieLogCount count) {
    out.printf(
        "trieLog count: %s\n - canonical count: %s\n - fork count: %s\n - orphaned count: %s\n",
        count.total, count.canonicalCount, count.forkCount, count.orphanCount);
  }

  record TrieLogCount(int total, int canonicalCount, int forkCount, int orphanCount) {}
}
