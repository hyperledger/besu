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
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.LazyBonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.DiffBasedWorldState;
import org.hyperledger.besu.ethereum.trie.diffbased.common.worldview.accumulator.DiffBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.evm.operation.BlockHashOperation;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@SuppressWarnings({"unchecked", "rawtypes", "unused"})
public class PreloadConcurrentTransactionProcessor {

  private static final Executor executor = Executors.newFixedThreadPool(4);

  private final MainnetTransactionProcessor transactionProcessor;

  private final TransactionCollisionDetector transactionCollisionDetector;

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
                new BonsaiWorldState(
                    (BonsaiWorldState) worldState, new LazyBonsaiCachedMerkleTrieLoader());
            WorldUpdater roundWorldStateUpdater = roundWorldState.updater();
            final TransactionProcessingResult result =
                transactionProcessor.processTransaction(
                    roundWorldStateUpdater,
                    blockHeader,
                    transaction,
                    miningBeneficiary,
                    OperationTracer.NO_TRACING,
                    blockHashLookup,
                    true,
                    TransactionValidationParams.processingBlock(),
                    privateMetadataUpdater,
                    blobGasPrice);
            roundWorldState.getAccumulator().commit();
            transactionCollisionDetector.saveParallelizedTransactionProcessingResult(
                transactionIndex, roundWorldState.getAccumulator(), result);
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
    final DiffBasedWorldStateUpdateAccumulator diffBasedWorldStateUpdateAccumulator =
        (DiffBasedWorldStateUpdateAccumulator) diffBasedWorldState.updater();
    final Optional<TransactionProcessingResult> maybeTransactionProcessingResult;
    final DiffBasedWorldStateUpdateAccumulator<?> transactionAccumulator =
        transactionCollisionDetector.getAccumulatorByTransaction().get(transactionIndex);
    if (transactionAccumulator != null
        && !transactionCollisionDetector.checkConflicts(
            miningBeneficiary,
            transaction,
            transactionAccumulator,
            diffBasedWorldStateUpdateAccumulator)) {
      diffBasedWorldStateUpdateAccumulator.cloneFromUpdaterWithPreloader(transactionAccumulator);
      maybeTransactionProcessingResult =
          Optional.of(transactionCollisionDetector.getResultByTransaction().get(transactionIndex));
      confirmedParallelizedTransaction++;
    } else {
      if (transactionAccumulator != null) {
        diffBasedWorldStateUpdateAccumulator.clonePriorFromUpdater(transactionAccumulator);
        conflictingButCachedTransaction++;
      }
      maybeTransactionProcessingResult = Optional.empty();
    }
    return maybeTransactionProcessingResult;
  }

  public int getConfirmedParallelizedTransaction() {
    return confirmedParallelizedTransaction;
  }

  public int getConflictingButCachedTransaction() {
    return conflictingButCachedTransaction;
  }
}
