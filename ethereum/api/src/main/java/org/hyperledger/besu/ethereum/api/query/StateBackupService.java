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
 *
 */

package org.hyperledger.besu.ethereum.api.query;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.StoredMerklePatriciaTrie;
import org.hyperledger.besu.ethereum.trie.TrieIterator;
import org.hyperledger.besu.ethereum.trie.TrieIterator.State;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;
import org.hyperledger.besu.plugin.data.Hash;
import org.hyperledger.besu.util.io.RollingFileWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StateBackupService {

  private static final Logger LOG = LoggerFactory.getLogger(StateBackupService.class);
  private static final Bytes ACCOUNT_END_MARKER;

  static {
    final BytesValueRLPOutput endMarker = new BytesValueRLPOutput();
    endMarker.startList();
    endMarker.endList();
    ACCOUNT_END_MARKER = endMarker.encoded();
  }

  private final String besuVersion;
  private final Lock submissionLock = new ReentrantLock();
  private final EthScheduler scheduler;
  private final Blockchain blockchain;
  private final WorldStateStorage worldStateStorage;
  private final BackupStatus backupStatus = new BackupStatus();

  private Path backupDir;
  private RollingFileWriter accountFileWriter;

  public StateBackupService(
      final String besuVersion,
      final Blockchain blockchain,
      final Path backupDir,
      final EthScheduler scheduler,
      final WorldStateStorage worldStateStorage) {
    this.besuVersion = besuVersion;
    this.blockchain = blockchain;
    this.backupDir = backupDir;
    this.scheduler = scheduler;
    this.worldStateStorage = worldStateStorage;
  }

  public Path getBackupDir() {
    return backupDir;
  }

  public BackupStatus requestBackup(
      final long block, final boolean compress, final Optional<Path> backupDir) {
    boolean requestAccepted = false;
    try {
      if (submissionLock.tryLock(100, TimeUnit.MILLISECONDS)) {
        try {
          if (!backupStatus.isBackingUp()) {
            requestAccepted = true;
            this.backupDir = backupDir.orElse(this.backupDir);
            backupStatus.targetBlock = block;
            backupStatus.compressed = compress;
            backupStatus.currentAccount = Bytes32.ZERO;
            scheduler.scheduleComputationTask(
                () -> {
                  try {
                    return backup(block, compress);

                  } catch (final IOException ioe) {
                    LOG.error("Error writing backups", ioe);
                    return backupStatus;
                  }
                });
          }
        } finally {
          submissionLock.unlock();
        }
      }
    } catch (final InterruptedException e) {
      // ignore
    }
    backupStatus.requestAccepted = requestAccepted;
    return backupStatus;
  }

  public static Path dataFileToIndex(final Path dataName) {
    return Path.of(dataName.toString().replaceAll("(.*)[-.]\\d\\d\\d\\d\\.(.)dat", "$1.$2idx"));
  }

  public static Path accountFileName(
      final Path backupDir,
      final long targetBlock,
      final int fileNumber,
      final boolean compressed) {
    return backupDir.resolve(
        String.format(
            "besu-account-backup-%08d-%04d.%sdat",
            targetBlock, fileNumber, compressed ? "c" : "r"));
  }

  public static Path headerFileName(
      final Path backupDir, final int fileNumber, final boolean compressed) {
    return backupDir.resolve(
        String.format("besu-header-backup-%04d.%sdat", fileNumber, compressed ? "c" : "r"));
  }

  public static Path bodyFileName(
      final Path backupDir, final int fileNumber, final boolean compressed) {
    return backupDir.resolve(
        String.format("besu-body-backup-%04d.%sdat", fileNumber, compressed ? "c" : "r"));
  }

  public static Path receiptFileName(
      final Path backupDir, final int fileNumber, final boolean compressed) {
    return backupDir.resolve(
        String.format("besu-receipt-backup-%04d.%sdat", fileNumber, compressed ? "c" : "r"));
  }

  private Path accountFileName(final int fileNumber, final boolean compressed) {
    return accountFileName(backupDir, backupStatus.targetBlock, fileNumber, compressed);
  }

  private Path headerFileName(final int fileNumber, final boolean compressed) {
    return headerFileName(backupDir, fileNumber, compressed);
  }

  private Path bodyFileName(final int fileNumber, final boolean compressed) {
    return bodyFileName(backupDir, fileNumber, compressed);
  }

  private Path receiptFileName(final int fileNumber, final boolean compressed) {
    return receiptFileName(backupDir, fileNumber, compressed);
  }

  private BackupStatus backup(final long block, final boolean compress) throws IOException {
    checkArgument(
        block >= 0 && block <= blockchain.getChainHeadBlockNumber(),
        "Backup Block must be within blockchain");
    backupStatus.targetBlock = block;
    backupStatus.compressed = compress;
    backupStatus.currentAccount = Bytes32.ZERO;

    backupChainData();
    backupLeaves();

    writeManifest();

    return backupStatus;
  }

  private void writeManifest() throws IOException {
    final Map<String, Object> manifest = new HashMap<>();
    manifest.put("clientVersion", besuVersion);
    manifest.put("compressed", backupStatus.compressed);
    manifest.put("targetBlock", backupStatus.targetBlock);
    manifest.put("accountCount", backupStatus.accountCount);

    Files.write(
        backupDir.resolve("besu-backup-manifest.json"),
        JsonUtil.getJson(manifest).getBytes(StandardCharsets.UTF_8));
  }

  private void backupLeaves() throws IOException {
    final Optional<BlockHeader> header = blockchain.getBlockHeader(backupStatus.targetBlock);
    if (header.isEmpty()) {
      backupStatus.currentAccount = null;
      return;
    }
    final Optional<Bytes> worldStateRoot =
        worldStateStorage.getAccountStateTrieNode(Bytes.EMPTY, header.get().getStateRoot());
    if (worldStateRoot.isEmpty()) {
      backupStatus.currentAccount = null;
      return;
    }

    try (final RollingFileWriter accountFileWriter =
        new RollingFileWriter(this::accountFileName, backupStatus.compressed)) {
      this.accountFileWriter = accountFileWriter;

      final StoredMerklePatriciaTrie<Bytes32, Bytes> accountTrie =
          new StoredMerklePatriciaTrie<>(
              worldStateStorage::getAccountStateTrieNode,
              header.get().getStateRoot(),
              Function.identity(),
              Function.identity());

      accountTrie.visitLeafs(this::visitAccount);
      backupStatus.currentAccount = null;
    }
  }

  private TrieIterator.State visitAccount(final Bytes32 nodeKey, final Node<Bytes> node) {
    if (node.getValue().isEmpty()) {
      return State.CONTINUE;
    }

    backupStatus.currentAccount = nodeKey;
    final Bytes nodeValue = node.getValue().orElse(Hash.EMPTY);
    final StateTrieAccountValue account =
        StateTrieAccountValue.readFrom(new BytesValueRLPInput(nodeValue, false));

    final Bytes code = worldStateStorage.getCode(account.getCodeHash(), null).orElse(Bytes.EMPTY);
    backupStatus.codeSize.addAndGet(code.size());

    final BytesValueRLPOutput accountOutput = new BytesValueRLPOutput();
    accountOutput.startList();
    accountOutput.writeBytes(nodeKey); // trie hash
    accountOutput.writeBytes(nodeValue); // account rlp
    accountOutput.writeBytes(code); // code
    accountOutput.endList();

    try {
      accountFileWriter.writeBytes(accountOutput.encoded().toArrayUnsafe());
    } catch (final IOException ioe) {
      LOG.error("Failure writing backup", ioe);
      return State.STOP;
    }

    // storage is written for each leaf, otherwise the whole trie would have to fit in memory
    final StoredMerklePatriciaTrie<Bytes32, Bytes> storageTrie =
        new StoredMerklePatriciaTrie<>(
            worldStateStorage::getAccountStateTrieNode,
            account.getStorageRoot(),
            Function.identity(),
            Function.identity());
    storageTrie.visitLeafs(
        (storageKey, storageValue) ->
            visitAccountStorage(storageKey, storageValue, accountFileWriter));

    try {
      accountFileWriter.writeBytes(ACCOUNT_END_MARKER.toArrayUnsafe());
    } catch (final IOException ioe) {
      LOG.error("Failure writing backup", ioe);
      return State.STOP;
    }

    backupStatus.accountCount.incrementAndGet();
    return State.CONTINUE;
  }

  private void backupChainData() throws IOException {
    try (final RollingFileWriter headerWriter =
            new RollingFileWriter(this::headerFileName, backupStatus.compressed);
        final RollingFileWriter bodyWriter =
            new RollingFileWriter(this::bodyFileName, backupStatus.compressed);
        final RollingFileWriter receiptsWriter =
            new RollingFileWriter(this::receiptFileName, backupStatus.compressed)) {
      for (long blockNumber = 0; blockNumber <= backupStatus.targetBlock; blockNumber++) {
        final Optional<Block> block = blockchain.getBlockByNumber(blockNumber);
        checkState(
            block.isPresent(), "Block data for %s was not found in the archive", blockNumber);

        final Optional<List<TransactionReceipt>> receipts =
            blockchain.getTxReceipts(block.get().getHash());
        checkState(
            receipts.isPresent(), "Receipts for %s was not found in the archive", blockNumber);

        final BytesValueRLPOutput headerOutput = new BytesValueRLPOutput();
        block.get().getHeader().writeTo(headerOutput);
        headerWriter.writeBytes(headerOutput.encoded().toArrayUnsafe());

        final BytesValueRLPOutput bodyOutput = new BytesValueRLPOutput();
        block.get().getBody().writeTo(bodyOutput);
        bodyWriter.writeBytes(bodyOutput.encoded().toArrayUnsafe());

        final BytesValueRLPOutput receiptsOutput = new BytesValueRLPOutput();
        receiptsOutput.writeList(receipts.get(), TransactionReceipt::writeToWithRevertReason);
        receiptsWriter.writeBytes(receiptsOutput.encoded().toArrayUnsafe());

        backupStatus.storedBlock = blockNumber;
      }
    }
  }

  private TrieIterator.State visitAccountStorage(
      final Bytes32 nodeKey, final Node<Bytes> node, final RollingFileWriter accountFileWriter) {
    backupStatus.currentStorage = nodeKey;

    final BytesValueRLPOutput output = new BytesValueRLPOutput();
    output.startList();
    output.writeBytes(nodeKey);
    output.writeBytes(node.getValue().orElse(Bytes.EMPTY));
    output.endList();

    try {
      accountFileWriter.writeBytes(output.encoded().toArrayUnsafe());
    } catch (final IOException ioe) {
      LOG.error("Failure writing backup", ioe);
      return State.STOP;
    }

    backupStatus.storageCount.incrementAndGet();
    return State.CONTINUE;
  }

  public static final class BackupStatus {
    long targetBlock;
    long storedBlock;
    boolean compressed;
    Bytes32 currentAccount;
    Bytes32 currentStorage;
    AtomicLong accountCount = new AtomicLong(0);
    AtomicLong codeSize = new AtomicLong(0);
    AtomicLong storageCount = new AtomicLong(0);
    boolean requestAccepted;

    @JsonGetter
    public String getTargetBlock() {
      return "0x" + Long.toHexString(targetBlock);
    }

    @JsonGetter
    public String getStoredBlock() {
      return "0x" + Long.toHexString(storedBlock);
    }

    @JsonGetter
    public String getCurrentAccount() {
      return currentAccount.toHexString();
    }

    @JsonGetter
    public String getCurrentStorage() {
      return currentStorage.toHexString();
    }

    @JsonGetter
    public boolean isBackingUp() {
      return currentAccount != null;
    }

    @JsonIgnore
    public long getAccountCount() {
      return accountCount.get();
    }

    @JsonIgnore
    public long getCodeSize() {
      return codeSize.get();
    }

    @JsonIgnore
    public long getStorageCount() {
      return storageCount.get();
    }

    @JsonIgnore
    public Bytes getCurrentAccountBytes() {
      return currentAccount;
    }

    @JsonIgnore
    public long getStoredBlockNum() {
      return storedBlock;
    }

    @JsonIgnore
    public long getTargetBlockNum() {
      return targetBlock;
    }
  }
}
