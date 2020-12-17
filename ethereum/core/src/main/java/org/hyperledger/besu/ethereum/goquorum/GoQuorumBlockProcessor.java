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
package org.hyperledger.besu.ethereum.goquorum;

import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.core.WorldUpdater;
import org.hyperledger.besu.ethereum.core.fees.TransactionGasBudgetCalculator;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MiningBeneficiaryCalculator;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.OperationTracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GoQuorumBlockProcessor extends MainnetBlockProcessor {

  private static final Logger LOG = LogManager.getLogger();

  // TODO-goquorum proper wiring
  private final GoQuorumPrivateDatabase privateDatabase = new GoQuorumPrivateDatabase();

  // TODO-goquorum proper wiring of GoQuorumPrivateStateRootResolver

  public GoQuorumBlockProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final TransactionReceiptFactory transactionReceiptFactory,
      final Wei blockReward,
      final MiningBeneficiaryCalculator miningBeneficiaryCalculator,
      final boolean skipZeroBlockRewards,
      final TransactionGasBudgetCalculator gasBudgetCalculator) {
    super(
        transactionProcessor,
        transactionReceiptFactory,
        blockReward,
        miningBeneficiaryCalculator,
        skipZeroBlockRewards,
        gasBudgetCalculator);

    if (!(transactionProcessor instanceof GoQuorumTransactionProcessor)) {
      throw new IllegalStateException(
          "GoQuorumBlockProcessor requires an instance of GoQuorumTransactionProcessor");
    }
  }

  @Override
  public Result processBlock(
      final Blockchain blockchain,
      final MutableWorldState worldState,
      final MutableWorldState privateWorldState,
      final Block block) {
    final BlockHeader blockHeader = block.getHeader();
    final List<Transaction> transactions = block.getBody().getTransactions();
    final List<BlockHeader> ommers = block.getBody().getOmmers();

    final List<TransactionReceipt> publicTxReceipts = new ArrayList<>();
    final Map<Hash, TransactionReceipt> privateTxReceipts = new HashMap<>();
    long currentGasUsed = 0;

    // Begin processing individual tx
    for (final Transaction transaction : transactions) {
      if (!hasAvailableBlockBudget(blockHeader, transaction, currentGasUsed)) {
        return AbstractBlockProcessor.Result.failed();
      }

      // TODO-goquorum can we reduce duplication??

      final WorldUpdater worldStateUpdater = worldState.updater();
      final WorldUpdater privateWorldStateUpdater = privateWorldState.updater();
      final BlockHashLookup blockHashLookup = new BlockHashLookup(blockHeader, blockchain);
      final Address miningBeneficiary =
          miningBeneficiaryCalculator.calculateBeneficiary(blockHeader);

      final TransactionProcessingResult result;
      if (transaction.isGoQuorumPrivateTransaction()) {
        result =
            ((GoQuorumTransactionProcessor) transactionProcessor)
                .processTransaction(
                    blockchain,
                    worldStateUpdater,
                    privateWorldStateUpdater,
                    blockHeader,
                    transaction,
                    miningBeneficiary,
                    OperationTracer.NO_TRACING,
                    blockHashLookup,
                    TransactionValidationParams.processingBlock());
      } else {
        result =
            transactionProcessor.processTransaction(
                blockchain,
                worldStateUpdater,
                blockHeader,
                transaction,
                miningBeneficiary,
                OperationTracer.NO_TRACING,
                blockHashLookup,
                true,
                TransactionValidationParams.processingBlock());
      }

      if (result.isInvalid()) {
        LOG.info(
            "Block processing error: transaction invalid '{}'. Block {} Transaction {}",
            result.getValidationResult().getInvalidReason(),
            blockHeader.getHash().toHexString(),
            transaction.getHash().toHexString());
        return AbstractBlockProcessor.Result.failed();
      }

      worldStateUpdater.commit();
      privateWorldStateUpdater.commit();

      currentGasUsed += transaction.getGasLimit() - result.getGasRemaining();

      // Only the logs are used for the
      final TransactionProcessingResult publicResult =
          TransactionProcessingResult.successful(
              Collections.emptyList(),
              result.getEstimateGasUsedByTransaction(),
              result.getGasRemaining(),
              result.getOutput(),
              result.getValidationResult());

      final TransactionReceipt publicTransactionReceipt =
          transactionReceiptFactory.create(transaction.getType(), publicResult, worldState, 0);
      publicTxReceipts.add(publicTransactionReceipt);

      final TransactionReceipt privateTransactionReceipt =
          transactionReceiptFactory.create(
              transaction.getType(), result, privateWorldState, currentGasUsed);
      privateTxReceipts.put(transaction.getHash(), privateTransactionReceipt);
    }
    // End processing individual tx

    if (!rewardCoinbase(worldState, blockHeader, ommers, skipZeroBlockRewards)) {
      // no need to log, rewardCoinbase logs the error.
      return AbstractBlockProcessor.Result.failed();
    }

    worldState.persist(blockHeader.getHash());
    privateWorldState.persist(null);

    privateDatabase.saveReceipts(privateTxReceipts);

    GoQuorumBlockValidator.privateStateRootResolver.updatePrivateStateRootHash(
        worldState.rootHash(), privateWorldState.rootHash());

    return Result.successful(publicTxReceipts);
  }
}
