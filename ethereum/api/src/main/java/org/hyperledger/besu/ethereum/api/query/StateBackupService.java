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

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public class StateBackupService {

  private static final Logger LOG = LogManager.getLogger();
  private static final long MAX_FILE_SIZE = 1 << 30; // 1 GiB max file size

  private final Lock submissionLock = new ReentrantLock();
  private final EthScheduler scheduler;
  private final Blockchain blockchain;
  private final WorldStateStorage worldStateStorage;
  private final BackupStatus backupStatus = new BackupStatus();

  private Path backupDir;
  private RollingFileWriter leafFileWriter;

  public StateBackupService(
      final Blockchain blockchain,
      final Path backupDir,
      final EthScheduler scheduler,
      final WorldStateStorage worldStateStorage) {
    this.blockchain = blockchain;
    this.backupDir = backupDir;
    this.scheduler = scheduler;
    this.worldStateStorage = worldStateStorage;
  }

  public Path getBackupDir() {
    return backupDir;
  }

  public BackupStatus requestBackup(final long block, final Optional<Path> backupDir) {
    boolean requestAccepted = false;
    try {
      if (submissionLock.tryLock(100, TimeUnit.MILLISECONDS)) {
        try {
          if (!backupStatus.isBackingUp()) {
            requestAccepted = true;
            this.backupDir = backupDir.orElse(this.backupDir);
            backupStatus.targetBlock = block;
            backupStatus.currentAccount = Bytes32.ZERO;
            scheduler.scheduleComputationTask(
                () -> {
                  try {
                    return backup(block);

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

  private File nextLeafFile(final int fileNumber) {
    return backupDir
        .resolve("besu-leaf-backup-" + backupStatus.targetBlock + "-" + fileNumber + ".backup")
        .toFile();
  }

  private File nextHeaderFile(final int fileNumber) {
    return backupDir.resolve("besu-header-backup-" + fileNumber + ".rdat").toFile();
  }

  private File nextBodyFile(final int fileNumber) {
    return backupDir.resolve("besu-body-backup-" + fileNumber + ".rdat").toFile();
  }

  private File nextReceiptFile(final int fileNumber) {
    return backupDir.resolve("besu-receipt-backup-" + fileNumber + ".rdat").toFile();
  }

  private BackupStatus backup(final long block) throws IOException {
    try {
      checkArgument(
          block >= 0 && block <= blockchain.getChainHeadBlockNumber(),
          "Backup Block must be within blockchain");
      backupStatus.targetBlock = block;
      final Optional<BlockHeader> header = blockchain.getBlockHeader(backupStatus.targetBlock);
      if (header.isEmpty()) {
        backupStatus.currentAccount = null;
        return backupStatus;
      }
      final Optional<Bytes> worldStateRoot =
          worldStateStorage.getAccountStateTrieNode(header.get().getStateRoot());
      if (worldStateRoot.isEmpty()) {
        backupStatus.currentAccount = null;
        return backupStatus;
      }
      backupStatus.currentAccount = Bytes32.ZERO;

      backupChaindata(block);

      backupLeaves(header.get());

      return backupStatus;
    } catch (final Throwable t) {
      LOG.error("Unexpected error", t);
      throw t;
    }
  }

  private void backupLeaves(final BlockHeader header) throws IOException {
    try (final RollingFileWriter leafFileWriter = new RollingFileWriter(this::nextLeafFile)) {
      this.leafFileWriter = leafFileWriter;

      final StoredMerklePatriciaTrie<Bytes32, Bytes> accountTrie =
          new StoredMerklePatriciaTrie<>(
              worldStateStorage::getAccountStateTrieNode,
              header.getStateRoot(),
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

    final Bytes code = worldStateStorage.getCode(account.getCodeHash()).orElse(Bytes.EMPTY);
    backupStatus.codeSize.addAndGet(code.size());

    final BytesValueRLPOutput accountOutput = new BytesValueRLPOutput();
    accountOutput.startList();
    accountOutput.writeBytes(nodeKey); // trie hash
    accountOutput.writeBytes(nodeValue); // account rlp
    accountOutput.writeBytes(code); // code
    accountOutput.startList(); // storage

    final StoredMerklePatriciaTrie<Bytes32, Bytes> storageTrie =
        new StoredMerklePatriciaTrie<>(
            worldStateStorage::getAccountStateTrieNode,
            account.getStorageRoot(),
            Function.identity(),
            Function.identity());
    storageTrie.visitLeafs(
        (storageKey, storageValue) -> visitAccountStorage(storageKey, storageValue, accountOutput));
    accountOutput.endList();
    accountOutput.endList();

    try {
      leafFileWriter.writeBytes(accountOutput.encoded().toArrayUnsafe());
    } catch (final IOException ioe) {
      LOG.error("Failure writing backup", ioe);
      return State.STOP;
    }

    backupStatus.accountCount.incrementAndGet();
    return State.CONTINUE;
  }

  private void backupChaindata(final long endBlock) throws IOException {
    try (final RollingFileWriter headerWriter = new RollingFileWriter(this::nextHeaderFile);
        final RollingFileWriter bodyWriter = new RollingFileWriter(this::nextBodyFile);
        final RollingFileWriter receiptsWriter = new RollingFileWriter(this::nextReceiptFile)) {
      for (int blockNumber = 0; blockNumber < endBlock; blockNumber++) {
        final Optional<Block> block = blockchain.getBlockByNumber(blockNumber);
        if (block.isEmpty()) {
          throw new IllegalStateException(
              "Block data for " + blockNumber + " was not found in the archive");
        }
        final Optional<List<TransactionReceipt>> receipts =
            blockchain.getTxReceipts(block.get().getHash());
        if (receipts.isEmpty()) {
          throw new IllegalStateException(
              "Receipts for " + blockNumber + " was not found in the archive");
        }

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
      final Bytes32 nodeKey, final Node<Bytes> node, final BytesValueRLPOutput output) {
    backupStatus.currentStorage = nodeKey;

    output.startList();
    output.writeBytes(nodeKey);
    output.writeBytes(node.getValue().orElse(Bytes.EMPTY));
    output.endList();

    backupStatus.storageCount.incrementAndGet();
    return State.CONTINUE;
  }

  static class RollingFileWriter implements Closeable {
    int currentSize;
    int fileNumber;
    final Function<Integer, File> filenameGenerator;
    FileOutputStream out;

    RollingFileWriter(final Function<Integer, File> filenameGenerator)
        throws FileNotFoundException {
      this.filenameGenerator = filenameGenerator;
      currentSize = 0;
      fileNumber = 1;
      out = new FileOutputStream(filenameGenerator.apply(fileNumber));
    }

    void writeBytes(final byte[] bytes) throws IOException {
      currentSize += bytes.length;
      if (currentSize > MAX_FILE_SIZE) {
        out.close();
        out = new FileOutputStream(filenameGenerator.apply(++fileNumber));
        currentSize = bytes.length;
      }
      out.write(bytes);
    }

    @Override
    public void close() throws IOException {
      out.close();
    }
  }

  public static final class BackupStatus {
    long targetBlock;
    long storedBlock;
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
