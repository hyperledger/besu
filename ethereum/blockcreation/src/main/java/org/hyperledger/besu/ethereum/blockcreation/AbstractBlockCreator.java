/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessBlobGasCalculator.calculateExcessBlobGasForParent;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.BlobGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.BlockTransactionSelector.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderFunctions;
import org.hyperledger.besu.ethereum.core.Deposit;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.SealableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.Withdrawal;
import org.hyperledger.besu.ethereum.core.encoding.DepositDecoder;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.BodyValidation;
import org.hyperledger.besu.ethereum.mainnet.DepositsValidator;
import org.hyperledger.besu.ethereum.mainnet.DifficultyCalculator;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduleBasedBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.WithdrawalsProcessor;
import org.hyperledger.besu.ethereum.mainnet.feemarket.BaseFeeMarket;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.services.exception.StorageException;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModuleException;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractBlockCreator implements AsyncBlockCreator {

  public interface ExtraDataCalculator {

    Bytes get(final BlockHeader parent);
  }

  private static final Logger LOG = LoggerFactory.getLogger(AbstractBlockCreator.class);

  protected final Address coinbase;
  private final MiningBeneficiaryCalculator miningBeneficiaryCalculator;
  protected final Supplier<Optional<Long>> targetGasLimitSupplier;

  private final ExtraDataCalculator extraDataCalculator;
  private final TransactionPool transactionPool;
  protected final ProtocolContext protocolContext;
  protected final ProtocolSchedule protocolSchedule;
  protected final BlockHeaderFunctions blockHeaderFunctions;
  private final Wei minTransactionGasPrice;
  private final Double minBlockOccupancyRatio;
  protected final BlockHeader parentHeader;
  private final Optional<Address> depositContractAddress;

  private final AtomicBoolean isCancelled = new AtomicBoolean(false);

  protected AbstractBlockCreator(
      final Address coinbase,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final Supplier<Optional<Long>> targetGasLimitSupplier,
      final ExtraDataCalculator extraDataCalculator,
      final TransactionPool transactionPool,
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final Wei minTransactionGasPrice,
      final Double minBlockOccupancyRatio,
      final BlockHeader parentHeader,
      final Optional<Address> depositContractAddress) {
    this.coinbase = coinbase;
    this.miningBeneficiaryCalculator = miningBeneficiaryCalculator;
    this.targetGasLimitSupplier = targetGasLimitSupplier;
    this.extraDataCalculator = extraDataCalculator;
    this.transactionPool = transactionPool;
    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.minTransactionGasPrice = minTransactionGasPrice;
    this.minBlockOccupancyRatio = minBlockOccupancyRatio;
    this.parentHeader = parentHeader;
    this.depositContractAddress = depositContractAddress;
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
  public BlockCreationResult createBlock(final long timestamp) {
    return createBlock(Optional.empty(), Optional.empty(), timestamp);
  }

  @Override
  public BlockCreationResult createBlock(
      final List<Transaction> transactions, final List<BlockHeader> ommers, final long timestamp) {
    return createBlock(Optional.of(transactions), Optional.of(ommers), timestamp);
  }

  @Override
  public BlockCreationResult createBlock(
      final Optional<List<Transaction>> maybeTransactions,
      final Optional<List<BlockHeader>> maybeOmmers,
      final long timestamp) {
    return createBlock(
        maybeTransactions, maybeOmmers, Optional.empty(), Optional.empty(), timestamp, true);
  }

  protected BlockCreationResult createBlock(
      final Optional<List<Transaction>> maybeTransactions,
      final Optional<List<BlockHeader>> maybeOmmers,
      final Optional<List<Withdrawal>> maybeWithdrawals,
      final Optional<Bytes32> maybePrevRandao,
      final long timestamp,
      boolean rewardCoinbase) {

    try (final MutableWorldState disposableWorldState = duplicateWorldStateAtParent()) {
      final ProtocolSpec newProtocolSpec =
          protocolSchedule.getForNextBlockHeader(parentHeader, timestamp);

      final ProcessableBlockHeader processableBlockHeader =
          createPendingBlockHeader(timestamp, maybePrevRandao, newProtocolSpec);
      final Address miningBeneficiary =
          miningBeneficiaryCalculator.getMiningBeneficiary(processableBlockHeader.getNumber());
      Wei blobGasPrice =
          newProtocolSpec
              .getFeeMarket()
              .dataPricePerGas(calculateExcessBlobGasForParent(newProtocolSpec, parentHeader));

      throwIfStopped();

      final List<BlockHeader> ommers = maybeOmmers.orElse(selectOmmers());

      throwIfStopped();
      final TransactionSelectionResults transactionResults =
          selectTransactions(
              processableBlockHeader,
              disposableWorldState,
              maybeTransactions,
              miningBeneficiary,
              blobGasPrice,
              newProtocolSpec);

      transactionResults.logSelectionStats();

      throwIfStopped();

      final Optional<WithdrawalsProcessor> maybeWithdrawalsProcessor =
          newProtocolSpec.getWithdrawalsProcessor();
      final boolean withdrawalsCanBeProcessed =
          maybeWithdrawalsProcessor.isPresent() && maybeWithdrawals.isPresent();
      if (withdrawalsCanBeProcessed) {
        maybeWithdrawalsProcessor
            .get()
            .processWithdrawals(maybeWithdrawals.get(), disposableWorldState.updater());
      }

      throwIfStopped();

      final DepositsValidator depositsValidator = newProtocolSpec.getDepositsValidator();
      Optional<List<Deposit>> maybeDeposits = Optional.empty();
      if (depositsValidator instanceof DepositsValidator.AllowedDeposits
          && depositContractAddress.isPresent()) {
        maybeDeposits = Optional.of(findDepositsFromReceipts(transactionResults));
      }

      throwIfStopped();

      if (rewardCoinbase
          && !rewardBeneficiary(
              disposableWorldState,
              processableBlockHeader,
              ommers,
              miningBeneficiary,
              newProtocolSpec.getBlockReward(),
              newProtocolSpec.isSkipZeroBlockRewards(),
              newProtocolSpec)) {
        LOG.trace("Failed to apply mining reward, exiting.");
        throw new RuntimeException("Failed to apply mining reward.");
      }

      throwIfStopped();

      final GasUsage usage = computeExcessBlobGas(transactionResults, newProtocolSpec);

      throwIfStopped();

      BlockHeaderBuilder builder =
          BlockHeaderBuilder.create()
              .populateFrom(processableBlockHeader)
              .ommersHash(BodyValidation.ommersHash(ommers))
              .stateRoot(disposableWorldState.rootHash())
              .transactionsRoot(
                  BodyValidation.transactionsRoot(transactionResults.getSelectedTransactions()))
              .receiptsRoot(BodyValidation.receiptsRoot(transactionResults.getReceipts()))
              .logsBloom(BodyValidation.logsBloom(transactionResults.getReceipts()))
              .gasUsed(transactionResults.getCumulativeGasUsed())
              .extraData(extraDataCalculator.get(parentHeader))
              .withdrawalsRoot(
                  withdrawalsCanBeProcessed
                      ? BodyValidation.withdrawalsRoot(maybeWithdrawals.get())
                      : null)
              .depositsRoot(maybeDeposits.map(BodyValidation::depositsRoot).orElse(null));
      if (usage != null) {
        builder.blobGasUsed(usage.used.toLong()).excessBlobGas(usage.excessBlobGas);
      }

      final SealableBlockHeader sealableBlockHeader = builder.buildSealableBlockHeader();

      final BlockHeader blockHeader = createFinalBlockHeader(sealableBlockHeader);

      final Optional<List<Withdrawal>> withdrawals =
          withdrawalsCanBeProcessed ? maybeWithdrawals : Optional.empty();
      final Block block =
          new Block(
              blockHeader,
              new BlockBody(
                  transactionResults.getSelectedTransactions(),
                  ommers,
                  withdrawals,
                  maybeDeposits));
      return new BlockCreationResult(block, transactionResults);
    } catch (final SecurityModuleException ex) {
      throw new IllegalStateException("Failed to create block signature", ex);
    } catch (final CancellationException | StorageException ex) {
      throw ex;
    } catch (final Exception ex) {
      throw new IllegalStateException(
          "Block creation failed unexpectedly. Will restart on next block added to chain.", ex);
    }
  }

  @VisibleForTesting
  List<Deposit> findDepositsFromReceipts(final TransactionSelectionResults transactionResults) {
    return transactionResults.getReceipts().stream()
        .flatMap(receipt -> receipt.getLogsList().stream())
        .filter(log -> depositContractAddress.get().equals(log.getLogger()))
        .map(DepositDecoder::decodeFromLog)
        .toList();
  }

  record GasUsage(BlobGas excessBlobGas, BlobGas used) {}

  private GasUsage computeExcessBlobGas(
      final TransactionSelectionResults transactionResults, final ProtocolSpec newProtocolSpec) {

    if (newProtocolSpec.getFeeMarket().implementsDataFee()) {
      final var gasCalculator = newProtocolSpec.getGasCalculator();
      final int newBlobsCount =
          transactionResults.getTransactionsByType(TransactionType.BLOB).stream()
              .map(tx -> tx.getVersionedHashes().orElseThrow())
              .mapToInt(List::size)
              .sum();
      // casting parent excess blob gas to long since for the moment it should be well below that
      // limit
      BlobGas ecessBlobGas =
          BlobGas.of(
              gasCalculator.computeExcessBlobGas(
                  parentHeader.getExcessBlobGas().map(BlobGas::toLong).orElse(0L), newBlobsCount));
      BlobGas used = BlobGas.of(gasCalculator.blobGasCost(newBlobsCount));
      return new GasUsage(ecessBlobGas, used);
    }
    return null;
  }

  private TransactionSelectionResults selectTransactions(
      final ProcessableBlockHeader processableBlockHeader,
      final MutableWorldState disposableWorldState,
      final Optional<List<Transaction>> transactions,
      final Address miningBeneficiary,
      final Wei blobGasPrice,
      final ProtocolSpec protocolSpec)
      throws RuntimeException {
    final MainnetTransactionProcessor transactionProcessor = protocolSpec.getTransactionProcessor();

    final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory =
        protocolSpec.getTransactionReceiptFactory();

    final BlockTransactionSelector selector =
        new BlockTransactionSelector(
            transactionProcessor,
            protocolContext.getBlockchain(),
            disposableWorldState,
            transactionPool,
            processableBlockHeader,
            transactionReceiptFactory,
            minTransactionGasPrice,
            minBlockOccupancyRatio,
            isCancelled::get,
            miningBeneficiary,
            blobGasPrice,
            protocolSpec.getFeeMarket(),
            protocolSpec.getGasCalculator(),
            protocolSpec.getGasLimitCalculator(),
            protocolContext.getTransactionSelectorFactory());

    if (transactions.isPresent()) {
      return selector.evaluateTransactions(transactions.get());
    } else {
      return selector.buildTransactionListForBlock();
    }
  }

  private MutableWorldState duplicateWorldStateAtParent() {
    final Hash parentStateRoot = parentHeader.getStateRoot();
    return protocolContext
        .getWorldStateArchive()
        .getMutable(parentHeader, false)
        .orElseThrow(
            () -> {
              LOG.info("Unable to create block because world state is not available");
              return new CancellationException(
                  "World state not available for block "
                      + parentHeader.getNumber()
                      + " with state root "
                      + parentStateRoot);
            });
  }

  private List<BlockHeader> selectOmmers() {
    return Lists.newArrayList();
  }

  private ProcessableBlockHeader createPendingBlockHeader(
      final long timestamp,
      final Optional<Bytes32> maybePrevRandao,
      final ProtocolSpec protocolSpec) {
    final long newBlockNumber = parentHeader.getNumber() + 1;
    long gasLimit =
        protocolSpec
            .getGasLimitCalculator()
            .nextGasLimit(
                parentHeader.getGasLimit(),
                targetGasLimitSupplier.get().orElse(parentHeader.getGasLimit()),
                newBlockNumber);

    final DifficultyCalculator difficultyCalculator = protocolSpec.getDifficultyCalculator();
    final BigInteger difficulty =
        difficultyCalculator.nextDifficulty(timestamp, parentHeader, protocolContext);

    final Wei baseFee =
        Optional.of(protocolSpec.getFeeMarket())
            .filter(FeeMarket::implementsBaseFee)
            .map(BaseFeeMarket.class::cast)
            .map(
                feeMarket ->
                    feeMarket.computeBaseFee(
                        newBlockNumber,
                        parentHeader.getBaseFee().orElse(Wei.ZERO),
                        parentHeader.getGasUsed(),
                        feeMarket.targetGasUsed(parentHeader)))
            .orElse(null);

    final Bytes32 prevRandao = maybePrevRandao.orElse(null);
    return BlockHeaderBuilder.create()
        .parentHash(parentHeader.getHash())
        .coinbase(coinbase)
        .difficulty(Difficulty.of(difficulty))
        .number(newBlockNumber)
        .gasLimit(gasLimit)
        .timestamp(timestamp)
        .baseFee(baseFee)
        .prevRandao(prevRandao)
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
      final Address miningBeneficiary,
      final Wei blockReward,
      final boolean skipZeroBlockRewards,
      final ProtocolSpec protocolSpec) {

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
    final EvmAccount beneficiary = updater.getOrCreate(miningBeneficiary);

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

      final EvmAccount ommerCoinbase = updater.getOrCreate(ommerHeader.getCoinbase());
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

  @FunctionalInterface
  protected interface MiningBeneficiaryCalculator {
    Address getMiningBeneficiary(long blockNumber);
  }
}
