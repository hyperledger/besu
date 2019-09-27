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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableAccount;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainnetBlockProcessor implements BlockProcessor {

  @FunctionalInterface
  public interface TransactionReceiptFactory {

    TransactionReceipt create(
        TransactionProcessor.Result result, WorldState worldState, long gasUsed);
  }

  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_GENERATION = 6;

  public static class Result implements BlockProcessor.Result {

    private static final Result FAILED = new Result(false, null);

    private final boolean successful;

    private final List<TransactionReceipt> receipts;

    public static Result successful(final List<TransactionReceipt> receipts) {
      return new Result(true, ImmutableList.copyOf(receipts));
    }

    public static Result failed() {
      return FAILED;
    }

    Result(final boolean successful, final List<TransactionReceipt> receipts) {
      this.successful = successful;
      this.receipts = receipts;
    }

    @Override
    public List<TransactionReceipt> getReceipts() {
      return receipts;
    }

    @Override
    public boolean isSuccessful() {
      return successful;
    }
  }

  private final TransactionProcessor transactionProcessor;

  private final TransactionReceiptFactory transactionReceiptFactory;

  private final Wei blockReward;

  private final boolean skipZeroBlockRewards;

  private final MiningBeneficiaryCalculator miningBeneficiaryCalculator;

  public MainnetBlockProcessor(
      final TransactionProcessor transactionProcessor,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards) {
    this.transactionProcessor = transactionProcessor;
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.blockReward = blockReward;
    this.miningBeneficiaryCalculator = miningBeneficiaryCalculator;
    this.skipZeroBlockRewards = skipZeroBlockRewards;
  }

  @Override
  public Result processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers) {

    long gasUsed = 0;
    final List<TransactionReceipt> receipts = new ArrayList<>();

    for (final Transaction transaction : transactions) {
      final long remainingGasBudget = blockHeader.getGasLimit() - gasUsed;
      if (Long.compareUnsigned(transaction.getGasLimit(), remainingGasBudget) > 0) {
        LOG.warn(
            "Transaction processing error: transaction gas limit {} exceeds available block budget remaining {}",
            transaction.getGasLimit(),
            remainingGasBudget);
        return Result.failed();
      }

      final WorldUpdater worldStateUpdater = worldState.updater();
      final BlockHashLookup blockHashLookup = new BlockHashLookup(blockHeader, blockchain);
      final Address miningBeneficiary =
          miningBeneficiaryCalculator.calculateBeneficiary(blockHeader);

      final TransactionProcessor.Result result =
          transactionProcessor.processTransaction(
              blockchain,
              worldStateUpdater,
              blockHeader,
              transaction,
              miningBeneficiary,
              blockHashLookup,
              true,
              TransactionValidationParams.processingBlock());
      if (result.isInvalid()) {
        return Result.failed();
      }

      worldStateUpdater.commit();
      gasUsed = transaction.getGasLimit() - result.getGasRemaining() + gasUsed;
      final TransactionReceipt transactionReceipt =
          transactionReceiptFactory.create(result, worldState, gasUsed);
      receipts.add(transactionReceipt);
    }

    if (!rewardCoinbase(worldState, blockHeader, ommers, skipZeroBlockRewards)) {
      return Result.failed();
    }

    worldState.persist();
    return Result.successful(receipts);
  }

  private boolean rewardCoinbase(
      final MutableWorldState worldState,
      final ProcessableBlockHeader header,
      final List<BlockHeader> ommers,
      final boolean skipZeroBlockRewards) {
    if (skipZeroBlockRewards && blockReward.isZero()) {
      return true;
    }
    final Wei coinbaseReward = blockReward.plus(blockReward.times(ommers.size()).dividedBy(32));
    final WorldUpdater updater = worldState.updater();
    final MutableAccount coinbase = updater.getOrCreate(header.getCoinbase());

    coinbase.incrementBalance(coinbaseReward);
    for (final BlockHeader ommerHeader : ommers) {
      if (ommerHeader.getNumber() - header.getNumber() > MAX_GENERATION) {
        LOG.warn(
            "Block processing error: ommer block number {} more than {} generations current block number {}",
            ommerHeader.getNumber(),
            MAX_GENERATION,
            header.getNumber());
        return false;
      }

      final MutableAccount ommerCoinbase = updater.getOrCreate(ommerHeader.getCoinbase());
      final long distance = header.getNumber() - ommerHeader.getNumber();
      final Wei ommerReward = blockReward.minus(blockReward.times(distance).dividedBy(8));
      ommerCoinbase.incrementBalance(ommerReward);
    }

    updater.commit();

    return true;
  }
}
