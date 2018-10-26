/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.util;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.GenesisConfig;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockBody;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.MutableAccount;
import tech.pegasys.pantheon.ethereum.core.MutableWorldState;
import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionReceipt;
import tech.pegasys.pantheon.ethereum.core.Wei;
import tech.pegasys.pantheon.ethereum.core.WorldUpdater;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;
import tech.pegasys.pantheon.ethereum.mainnet.HeaderValidationMode;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSchedule;
import tech.pegasys.pantheon.ethereum.mainnet.ProtocolSpec;
import tech.pegasys.pantheon.ethereum.mainnet.ScheduleBasedBlockHashFunction;
import tech.pegasys.pantheon.ethereum.rlp.FileRLPInput;
import tech.pegasys.pantheon.ethereum.rlp.RLPInput;
import tech.pegasys.pantheon.util.bytes.BytesValue;
import tech.pegasys.pantheon.util.uint.UInt256;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;

/**
 * Pantheon Blockchain Import Util.
 *
 * <p>Expected File RLP Format:
 *
 * <p>Snapshot: BlockhainSnapshot || WorldStateSnapshot
 *
 * <p>BlockchainSnapshot: N || BlockWithReceipts[0] || BlockWithReceipts[1] || ... ||
 * BlockWithReceipts[N]
 *
 * <p>BlockWithReceipts[n]: Block[n] || Receipts[n]
 *
 * <p>Block[n]: Header[n] || Transactions[n] || OmmerHeaders[n]
 *
 * <p>Transactions[n]: [ Transaction[0] || Transaction[1] || ... || Transaction[T] ]
 * OmmerHeaders[n]: [ OmmerHeader[0] || OmmerHeader[1] || ... || OmmerHeader[O] ] Receipts[n]: [
 * Receipt[0] || Receipt[1] || ... || Receipt[T] ]
 *
 * <p>WorldStateSnapshot: AccountSnapshot[0] || AccountSnapshot[1] || ... || AccountSnapshot[A]
 * AccountSnapshot[a]: AccountAddress[a] || AccountState[a] || AccountCode[a] ||
 * AccountStorageSnapshot[a] AccountStorageSnapshot[a]: AccountStorageEntry[0] ||
 * AccountStorageEntry[1] || ... || AccountStorageEntry[E] AccountStorageEntry[e]:
 * AccountStorageKey[e] || AccountStorageValue[e]
 *
 * <p>N = number of blocks T = number of transactions in block O = number of ommers in block A =
 * number of accounts in world state E = number of storage entries in the account || = concatenation
 */
public class BlockchainImporter extends BlockImporter {
  private static final Logger LOG = LogManager.getLogger();
  private static final Logger METRICS_LOG = LogManager.getLogger(LOG.getName() + "-metrics");

  Boolean isSkipHeaderValidation = false;

  /**
   * Imports blockchain from file as concatenated RLP sections
   *
   * @param <C> the consensus context type
   * @param dataFilePath Path to the file containing the dataFilePath
   * @param pantheonController the PantheonController that defines blockchain behavior
   * @param isSkipHeaderValidation if true, header validation is skipped. This must only be used
   *     when the source data is fully trusted / guaranteed to be correct.
   * @param metricsIntervalSec seconds between logging progress metrics
   * @param accountCommitInterval commit account state every n accounts
   * @param isSkipBlocks true if blocks in the import file should be skipped over.
   * @param isSkipAccounts true if accounts in the import file should be skipped over.
   * @param worldStateOffset file offset for the starting byte of the world state. Only relevant in
   *     combination with isSkipBlocks
   * @return the import result
   * @throws IOException On Failure
   */
  public <C> BlockImporter.ImportResult importBlockchain(
      final Path dataFilePath,
      final PantheonController<C> pantheonController,
      final boolean isSkipHeaderValidation,
      final int metricsIntervalSec,
      final int accountCommitInterval,
      final boolean isSkipBlocks,
      final boolean isSkipAccounts,
      final Long worldStateOffset)
      throws IOException {
    checkNotNull(dataFilePath);
    checkNotNull(pantheonController);
    this.isSkipHeaderValidation = isSkipHeaderValidation;
    final long startTime = System.currentTimeMillis();

    checkNotNull(dataFilePath);
    try (final FileChannel file = FileChannel.open(dataFilePath, StandardOpenOption.READ)) {
      final FileRLPInput rlp = new FileRLPInput(file, true);
      LOG.info("Import started.");

      final BlockchainImporter.ImportResult blockImportResults;
      blockImportResults =
          importBlockchain(
              pantheonController, rlp, isSkipBlocks, metricsIntervalSec, worldStateOffset);

      if (!isSkipAccounts) {
        final Hash worldStateRootHash;
        worldStateRootHash =
            importWorldState(pantheonController, rlp, metricsIntervalSec, accountCommitInterval);
        validateWorldStateRootHash(pantheonController, worldStateRootHash);
      }

      final long totalRunningSec = (System.currentTimeMillis() - startTime) / 1000;
      final double totallRunningHours = totalRunningSec / (60.0 * 60);

      final String message =
          format(
              "Import finished in %,d seconds (%,1.2f hours).",
              totalRunningSec, totallRunningHours);
      METRICS_LOG.info(message);

      return blockImportResults;
    } catch (final Exception e) {
      final String message = format("Unable to import from file '%s'", dataFilePath.toString());
      throw new RuntimeException(message, e);
    } finally {
      pantheonController.close();
    }
  }

  /**
   * Imports the blockchain section of the file
   *
   * @param <C> the consensus context type
   * @param pantheonController the PantheonController that defines blockchain behavior
   * @param rlp RLP Input File
   * @param isSkipBlocks true if blocks in the import file should be skipped over.
   * @param metricsIntervalSec seconds between logging progress metrics
   * @param worldStateOffset file offset for the starting byte of the world state. Only relevant in
   *     combination with isSkipBlocks
   * @return the import result
   */
  private <C> BlockImporter.ImportResult importBlockchain(
      final PantheonController<C> pantheonController,
      final FileRLPInput rlp,
      final Boolean isSkipBlocks,
      final int metricsIntervalSec,
      final Long worldStateOffset) {
    final ProtocolSchedule<C> protocolSchedule = pantheonController.getProtocolSchedule();
    final ProtocolContext<C> context = pantheonController.getProtocolContext();
    final GenesisConfig<C> genesis = pantheonController.getGenesisConfig();
    checkNotNull(isSkipBlocks);
    final BlockHeader genesisHeader = genesis.getBlock().getHeader();

    final long startTime = System.currentTimeMillis();
    long lapStartTime = startTime;
    final long metricsIntervalMS =
        1_000L * metricsIntervalSec; // Use Millis here to make math easier
    long nextMetricsTime = startTime + metricsIntervalMS;
    long itemStartingOffset = 0;
    final String logAction = isSkipBlocks ? "Skipped" : "Imported";

    final long totalBlockCount = rlp.readLongScalar();

    LOG.info(
        format(
            "Import file contains %,d blocks, starting at file offset %,d.",
            totalBlockCount, rlp.currentOffset()));

    if (isSkipBlocks && worldStateOffset != null) {
      // Skip blocks.  Offset was given, so we don't even have to parse through the blocks
      logFinalMetrics("Skipped", "block", startTime, Math.toIntExact(totalBlockCount));
      rlp.setTo(worldStateOffset);
      return new BlockchainImporter.ImportResult(UInt256.ZERO, 0);
    }

    final Function<RLPInput, BlockHeader> headerReader =
        rlp2 -> BlockHeader.readFrom(rlp2, ScheduleBasedBlockHashFunction.create(protocolSchedule));

    BlockHeader previousHeader = genesis.getBlock().getHeader();
    int blockCount = 0;
    int lapCount = 0;
    BlockHeader header = null;
    BlockBody body = null;
    List<TransactionReceipt> receipts = null;
    try {
      while (blockCount < totalBlockCount) {
        header = null; // Reset so that if an error occurs, we log the correct data.
        body = null;
        receipts = null;

        blockCount++;
        lapCount++;
        itemStartingOffset = rlp.currentOffset();

        if (isSkipBlocks && !(blockCount == totalBlockCount - 1)) {
          // Skip block, unless it is the last one.  If it's the last one, it will get parsed
          // printed into the log, but not stored.  This is for ops & dev debug purposes.
          rlp.skipNext();
          rlp.skipNext();
        } else {
          rlp.enterList(true);
          header = headerReader.apply(rlp);
          body = new BlockBody(rlp.readList(Transaction::readFrom), rlp.readList(headerReader));
          rlp.leaveList();
          receipts = rlp.readList(TransactionReceipt::readFrom);
          if (header.getNumber() == genesisHeader.getNumber()) {
            // Don't import genesis block
            previousHeader = header;
            continue;
          }
          final ProtocolSpec<C> protocolSpec =
              protocolSchedule.getByBlockNumber(header.getNumber());

          if (!isSkipHeaderValidation) {
            // Validate headers here because we already have the previous block, and can avoid
            // an unnecessary lookup compared to doing the validation in the BlockImporter below.
            final BlockHeaderValidator<C> blockHeaderValidator =
                protocolSpec.getBlockHeaderValidator();
            final boolean validHeader =
                blockHeaderValidator.validateHeader(
                    header, previousHeader, context, HeaderValidationMode.FULL);
            if (!validHeader) {
              final String message =
                  format(
                      "Invalid header block number %,d at file position %,d",
                      header.getNumber(), itemStartingOffset);
              throw new IllegalStateException(message);
            }
          }

          if (blockCount == 1) {
            // Log the first block for ops & dev debug purposes.
            LOG.info(
                format(
                    "First Block, file offset=%,d\nHeader=%s\n\nBody=%s\n\n",
                    itemStartingOffset, header, body));
          }

          if (blockCount == totalBlockCount) {
            // Log the last block for ops & dev debug purposes.
            LOG.info(
                format(
                    "Last Block, file offset=%,d\nHeader=%s\n\nBody=%s\n\n",
                    itemStartingOffset, header, body));
          }

          if (LOG.isTraceEnabled()) {
            final String receiptsStr =
                receipts == null ? null : Strings.join(receipts.iterator(), ',');
            LOG.trace(
                format(
                    "About to import block from file offset %,d with header=%s, body=%s, receipts=%s",
                    itemStartingOffset, header, body, receiptsStr));
          }

          final tech.pegasys.pantheon.ethereum.core.BlockImporter<C> blockImporter;
          blockImporter = protocolSpec.getBlockImporter();

          if (!isSkipBlocks) {
            // Do not validate headers here.  They were already validated above, since we already
            // have the previous block on-hand, we avoid the extra lookup BlockImporter would do
            final boolean blockImported =
                blockImporter.fastImportBlock(
                    context, new Block(header, body), receipts, HeaderValidationMode.NONE);
            if (!blockImported) {
              final String message =
                  format(
                      "Invalid header block number %,d at file position %,d",
                      header.getNumber(), itemStartingOffset);
              throw new IllegalStateException(message);
            }
          }
        }

        if (System.currentTimeMillis() >= nextMetricsTime) {
          logLapMetrics(logAction, "block", startTime, blockCount, lapStartTime, lapCount);
          lapCount = 0;
          lapStartTime = System.currentTimeMillis();
          nextMetricsTime = lapStartTime + metricsIntervalMS;
        }
        previousHeader = header;
      }
    } catch (final RuntimeException e) {
      final String receiptsStr = receipts == null ? null : Strings.join(receipts.iterator(), ',');
      final String message =
          format(
              "Error importing block from file offset %,d with header=%s, body=%s, receipts=%s",
              itemStartingOffset, header, body, receiptsStr);
      throw new RuntimeException(message, e);
    }

    logFinalMetrics(logAction, "block", startTime, blockCount);
    return new BlockchainImporter.ImportResult(
        context.getBlockchain().getChainHead().getTotalDifficulty(), blockCount);
  }

  /**
   * Imports the worldstate section of the file
   *
   * @param pantheonController the PantheonController that defines blockchain behavior
   * @param rlp RLP Input File
   * @param metricsIntervalSec seconds between logging progress metrics
   * @param accountCommitInterval commit account state every n accounts
   * @param <C> the consensus context type
   * @return root hash of the world state
   */
  private <C> Hash importWorldState(
      final PantheonController<C> pantheonController,
      final FileRLPInput rlp,
      final int metricsIntervalSec,
      final int accountCommitInterval) {
    final ProtocolContext<C> context = pantheonController.getProtocolContext();
    final MutableWorldState worldState = context.getWorldStateArchive().getMutable();
    WorldUpdater worldStateUpdater = worldState.updater();

    final long startTime = System.currentTimeMillis();
    long lapStartTime = startTime;
    final long metricsIntervalMS =
        1_000L * metricsIntervalSec; // Use Millis here to make math easier
    long nextMetricsTime = startTime + metricsIntervalMS;
    long itemStartingOffset = 0;

    int count = 0;
    int lapCount = 0;
    Address address = null;
    MutableAccount account = null;
    Long nonce = null;
    Wei balance = null;
    Hash storageRoot = null;
    Hash codeHash = null;

    LOG.info(format("Starting Account Import at file offset %,d", rlp.currentOffset()));
    LOG.info("Initial world state root hash: {}", worldState.rootHash());
    try {
      while (!rlp.isDone() && rlp.nextSize() == 20) {
        address = null; // reset to null here so we can log useful info if error occurs
        account = null;
        nonce = null;
        balance = null;
        storageRoot = null;
        codeHash = null;

        count++;
        lapCount++;
        itemStartingOffset = rlp.currentOffset();

        address = Address.readFrom(rlp);

        rlp.enterList(true);
        nonce = rlp.readLongScalar();
        balance = rlp.readUInt256Scalar(Wei::wrap);
        storageRoot = Hash.wrap(rlp.readBytes32());
        codeHash = Hash.wrap(rlp.readBytes32());
        rlp.leaveList();

        if (LOG.isTraceEnabled()) {
          LOG.trace(
              format(
                  "About to import account from file offset %,d with address=%s, nonce=%s, balance=%s",
                  itemStartingOffset, address, nonce, balance));
        }

        account = worldStateUpdater.createAccount(address, nonce, balance);

        final BytesValue code = rlp.readBytesValue();
        account.setCode(code);

        // hash code and compare to codehash
        verifyCodeHash(address, code, codeHash);

        while (!rlp.isDone() && rlp.nextSize() == 32) {
          // Read an Account Storage Entry.  We know the key is 32 bytes, vs 20 bytes if we started
          // with the next Account
          final UInt256 key = rlp.readUInt256Scalar();
          final UInt256 value = rlp.readUInt256Scalar();
          account.setStorageValue(key, value);
        }

        // Add verification for each account's storage root hash here, if debugging state root
        // becomes a problem
        // Functionally, the single check at the end is enough, but if that fails, it doesn't give
        // any
        // indication about which account started the mismatch.  That check can go here if it
        // becomes necessary.

        if (count == 1) {
          // Log the first account for ops & dev debug purposes.
          LOG.info(
              format(
                  "Importing first account number %d at file offset %,d.  address=%s, account=%s, account nonce=%s, account balance=%s, account storage root=%s, account code hash=%s",
                  count,
                  itemStartingOffset,
                  address,
                  account,
                  nonce,
                  balance,
                  storageRoot,
                  codeHash));
        }

        if (count % accountCommitInterval == 0) {
          worldStateUpdater.commit();

          worldStateUpdater =
              worldState.updater(); // Get a new updater, so the old one can GC itself.
        }
        if (count % accountCommitInterval == 0) {
          worldState.persist();
        }
        if (System.currentTimeMillis() >= nextMetricsTime) {
          logLapMetrics("Imported", "account", startTime, count, lapStartTime, lapCount);
          lapCount = 0;
          lapStartTime = System.currentTimeMillis();
          nextMetricsTime = lapStartTime + metricsIntervalMS;
        }
      }

      // Log the last account for ops & dev debug purposes.
      LOG.info(
          format(
              "Importing last account number %d at file offset %,d.  address=%s, account=%s, account nonce=%s, account balance=%s, account storage root=%s, account code hash=%s",
              count, itemStartingOffset, address, account, nonce, balance, storageRoot, codeHash));

      // Do a final commit & persist.
      worldStateUpdater.commit();
      worldState.persist();
    } catch (final Exception e) {
      final String message =
          format(
              "Error importing account number %d at file offset %,d.  address=%s, account=%s, account nonce=%s, account balance=%s, account storage root=%s, account code hash=%s",
              count, itemStartingOffset, address, account, nonce, balance, storageRoot, codeHash);
      throw new RuntimeException(message, e);
    }
    logFinalMetrics("Imported", "account", startTime, count);
    LOG.info("Final world state root hash: {}", worldState.rootHash());
    return worldState.rootHash();
  }

  /**
   * Verifies the account code against it's stated hash
   *
   * @param address Address of the account being checked
   * @param code Code to be verified
   * @param codeHashFromState stated hash of the code to verify
   */
  private void verifyCodeHash(
      final Address address, final BytesValue code, final Hash codeHashFromState) {
    final Hash myHash = Hash.hash(code);
    if (!myHash.equals(codeHashFromState)) {
      final String message =
          format(
              "Code hash does not match for account %s.  Expected %s, but got %s for code %s",
              address, codeHashFromState, myHash, code);
      throw new RuntimeException(message);
    }
  }

  /**
   * Verifies the calculated world state's root hash against the stated value in the blockain's head
   * block
   *
   * @param pantheonController the PantheonController that defines blockchain behavior
   * @param worldStateRootHash calculated world state's root hash
   * @param <C> the consensus context type
   */
  private <C> void validateWorldStateRootHash(
      final PantheonController<C> pantheonController, final Hash worldStateRootHash) {
    final ProtocolContext<C> context = pantheonController.getProtocolContext();
    final MutableBlockchain blockchain = context.getBlockchain();
    final Optional<BlockHeader> header = blockchain.getBlockHeader(blockchain.getChainHeadHash());

    if (!header.isPresent()) {
      final String message =
          "Can not get header for blockchain head, using hash " + blockchain.getChainHeadHash();
      throw new IllegalStateException(message);
    }
    final Hash blockStorageHash = header.get().getStateRoot();
    if (!blockStorageHash.equals(worldStateRootHash)) {
      final String message =
          format(
              "Invalid block: state root mismatch (expected=%s, actual=%s)",
              blockStorageHash, worldStateRootHash);
      throw new RuntimeException(message);
    }
  }

  /**
   * logs progress metrics for each 'lap' of execution. The total stats are also logged. Laps are
   * defined as number of seconds between logging progress metrics.
   *
   * @param action Action being performed on itemName. (eg "Imported", "Skipped")
   * @param itemName Item being tracked. (eg "account", "block"). identifier for what is being
   *     tracked
   * @param startTime timestamp for when the overall process started
   * @param totalItemCount total items processed
   * @param lapStartTime timestamp for when the current lap processing started
   * @param lapItemCount items processed this lap
   */
  private void logLapMetrics(
      final String action,
      final String itemName,
      final long startTime,
      final int totalItemCount,
      final long lapStartTime,
      final int lapItemCount) {
    final long curTime = System.currentTimeMillis();
    long lapRunningSec = (curTime - lapStartTime) / 1000;
    long totalRunningSec = (curTime - startTime) / 1000;
    lapRunningSec = lapRunningSec > 0 ? lapRunningSec : 1; // Set min time to 1 sec.
    totalRunningSec = totalRunningSec > 0 ? totalRunningSec : 1; // Set min time to 1 sec.
    final long lapItemPerSec = lapItemCount / lapRunningSec;
    final long totalItemPerSec = totalItemCount / totalRunningSec;
    final String message =
        format(
            "%s %,7d %ss in %3d seconds  (%,5d %ss/sec). Totals: %,7d %ss in %3d seconds (%,5d %ss/sec).",
            action,
            lapItemCount,
            itemName,
            lapRunningSec,
            lapItemPerSec,
            itemName,
            totalItemCount,
            itemName,
            totalRunningSec,
            totalItemPerSec,
            itemName);
    METRICS_LOG.info(message);
  }

  /**
   * logs the final metrics for this process.
   *
   * @param action Action being performed on itemName. (eg "Imported", "Skipped")
   * @param itemName Item being tracked. (eg "account", "block"). identifier for what is being
   *     tracked
   * @param startTime timestamp for when the overall process started
   * @param totalItemCount total items processed
   */
  private void logFinalMetrics(
      final String action, final String itemName, final long startTime, final int totalItemCount) {
    final long curTime = System.currentTimeMillis();
    long totalRunningSec = (curTime - startTime) / 1000;
    totalRunningSec = totalRunningSec > 0 ? totalRunningSec : 1; // Set min time to 1 sec.
    final long totalItemPerSec = totalItemCount / totalRunningSec;
    final String message =
        format(
            "%s %,d %ss in %,d seconds  (%,d %ss/sec).",
            action, totalItemCount, itemName, totalRunningSec, totalItemPerSec, itemName);
    METRICS_LOG.info(message);
  }
}
