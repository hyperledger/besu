/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.mainnet.parallelization;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldView;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public class PreloadConcurrentTransactionProcessor {

  private static final Executor executor = Executors.newFixedThreadPool(4);

  private final MainnetTransactionProcessor transactionProcessor;

  private final TransactionCollisionDetector transactionCollisionDetector;

  private final Map<Long, ParallelizedTransactionContext> parallelizedTransactionContextByLocation =
      new ConcurrentHashMap<>();

  int confirmedParallelizedTransaction = 0;
  int conflictingButCachedTransaction = 0;

  public PreloadConcurrentTransactionProcessor(
      final MainnetTransactionProcessor transactionProcessor) {
    this.transactionProcessor = transactionProcessor;
    this.transactionCollisionDetector = new TransactionCollisionDetector();
  }

  public void runAsyncPreloadBlock(
      final MutableWorldState worldState,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final Address miningBeneficiary,
      final BlockHashOperation.BlockHashLookup blockHashLookup,
      final Wei blobGasPrice,
      final PrivateMetadataUpdater privateMetadataUpdater) {
    confirmedParallelizedTransaction = 0;
    conflictingButCachedTransaction = 0;
    for (int i = 0; i < transactions.size(); i++) {
      final Transaction transaction = transactions.get(i);
      final long transactionIndex = i;
      CompletableFuture.runAsync(
          () -> {
            DiffBasedWorldState roundWorldState =
                new BonsaiWorldState((BonsaiWorldState) worldState);

            final ParallelizedTransactionContext.Builder contextBuilder =
                new ParallelizedTransactionContext.Builder();
            final DiffBasedWorldStateUpdateAccumulator<?> roundWorldStateUpdater =
                (DiffBasedWorldStateUpdateAccumulator<?>) roundWorldState.updater();
            final TransactionProcessingResult result =
                transactionProcessor.processTransaction(
                    roundWorldStateUpdater,
                    blockHeader,
                    transaction,
                    miningBeneficiary,
                    new OperationTracer() {
                      @Override
                      public void traceTransactionBeforeMiningReward(
                          final WorldView worldView,
                          final org.hyperledger.besu.datatypes.Transaction tx,
                          final Wei miningReward) {
                        if (transactionCollisionDetector
                            .getAddressesTouchedByTransaction(
                                transaction, Optional.of(roundWorldStateUpdater))
                            .contains(miningBeneficiary)) {
                          contextBuilder.isMiningBeneficiaryTouchedPreRewardByTransaction(true);
                        }
                        contextBuilder.miningBeneficiaryReward(miningReward);
                      }
                    },
                    blockHashLookup,
                    true,
                    TransactionValidationParams.processingBlock(),
                    privateMetadataUpdater,
                    blobGasPrice);

            // commit the accumulator
            roundWorldState.getAccumulator().commit();

            contextBuilder
                .transactionAccumulator(roundWorldState.getAccumulator())
                .transactionProcessingResult(result);

            final ParallelizedTransactionContext parallelizedTransactionContext =
                contextBuilder.build();
            if (!parallelizedTransactionContext
                .isMiningBeneficiaryTouchedPreRewardByTransaction()) {
              /*
               *If the address of the mining beneficiary has been touched only for adding rewards,
               *we remove it from the accumulator to avoid a false positive collision.
               * The balance will be increased during the sequential processing.
               */
              roundWorldStateUpdater.getAccountsToUpdate().remove(miningBeneficiary);
            }
            parallelizedTransactionContextByLocation.put(
                transactionIndex, parallelizedTransactionContext);
          },
          executor);
    }
  }

  public Optional<TransactionProcessingResult> applyPreloadBlockResult(
      final MutableWorldState worldState,
      final Address miningBeneficiary,
      final Transaction transaction,
      final long transactionIndex) {
    final DiffBasedWorldState diffBasedWorldState = (DiffBasedWorldState) worldState;
    final DiffBasedWorldStateUpdateAccumulator blockAccumulator =
        (DiffBasedWorldStateUpdateAccumulator) diffBasedWorldState.updater();
    final ParallelizedTransactionContext parallelizedTransactionContext =
        parallelizedTransactionContextByLocation.get(transactionIndex);
    if (parallelizedTransactionContext != null) {
      final DiffBasedWorldStateUpdateAccumulator<?> transactionAccumulator =
          parallelizedTransactionContext.transactionAccumulator();
      final TransactionProcessingResult transactionProcessingResult =
          parallelizedTransactionContext.transactionProcessingResult();
      final boolean hasCollision =
          transactionCollisionDetector.hasCollision(
              transaction, miningBeneficiary, parallelizedTransactionContext, blockAccumulator);
      if (transactionProcessingResult.isSuccessful() && !hasCollision) {
        blockAccumulator
            .getOrCreate(miningBeneficiary)
            .incrementBalance(parallelizedTransactionContext.miningBeneficiaryReward());

        blockAccumulator.cloneFromUpdaterWithPreloader(transactionAccumulator);

        confirmedParallelizedTransaction++;
        return Optional.of(transactionProcessingResult);
      } else {
        blockAccumulator.clonePriorFromUpdater(transactionAccumulator);
        conflictingButCachedTransaction++;
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  public int getConfirmedParallelizedTransaction() {
    return confirmedParallelizedTransaction;
  }

  public int getConflictingButCachedTransaction() {
    return conflictingButCachedTransaction;
  }
}
