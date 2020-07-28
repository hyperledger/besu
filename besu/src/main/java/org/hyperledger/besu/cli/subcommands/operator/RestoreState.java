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
import org.hyperledger.besu.ethereum.worldstate.StateTrieAccountValue;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    name = "restore-state",
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

  private long targetBlock;
  private long accountCount;
  private boolean compressed;

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

    // load state
    try {
      final ObjectNode manifest =
          JsonUtil.objectNodeFromString(
              Files.readString(backupDir.resolve("besu-backup-manifest.json")));

      compressed = manifest.get("compressed").asBoolean(false);
      targetBlock = manifest.get("targetBlock").asLong();
      accountCount = manifest.get("accountCount").asLong();

      //      restoreBlocks();
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
        if (header.getNumber() == targetBlock) {
          System.out.println(header.getStateRoot());
        }
        BlockBody.readFrom(new BytesValueRLPInput(Bytes.wrap(bodyEntry), false, true), functions);
        final RLPInput receiptsRlp = new BytesValueRLPInput(Bytes.wrap(receiptEntry), false, true);
        final int receiptsCount = receiptsRlp.enterList();
        for (int j = 0; j < receiptsCount; j++) {
          TransactionReceipt.readFrom(receiptsRlp, true);
        }
        receiptsRlp.leaveList();
      }
    }
    LOG.info("Chain data loaded");
  }

  @SuppressWarnings("UnusedVariable")
  private void restoreAccounts() throws IOException {
    final PersistVisitor<Bytes> persistVisitor = new PersistVisitor<>();
    Node<Bytes> root = persistVisitor.initialRoot();

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
        if (length != 4) {
          throw new RuntimeException("Unexpected account length " + length);
        }
        final Bytes32 trieKey = accountInput.readBytes32(); // trie hash
        final Bytes accountRlp = accountInput.readBytes(); // account rlp
        final Bytes code = accountInput.readBytes(); // code

        final StateTrieAccountValue trieAccount =
            StateTrieAccountValue.readFrom(new BytesValueRLPInput(accountRlp, false, true));
        if (!trieAccount.getCodeHash().equals(Hash.hash(code))) {
          throw new RuntimeException("Code hash doesn't match");
        }
        //        System.out.println(trieKey);
        final RestoreVisitor<Bytes> restoreVistor =
            new RestoreVisitor<>(t -> t, accountRlp, persistVisitor);

        root = root.accept(restoreVistor, bytesToPath(trieKey));

        final int storageTrieSize = accountInput.enterList();
        for (int j = 0; j < storageTrieSize; j++) {
          final int len = accountInput.enterList();
          if (len != 2) {
            throw new RuntimeException("Unexpected storage trie entry length " + len);
          }
          accountInput.readBytes();
          accountInput.readBytes();
          accountInput.leaveList();
        }
        accountInput.leaveList();
      }
    }
    persistVisitor.finalize(root);
    System.out.println(root.getHash());
    LOG.info("Account data loaded");
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
