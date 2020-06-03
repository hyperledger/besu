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
 */
package org.hyperledger.besu.ethereum.blockcreation;

import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.DefaultEvmAccount;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.core.fees.EIP1559;
import org.hyperledger.besu.ethereum.core.fees.FeeMarket;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.DifficultyCalculator;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.TransactionProcessor;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModuleException;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

public abstract class AbstractBlockCreator<C> implements AsyncBlockCreator {

  public interface ExtraDataCalculator {

    Bytes get(final BlockHeader parent);
  }

  private static final Logger LOG = LogManager.getLogger();

  protected final Address coinbase;

  private final Function<Long, Long> gasLimitCalculator;

  private final ExtraDataCalculator extraDataCalculator;
  private final PendingTransactions pendingTransactions;
  protected final ProtocolContext<C> protocolContext;
  protected final ProtocolSchedule<C> protocolSchedule;
  protected final BlockHeaderFunctions blockHeaderFunctions;
  private final Wei minTransactionGasPrice;
  private final Double minBlockOccupancyRatio;
  private final Address miningBeneficiary;
  protected final BlockHeader parentHeader;
  protected final ProtocolSpec<C> protocolSpec;

  private final AtomicBoolean isCancelled = new AtomicBoolean(false);

  public AbstractBlockCreator(
      final Address coinbase,
      final ExtraDataCalculator extraDataCalculator,
      final PendingTransactions pendingTransactions,
      final ProtocolContext<C> protocolContext,
      final ProtocolSchedule<C> protocolSchedule,
      final Function<Long, Long> gasLimitCalculator,
      final Wei minTransactionGasPrice,
      final Address miningBeneficiary,
      final Double minBlockOccupancyRatio,
      final BlockHeader parentHeader) {
    this.coinbase = coinbase;
    this.extraDataCalculator = extraDataCalculator;
    this.pendingTransactions = pendingTransactions;
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.gasLimitCalculator = gasLimitCalculator;
    this.minTransactionGasPrice = minTransactionGasPrice;
    this.minBlockOccupancyRatio = minBlockOccupancyRatio;
    this.miningBeneficiary = miningBeneficiary;
    this.parentHeader = parentHeader;
    this.protocolSpec = protocolSchedule.getByBlockNumber(parentHeader.getNumber() + 1);
    blockHeaderFunctions = ScheduleBasedBlockHeaderFunctions.create(protocolSchedule);
  }

  /**
   * Create block will create a new block at the head of the blockchain specified in the
   * protocolContext.
   *
   * <p>It will select transactions from the PendingTransaction list for inclusion in the Block
   * body, and will supply an empty Ommers list.
   *
   * <p>Once transactions have been selected and applied to a disposable/temporary world state, the
   * block reward is paid to the relevant coinbase, and a sealable header is constucted.
   *
   * <p>The sealableHeader is then provided to child instances for sealing (i.e. proof of work or
   * otherwise).
   *
   * <p>The constructed block is then returned.
   *
   * @return a block with appropriately selected transactions, seals and ommers.
   */
  @Override
  public Block createBlock(final long timestamp) {
    return createBlock(Optional.empty(), Optional.empty(), timestamp);
  }

  @Override
  public Block createBlock(
      final List<Transaction> transactions, final List<BlockHeader> ommers, final long timestamp) {
    return createBlock(Optional.of(transactions), Optional.of(ommers), timestamp);
  }

  private Block createBlock(
      final Optional<List<Transaction>> maybeTransactions,
      final Optional<List<BlockHeader>> maybeOmmers,
      final long timestamp) {
    try {
      final ProcessableBlockHeader processableBlockHeader = createPendingBlockHeader(timestamp);

      throwIfStopped();

      final MutableWorldState disposableWorldState = duplicateWorldStateAtParent();

      throwIfStopped();

      final List<BlockHeader> ommers = maybeOmmers.orElse(selectOmmers());

      throwIfStopped();

      final BlockTransactionSelector.TransactionSelectionResults transactionResults =
          selectTransactions(processableBlockHeader, disposableWorldState, maybeTransactions);

      throwIfStopped();

      final ProtocolSpec<C> protocolSpec =
          protocolSchedule.getByBlockNumber(processableBlockHeader.getNumber());

      if (!rewardBeneficiary(
          disposableWorldState,
          processableBlockHeader,
          ommers,
          protocolSpec.getBlockReward(),
          protocolSpec.isSkipZeroBlockRewards())) {
        LOG.trace("Failed to apply mining reward, exiting.");
        throw new RuntimeException("Failed to apply mining reward.");
      }

      throwIfStopped();

      final SealableBlockHeader sealableBlockHeader =
          BlockHeaderBuilder.create()
              .populateFrom(processableBlockHeader)
              .ommersHash(BodyValidation.ommersHash(ommers))
              .stateRoot(disposableWorldState.rootHash())
              .transactionsRoot(
                  BodyValidation.transactionsRoot(transactionResults.getTransactions()))
              .receiptsRoot(BodyValidation.receiptsRoot(transactionResults.getReceipts()))
              .logsBloom(BodyValidation.logsBloom(transactionResults.getReceipts()))
              .gasUsed(transactionResults.getTotalCumulativeGasUsed())
              .extraData(extraDataCalculator.get(parentHeader))
              .buildSealableBlockHeader();

      final BlockHeader blockHeader = createFinalBlockHeader(sealableBlockHeader);

      return new Block(blockHeader, new BlockBody(transactionResults.getTransactions(), ommers));
    } catch (final SecurityModuleException ex) {
      LOG.warn("Failed to create block signature.", ex);
      throw ex;
    } catch (final CancellationException ex) {
      LOG.trace("Attempt to create block was interrupted.");
      throw ex;
    } catch (final Exception ex) {
      // TODO(tmm): How are we going to know this has exploded, and thus restart it?
      LOG.trace("Block creation failed unexpectedly. Will restart on next block added to chain.");
      throw ex;
    }
  }

  private BlockTransactionSelector.TransactionSelectionResults selectTransactions(
      final ProcessableBlockHeader processableBlockHeader,
      final MutableWorldState disposableWorldState,
      final Optional<List<Transaction>> transactions)
      throws RuntimeException {
    final TransactionProcessor transactionProcessor = protocolSpec.getTransactionProcessor();

    final MainnetBlockProcessor.TransactionReceiptFactory transactionReceiptFactory =
        protocolSpec.getTransactionReceiptFactory();

    final BlockTransactionSelector selector =
        new BlockTransactionSelector(
            transactionProcessor,
            protocolContext.getBlockchain(),
            disposableWorldState,
            pendingTransactions,
            processableBlockHeader,
            transactionReceiptFactory,
            minTransactionGasPrice,
            minBlockOccupancyRatio,
            isCancelled::get,
            miningBeneficiary,
            protocolSpec.getTransactionPriceCalculator(),
            protocolSpec.getEip1559());

    if (transactions.isPresent()) {
      return selector.evaluateTransactions(transactions.get());
    } else {
      return selector.buildTransactionListForBlock();
    }
  }

  private MutableWorldState duplicateWorldStateAtParent() {
    final Hash parentStateRoot = parentHeader.getStateRoot();
    final MutableWorldState worldState =
        protocolContext
            .getWorldStateArchive()
            .getMutable(parentStateRoot)
            .orElseThrow(
                () -> {
                  LOG.info("Unable to create block because world state is not available");
                  return new IllegalStateException(
                      "World state not available for block "
                          + parentHeader.getNumber()
                          + " with state root "
                          + parentStateRoot);
                });

    return worldState.copy();
  }

  private List<BlockHeader> selectOmmers() {
    return Lists.newArrayList();
  }

  private ProcessableBlockHeader createPendingBlockHeader(final long timestamp) {
    final long newBlockNumber = parentHeader.getNumber() + 1;
    final long gasLimit;
    if (ExperimentalEIPs.eip1559Enabled && protocolSpec.isEip1559()) {
      gasLimit = protocolSpec.getEip1559().orElseThrow().eip1559GasPool(newBlockNumber);
    } else {
      gasLimit = gasLimitCalculator.apply(parentHeader.getGasLimit());
    }
    final DifficultyCalculator<C> difficultyCalculator = protocolSpec.getDifficultyCalculator();
    final BigInteger difficulty =
        difficultyCalculator.nextDifficulty(timestamp, parentHeader, protocolContext);

    Long baseFee = null;
    if (ExperimentalEIPs.eip1559Enabled && protocolSpec.isEip1559()) {
      final EIP1559 eip1559 = protocolSpec.getEip1559().orElseThrow();
      if (eip1559.isForkBlock(newBlockNumber)) {
        baseFee = FeeMarket.eip1559().getInitialBasefee();
      } else {
        baseFee =
            eip1559.computeBaseFee(
                parentHeader.getBaseFee().orElseThrow(), parentHeader.getGasUsed());
      }
    }
    return BlockHeaderBuilder.create()
        .parentHash(parentHeader.getHash())
        .coinbase(coinbase)
        .difficulty(Difficulty.of(difficulty))
        .number(newBlockNumber)
        .gasLimit(gasLimit)
        .timestamp(timestamp)
        .baseFee(baseFee)
        .buildProcessableBlockHeader();
  }

  @Override
  public void cancel() {
    isCancelled.set(true);
  }

  @Override
  public boolean isCancelled() {
    return isCancelled.get();
  }

  private void throwIfStopped() throws CancellationException {
    if (isCancelled.get()) {
      throw new CancellationException();
    }
  }

  /* Copied from BlockProcessor (with modifications). */
  boolean rewardBeneficiary(
      final MutableWorldState worldState,
      final ProcessableBlockHeader header,
      final List<BlockHeader> ommers,
      final Wei blockReward,
      final boolean skipZeroBlockRewards) {

    // TODO(tmm): Added to make this work, should come from blockProcessor.
    final int MAX_GENERATION = 6;
    if (skipZeroBlockRewards && blockReward.isZero()) {
      return true;
    }

    final Wei coinbaseReward =
        protocolSpec
            .getBlockProcessor()
            .getCoinbaseReward(blockReward, header.getNumber(), ommers.size());
    final WorldUpdater updater = worldState.updater();
    final DefaultEvmAccount beneficiary = updater.getOrCreate(miningBeneficiary);

    beneficiary.getMutable().incrementBalance(coinbaseReward);
    for (final BlockHeader ommerHeader : ommers) {
      if (ommerHeader.getNumber() - header.getNumber() > MAX_GENERATION) {
        LOG.trace(
            "Block processing error: ommer block number {} more than {} generations current block number {}",
            ommerHeader.getNumber(),
            MAX_GENERATION,
            header.getNumber());
        return false;
      }

      final DefaultEvmAccount ommerCoinbase = updater.getOrCreate(ommerHeader.getCoinbase());
      final Wei ommerReward =
          protocolSpec
              .getBlockProcessor()
              .getOmmerReward(blockReward, header.getNumber(), ommerHeader.getNumber());
      ommerCoinbase.getMutable().incrementBalance(ommerReward);
    }

    updater.commit();

    return true;
  }

  protected abstract BlockHeader createFinalBlockHeader(
      final SealableBlockHeader sealableBlockHeader);
}
