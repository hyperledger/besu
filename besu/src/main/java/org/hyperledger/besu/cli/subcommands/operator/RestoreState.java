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

package org.hyperledger.besu.cli.subcommands.operator;

import static org.hyperledger.besu.cli.DefaultCommandValues.MANDATORY_LONG_FORMAT_HELP;
import static org.hyperledger.besu.ethereum.trie.CompactEncoding.bytesToPath;

import org.hyperledger.besu.config.JsonUtil;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.ethereum.api.query.StateBackupService;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.trie.Node;
import org.hyperledger.besu.ethereum.trie.PersistVisitor;
import org.hyperledger.besu.ethereum.trie.RestoreVisitor;
import org.hyperledger.besu.ethereum.worldstate.DefaultWorldStateArchive;
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;
import org.hyperledger.besu.ethereum.worldstate.WorldStateStorage;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.xerial.snappy.Snappy;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

@Command(
    name = "x-restore-state",
    description = "Restores the chain from a previously generated backup-state.",
    mixinStandardHelpOptions = true)
public class RestoreState implements Runnable {

  private static final Logger LOG = LogManager.getLogger();

  @Option(
      names = "--backup-path",
      required = true,
      paramLabel = MANDATORY_LONG_FORMAT_HELP,
      description = "The path to store the backup files.",
      arity = "1..1")
  private final Path backupDir = null;

  @ParentCommand private OperatorSubCommand parentCommand;

  private static final int TRIE_NODE_COMMIT_BATCH_SIZE = 100;

  private long targetBlock;
  private long accountCount;
  private long trieNodeCount;
  private boolean compressed;
  private BesuController besuController;
  private WorldStateStorage.Updater updater;

  private Path accountFileName(final int fileNumber, final boolean compressed) {
    return StateBackupService.accountFileName(backupDir, targetBlock, fileNumber, compressed);
  }

  private Path headerFileName(final int fileNumber, final boolean compressed) {
    return StateBackupService.headerFileName(backupDir, fileNumber, compressed);
  }

  private Path bodyFileName(final int fileNumber, final boolean compressed) {
    return StateBackupService.bodyFileName(backupDir, fileNumber, compressed);
  }

  private Path receiptFileName(final int fileNumber, final boolean compressed) {
    return StateBackupService.receiptFileName(backupDir, fileNumber, compressed);
  }

  @Override
  public void run() {
    try {
      final ObjectNode manifest =
          JsonUtil.objectNodeFromString(
              Files.readString(backupDir.resolve("besu-backup-manifest.json")));

      compressed = manifest.get("compressed").asBoolean(false);
      targetBlock = manifest.get("targetBlock").asLong();
      accountCount = manifest.get("accountCount").asLong();
      besuController = createBesuController();

      restoreBlocks();
      restoreAccounts();

      LOG.info("Restore complete");

    } catch (final IOException e) {
      LOG.error("Error restoring state", e);
    }
  }

  private void restoreBlocks() throws IOException {
    try (final RollingFileReader headerReader =
            new RollingFileReader(this::headerFileName, compressed);
        final RollingFileReader bodyReader = new RollingFileReader(this::bodyFileName, compressed);
        final RollingFileReader receiptReader =
            new RollingFileReader(this::receiptFileName, compressed)) {
      final MutableBlockchain blockchain = besuController.getProtocolContext().getBlockchain();
      // target block is "including" the target block, so LE test not LT.
      for (int i = 0; i <= targetBlock; i++) {
        if (i % 100000 == 0) {
          LOG.info("Loading chain data {} / {}", i, targetBlock);
        }

        final byte[] headerEntry = headerReader.readBytes();
        final byte[] bodyEntry = bodyReader.readBytes();
        final byte[] receiptEntry = receiptReader.readBytes();
        final BlockHeaderFunctions functions = new MainnetBlockHeaderFunctions();

        final BlockHeader header =
            BlockHeader.readFrom(
                new BytesValueRLPInput(Bytes.wrap(headerEntry), false, true), functions);
        final BlockBody body =
            BlockBody.readFrom(
                new BytesValueRLPInput(Bytes.wrap(bodyEntry), false, true), functions);
        final RLPInput receiptsRlp = new BytesValueRLPInput(Bytes.wrap(receiptEntry), false, true);
        final int receiptsCount = receiptsRlp.enterList();
        final List<TransactionReceipt> receipts = new ArrayList<>(receiptsCount);
        for (int j = 0; j < receiptsCount; j++) {
          receipts.add(TransactionReceipt.readFrom(receiptsRlp, true));
        }
        receiptsRlp.leaveList();

        blockchain.appendBlock(new Block(header, body), receipts);
      }
    }
    LOG.info("Chain data loaded");
  }

  @SuppressWarnings("UnusedVariable")
  private void restoreAccounts() throws IOException {
    newWorldStateUpdater();
    int storageBranchCount = 0;
    int storageExtensionCount = 0;
    int storageLeafCount = 0;

    final PersistVisitor<Bytes> accountPersistVisitor =
        new PersistVisitor<>(this::updateAccountState);
    Node<Bytes> root = accountPersistVisitor.initialRoot();

    try (final RollingFileReader reader =
        new RollingFileReader(this::accountFileName, compressed)) {
      for (int i = 0; i < accountCount; i++) {
        if (i % 100000 == 0) {
          LOG.info("Loading account data {} / {}", i, accountCount);
        }
        final byte[] accountEntry = reader.readBytes();
        final BytesValueRLPInput accountInput =
            new BytesValueRLPInput(Bytes.of(accountEntry), false, true);
        final int length = accountInput.enterList();
        if (length != 3) {
          throw new RuntimeException("Unexpected account length " + length);
        }
        final Bytes32 trieKey = accountInput.readBytes32();
        final Bytes accountRlp = accountInput.readBytes();
        final Bytes code = accountInput.readBytes();

        final StateTrieAccountValue trieAccount =
            StateTrieAccountValue.readFrom(new BytesValueRLPInput(accountRlp, false, true));
        if (!trieAccount.getCodeHash().equals(Hash.hash(code))) {
          throw new RuntimeException("Code hash doesn't match");
        }
        if (code.size() > 0) {
          updateCode(code);
        }

        final RestoreVisitor<Bytes> accountTrieWriteVisitor =
            new RestoreVisitor<>(t -> t, accountRlp, accountPersistVisitor);

        root = root.accept(accountTrieWriteVisitor, bytesToPath(trieKey));

        final PersistVisitor<Bytes> storagePersistVisitor =
            new PersistVisitor<>(this::updateAccountStorage);
        Node<Bytes> storageRoot = storagePersistVisitor.initialRoot();

        while (true) {
          final byte[] trieEntry = reader.readBytes();
          final BytesValueRLPInput trieInput =
              new BytesValueRLPInput(Bytes.of(trieEntry), false, true);
          final int len = trieInput.enterList();
          if (len == 0) {
            break;
          }
          if (len != 2) {
            throw new RuntimeException("Unexpected storage trie entry length " + len);
          }
          final Bytes32 storageTrieKey = Bytes32.wrap(trieInput.readBytes());
          final Bytes storageTrieValue = Bytes.wrap(trieInput.readBytes());
          final RestoreVisitor<Bytes> storageTrieWriteVisitor =
              new RestoreVisitor<>(t -> t, storageTrieValue, storagePersistVisitor);
          storageRoot = storageRoot.accept(storageTrieWriteVisitor, bytesToPath(storageTrieKey));

          trieInput.leaveList();
        }
        storagePersistVisitor.persist(storageRoot);
        storageBranchCount += storagePersistVisitor.getBranchNodeCount();
        storageExtensionCount += storagePersistVisitor.getExtensionNodeCount();
        storageLeafCount += storagePersistVisitor.getLeafNodeCount();

        accountInput.leaveList();
      }
    }
    accountPersistVisitor.persist(root);
    updater.commit();
    LOG.info("Account BranchNodes: {} ", accountPersistVisitor.getBranchNodeCount());
    LOG.info("Account ExtensionNodes: {} ", accountPersistVisitor.getExtensionNodeCount());
    LOG.info("Account LeafNodes: {} ", accountPersistVisitor.getLeafNodeCount());
    LOG.info("Storage BranchNodes: {} ", storageBranchCount);
    LOG.info("Storage LeafNodes: {} ", storageExtensionCount);
    LOG.info("Storage ExtensionNodes: {} ", storageLeafCount);
    LOG.info("Account data loaded");
  }

  private void newWorldStateUpdater() {
    if (updater != null) {
      updater.commit();
    }
    final WorldStateStorage worldStateStorage =
        ((DefaultWorldStateArchive) besuController.getProtocolContext().getWorldStateArchive())
            .getWorldStateStorage();
    updater = worldStateStorage.updater();
  }

  private void maybeCommitUpdater() {
    if (trieNodeCount % TRIE_NODE_COMMIT_BATCH_SIZE == 0) {
      newWorldStateUpdater();
    }
  }

  private void updateCode(final Bytes code) {
    maybeCommitUpdater();
    updater.putCode(code);
  }

  private void updateAccountState(final Bytes32 key, final Bytes value) {
    maybeCommitUpdater();
    updater.putAccountStateTrieNode(key, value);
    trieNodeCount++;
  }

  private void updateAccountStorage(final Bytes32 key, final Bytes value) {
    maybeCommitUpdater();
    updater.putAccountStorageTrieNode(key, value);
    trieNodeCount++;
  }

  static class RollingFileReader implements Closeable {
    final BiFunction<Integer, Boolean, Path> filenameGenerator;
    final boolean compressed;
    int currentPosition;
    int fileNumber;
    FileInputStream in;
    final DataInputStream index;
    boolean done = false;

    RollingFileReader(
        final BiFunction<Integer, Boolean, Path> filenameGenerator, final boolean compressed)
        throws IOException {
      this.filenameGenerator = filenameGenerator;
      this.compressed = compressed;
      final Path firstInputFile = filenameGenerator.apply(fileNumber, compressed);
      in = new FileInputStream(firstInputFile.toFile());
      index =
          new DataInputStream(
              new FileInputStream(StateBackupService.dataFileToIndex(firstInputFile).toFile()));
      fileNumber = index.readInt();
      currentPosition = index.readUnsignedShort();
    }

    byte[] readBytes() throws IOException {
      byte[] raw;
      try {
        final int start = currentPosition;
        final int nextFile = index.readUnsignedShort();
        currentPosition = index.readInt();
        if (nextFile == fileNumber) {
          final int len = currentPosition - start;
          raw = new byte[len];
          //noinspection ResultOfMethodCallIgnored
          in.read(raw);
        } else {
          raw = in.readAllBytes();
          in.close();
          fileNumber = nextFile;
          in = new FileInputStream(filenameGenerator.apply(fileNumber, compressed).toFile());
          if (currentPosition != 0) {
            //noinspection ResultOfMethodCallIgnored
            in.skip(currentPosition);
          }
        }
      } catch (final EOFException eofe) {
        // this happens when we read the last value, where there is no next index.
        raw = in.readAllBytes();
        done = true;
      }
      return compressed ? Snappy.uncompress(raw) : raw;
    }

    @Override
    public void close() throws IOException {
      in.close();
      index.close();
    }

    public boolean isDone() {
      return done;
    }
  }

  @SuppressWarnings("unused")
  BesuController createBesuController() {
    return parentCommand.parentCommand.buildController();
  }
}
