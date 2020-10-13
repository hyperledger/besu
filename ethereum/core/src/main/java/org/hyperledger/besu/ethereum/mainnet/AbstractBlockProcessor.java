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
package org.hyperledger.besu.ethereum.mainnet;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldState;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.core.fees.TransactionGasBudgetCalculator;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.OperationTracer;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractBlockProcessor implements BlockProcessor {
  @FunctionalInterface
  public interface TransactionReceiptFactory {

    TransactionReceipt create(
        TransactionProcessor.Result result, WorldState worldState, long gasUsed);
  }

  private static final Logger LOG = LogManager.getLogger();

  static final int MAX_GENERATION = 6;

  public static class Result implements BlockProcessor.Result {

    private static final AbstractBlockProcessor.Result FAILED =
        new AbstractBlockProcessor.Result(false, null);

    private final boolean successful;

    private final List<TransactionReceipt> receipts;

    public static AbstractBlockProcessor.Result successful(
        final List<TransactionReceipt> receipts) {
      return new AbstractBlockProcessor.Result(true, ImmutableList.copyOf(receipts));
    }

    public static AbstractBlockProcessor.Result failed() {
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

  private final AbstractBlockProcessor.TransactionReceiptFactory transactionReceiptFactory;

  final Wei blockReward;

  private final boolean skipZeroBlockRewards;

  private final MiningBeneficiaryCalculator miningBeneficiaryCalculator;

  private final TransactionGasBudgetCalculator gasBudgetCalculator;

  protected AbstractBlockProcessor(
      final TransactionProcessor transactionProcessor,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards,
      final TransactionGasBudgetCalculator gasBudgetCalculator) {
    this.transactionProcessor = transactionProcessor;
    this.transactionReceiptFactory = transactionReceiptFactory;
    this.blockReward = blockReward;
    this.miningBeneficiaryCalculator = miningBeneficiaryCalculator;
    this.skipZeroBlockRewards = skipZeroBlockRewards;
    this.gasBudgetCalculator = gasBudgetCalculator;
  }

  @Override
  public AbstractBlockProcessor.Result processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final List<BlockHeader> ommers,
      final PrivateMetadataUpdater privateMetadataUpdater) {

    final List<TransactionReceipt> receipts = new ArrayList<>();
    long currentGasUsed = 0;
    for (final Transaction transaction : transactions) {
      final long remainingGasBudget = blockHeader.getGasLimit() - currentGasUsed;
      if (!gasBudgetCalculator.hasBudget(
          transaction, blockHeader.getNumber(), blockHeader.getGasLimit(), currentGasUsed)) {
        LOG.info(
            "Block processing error: transaction gas limit {} exceeds available block budget remaining {}. Block {} Transaction {}",
            transaction.getGasLimit(),
            remainingGasBudget,
            blockHeader.getHash().toHexString(),
            transaction.getHash().toHexString());
        return AbstractBlockProcessor.Result.failed();
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
              OperationTracer.NO_TRACING,
              blockHashLookup,
              true,
              TransactionValidationParams.processingBlock(),
              privateMetadataUpdater);
      if (result.isInvalid()) {
        LOG.info(
            "Block processing error: transaction invalid '{}'. Block {} Transaction {}",
            result.getValidationResult().getInvalidReason(),
            blockHeader.getHash().toHexString(),
            transaction.getHash().toHexString());
        return AbstractBlockProcessor.Result.failed();
      }

      worldStateUpdater.commit();

      currentGasUsed += transaction.getGasLimit() - result.getGasRemaining();

      final TransactionReceipt transactionReceipt =
          transactionReceiptFactory.create(result, worldState, currentGasUsed);
      receipts.add(transactionReceipt);
    }

    if (!rewardCoinbase(worldState, blockHeader, ommers, skipZeroBlockRewards)) {
      // no need to log, rewardCoinbase logs the error.
      return AbstractBlockProcessor.Result.failed();
    }

    worldState.persist();
    return AbstractBlockProcessor.Result.successful(receipts);
  }

  protected MiningBeneficiaryCalculator getMiningBeneficiaryCalculator() {
    return miningBeneficiaryCalculator;
  }

  abstract boolean rewardCoinbase(
      final MutableWorldState worldState,
      final BlockHeader header,
      final List<BlockHeader> ommers,
      final boolean skipZeroBlockRewards);
}
