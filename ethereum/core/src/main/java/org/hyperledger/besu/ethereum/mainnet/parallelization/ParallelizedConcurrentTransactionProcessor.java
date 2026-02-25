/*
 * Copyright contributors to Besu.
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
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.SlowBlockTracer;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.AccessLocationTracker;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BlockAccessListBuilder;
import org.hyperledger.besu.ethereum.mainnet.systemcall.BlockProcessingContext;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.PathBasedWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.tracing.EVMExecutionMetricsTracer;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.tracing.TracerAggregator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.google.common.annotations.VisibleForTesting;

/**
 * Optimizes transaction processing by executing transactions in parallel within a given block.
 * Transactions are executed optimistically in a non-blocking manner. After execution, the class
 * checks for potential conflicts among transactions to ensure data integrity before applying the
 * results to the world state.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ParallelizedConcurrentTransactionProcessor extends ParallelBlockTransactionProcessor {

  private final MainnetTransactionProcessor transactionProcessor;

  private final TransactionCollisionDetector transactionCollisionDetector;

  private final BlockProcessingContext blockProcessingContext;

  /**
   * Constructs a PreloadConcurrentTransactionProcessor with a specified transaction processor. This
   * processor is responsible for the individual processing of transactions.
   *
   * @param transactionProcessor The transaction processor for processing individual transactions.
   * @param blockProcessingContext The block processing context containing operation tracers and
   *     other context.
   */
  public ParallelizedConcurrentTransactionProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final BlockProcessingContext blockProcessingContext) {
    this.transactionProcessor = transactionProcessor;
    this.transactionCollisionDetector = new TransactionCollisionDetector();
    this.blockProcessingContext = blockProcessingContext;
  }

  @VisibleForTesting
  public ParallelizedConcurrentTransactionProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final TransactionCollisionDetector transactionCollisionDetector) {
    this.transactionProcessor = transactionProcessor;
    this.transactionCollisionDetector = transactionCollisionDetector;
    this.blockProcessingContext = null; // For testing only
  }

  @VisibleForTesting
  public ParallelizedConcurrentTransactionProcessor(
      final MainnetTransactionProcessor transactionProcessor,
      final TransactionCollisionDetector transactionCollisionDetector,
      final BlockProcessingContext blockProcessingContext) {
    this.transactionProcessor = transactionProcessor;
    this.transactionCollisionDetector = transactionCollisionDetector;
    this.blockProcessingContext = blockProcessingContext;
  }

  @Override
  @VisibleForTesting
  protected ParallelizedTransactionContext runTransaction(
      final ProtocolContext protocolContext,
      final BlockHeader blockHeader,
      final int transactionLocation,
      final Transaction transaction,
      final Address miningBeneficiary,
      final BlockHashLookup blockHashLookup,
      final Wei blobGasPrice,
      final Optional<BlockAccessListBuilder> blockAccessListBuilder) {

    final BonsaiWorldState ws = getWorldState(protocolContext, blockHeader);
    if (ws == null) return null;

    try {
      ws.disableCacheMerkleTrieLoader();
      final ParallelizedTransactionContext.Builder contextBuilder =
          new ParallelizedTransactionContext.Builder();
      final PathBasedWorldStateUpdateAccumulator<?> roundWorldStateUpdater =
          (PathBasedWorldStateUpdateAccumulator<?>) ws.updater();
      final WorldUpdater transactionUpdater = roundWorldStateUpdater.updater();
      final Optional<AccessLocationTracker> transactionLocationTracker =
          blockAccessListBuilder.map(
              b ->
                  BlockAccessListBuilder.createTransactionAccessLocationTracker(
                      transactionLocation));

      // Create the mining beneficiary tracer for parallel execution collision detection
      final OperationTracer miningBeneficiaryTracer =
          new OperationTracer() {
            @Override
            public void traceBeforeRewardTransaction(
                final WorldView worldView,
                final org.hyperledger.besu.datatypes.Transaction tx,
                final Wei miningReward) {
              /*
               * This part checks if the mining beneficiary's account was accessed before increasing its balance for rewards.
               * Indeed, if the transaction has interacted with the address to read or modify it,
               * it means that the value is necessary for the proper execution of the transaction and will therefore be considered in collision detection.
               * If this is not the case, we can ignore this address during conflict detection.
               */
              if (transactionCollisionDetector
                  .getAddressesTouchedByTransaction(
                      transaction, Optional.of(roundWorldStateUpdater))
                  .contains(miningBeneficiary)) {
                contextBuilder.isMiningBeneficiaryTouchedPreRewardByTransaction(true);
              }
              contextBuilder.miningBeneficiaryReward(miningReward);
            }
          };

      // Create separate background tracer for parallel execution
      // This includes a copy of EVMExecutionMetricsTracer if present in the block tracer
      final OperationTracer backgroundBlockTracer = createBackgroundTracer(blockProcessingContext);

      // Compose the background tracer with the mining beneficiary tracer
      final OperationTracer composedTracer =
          backgroundBlockTracer != null
              ? TracerAggregator.combining(backgroundBlockTracer, miningBeneficiaryTracer)
              : miningBeneficiaryTracer;

      final TransactionProcessingResult result =
          transactionProcessor.processTransaction(
              transactionUpdater,
              blockHeader,
              transaction.detachedCopy(),
              miningBeneficiary,
              composedTracer,
              blockHashLookup,
              TransactionValidationParams.processingBlock(),
              blobGasPrice,
              transactionLocationTracker);

      // commit the accumulator in order to apply all the modifications
      transactionUpdater.commit();
      roundWorldStateUpdater.commit();

      contextBuilder
          .transactionAccumulator(ws.getAccumulator())
          .transactionProcessingResult(result)
          .backgroundTracer(backgroundBlockTracer);

      final ParallelizedTransactionContext parallelizedTransactionContext = contextBuilder.build();
      if (!parallelizedTransactionContext.isMiningBeneficiaryTouchedPreRewardByTransaction()) {
        /*
         * If the address of the mining beneficiary has been touched only for adding rewards,
         * we remove it from the accumulator to avoid a false positive collision.
         * The balance will be increased during the sequential processing.
         */
        roundWorldStateUpdater.getAccountsToUpdate().remove(miningBeneficiary);
      }
      return parallelizedTransactionContext;
    } catch (Exception ex) {
      // no op as failing to get worldstate
      return null;
    } finally {
      if (ws != null) ws.close();
    }
  }

  /**
   * Applies the results of parallelized transactions to the world state after checking for
   * conflicts.
   *
   * <p>If a transaction was executed optimistically without any detected conflicts, its result is
   * directly applied to the world state. If there is a conflict, this method does not apply the
   * transaction's modifications directly to the world state. Instead, it caches the data read from
   * the database during the transaction's execution. This cached data is then used to optimize the
   * replay of the transaction by reducing the need for additional reads from the disk, thereby
   * making the replay process faster. This approach ensures that the integrity of the world state
   * is maintained while optimizing the performance of transaction processing.
   *
   * @param worldState Mutable world state intended for applying transaction results.
   * @param miningBeneficiary Address of the beneficiary for mining rewards.
   * @param transaction Transaction for which the result is to be applied.
   * @param transactionLocation Index of the transaction within the block.
   * @param confirmedParallelizedTransactionCounter Metric counter for confirmed parallelized
   *     transactions
   * @param conflictingButCachedTransactionCounter Metric counter for conflicting but cached
   *     transactions
   * @return Optional containing the transaction processing result if applied, or empty if the
   *     transaction needs to be replayed due to a conflict.
   */
  @Override
  public Optional<TransactionProcessingResult> getProcessingResult(
      final MutableWorldState worldState,
      final Address miningBeneficiary,
      final Transaction transaction,
      final int transactionLocation,
      final Optional<Counter> confirmedParallelizedTransactionCounter,
      final Optional<Counter> conflictingButCachedTransactionCounter) {

    final CompletableFuture<ParallelizedTransactionContext> future = futures[transactionLocation];

    if (future != null && future.isDone()) {
      final ParallelizedTransactionContext parallelizedTransactionContext = future.resultNow();
      if (parallelizedTransactionContext == null) {
        return Optional.empty();
      }

      final PathBasedWorldState pathBasedWorldState = (PathBasedWorldState) worldState;
      final PathBasedWorldStateUpdateAccumulator blockAccumulator =
          (PathBasedWorldStateUpdateAccumulator) pathBasedWorldState.updater();
      final PathBasedWorldStateUpdateAccumulator<?> transactionAccumulator =
          parallelizedTransactionContext.transactionAccumulator();
      final TransactionProcessingResult transactionProcessingResult =
          parallelizedTransactionContext.transactionProcessingResult();
      final boolean hasCollision =
          transactionCollisionDetector.hasCollision(
              transaction, miningBeneficiary, parallelizedTransactionContext, blockAccumulator);
      if (transactionProcessingResult.isSuccessful() && !hasCollision) {
        final MutableAccount miningBeneficiaryAccount =
            blockAccumulator.getOrCreate(miningBeneficiary);
        Wei reward = parallelizedTransactionContext.miningBeneficiaryReward();
        if (!reward.isZero() || !transactionProcessor.getClearEmptyAccounts()) {
          miningBeneficiaryAccount.incrementBalance(reward);
        }

        final Wei miningBeneficiaryPostBalance = miningBeneficiaryAccount.getBalance();
        transactionProcessingResult
            .getPartialBlockAccessView()
            .ifPresent(
                partialBlockAccessView ->
                    partialBlockAccessView.accountChanges().stream()
                        .filter(
                            accountChanges -> accountChanges.getAddress().equals(miningBeneficiary))
                        .findFirst()
                        .ifPresent(
                            accountChanges ->
                                accountChanges.setPostBalance(miningBeneficiaryPostBalance)));

        blockAccumulator.importStateChangesFromSource(transactionAccumulator);

        // Consolidate tracer results from successful parallel execution
        consolidateTracerResults(parallelizedTransactionContext);

        if (confirmedParallelizedTransactionCounter.isPresent()) {
          confirmedParallelizedTransactionCounter.get().inc();
          transactionProcessingResult.setIsProcessedInParallel(Optional.of(Boolean.TRUE));
          transactionProcessingResult.accumulator = transactionAccumulator;
        }
        return Optional.of(transactionProcessingResult);
      } else {
        blockAccumulator.importPriorStateFromSource(transactionAccumulator);
        if (conflictingButCachedTransactionCounter.isPresent())
          conflictingButCachedTransactionCounter.get().inc();
        // If there is a conflict, we return an empty result to signal the block processor to
        // re-execute the transaction.
        return Optional.empty();
      }
    }
    if (future != null) {
      future.cancel(true);
    }
    return Optional.empty();
  }

  /**
   * Creates a tracer for background (parallel) transaction execution. Tracers with mutable state
   * (e.g., EVMExecutionMetricsTracer, SlowBlockTracer) cannot be shared between threads. This
   * method creates fresh EVMExecutionMetricsTracer instances for background execution so that EVM
   * opcode metrics are captured and can be merged back after conflict-free parallel execution.
   *
   * @param blockProcessingContext the block processing context containing the original tracer
   * @return a background tracer instance, or null if no block tracer exists
   */
  private OperationTracer createBackgroundTracer(
      final BlockProcessingContext blockProcessingContext) {
    if (blockProcessingContext == null) {
      return null;
    }

    final OperationTracer blockTracer = blockProcessingContext.getOperationTracer();
    if (blockTracer == null) {
      return null;
    }

    // Check if the block tracer contains any metrics tracer (EVMExecutionMetricsTracer directly,
    // or wrapped inside a SlowBlockTracer or TracerAggregator)
    if (hasMetricsTracer(blockTracer)) {
      // Create a new EVMExecutionMetricsTracer instance for background execution
      final EVMExecutionMetricsTracer backgroundMetricsTracer = new EVMExecutionMetricsTracer();

      // If the block tracer is a standalone metrics tracer or SlowBlockTracer, return background
      if (blockTracer instanceof EVMExecutionMetricsTracer
          || blockTracer instanceof SlowBlockTracer) {
        return backgroundMetricsTracer;
      }

      // If the block tracer is a TracerAggregator, create a new aggregator with
      // the background EVMExecutionMetricsTracer replacing metrics-containing tracers
      if (blockTracer instanceof TracerAggregator) {
        return createBackgroundTracerAggregator(
            (TracerAggregator) blockTracer, backgroundMetricsTracer);
      }
    }

    // For other tracer types that don't need separate instances, return the original
    return blockTracer;
  }

  /**
   * Creates a background TracerAggregator by replacing EVMExecutionMetricsTracer and
   * SlowBlockTracer instances with the provided background metrics tracer, while preserving all
   * other tracers. Uses a flag to avoid adding the background tracer twice if both types are
   * present.
   */
  private OperationTracer createBackgroundTracerAggregator(
      final TracerAggregator originalAggregator,
      final EVMExecutionMetricsTracer backgroundMetricsTracer) {

    final List<OperationTracer> originalTracers = originalAggregator.getTracers();
    final List<OperationTracer> backgroundTracers = new ArrayList<>(originalTracers.size());
    boolean metricsTracerAdded = false;
    for (final OperationTracer tracer : originalTracers) {
      if (tracer instanceof EVMExecutionMetricsTracer || tracer instanceof SlowBlockTracer) {
        if (!metricsTracerAdded) {
          backgroundTracers.add(backgroundMetricsTracer);
          metricsTracerAdded = true;
        }
        // Skip duplicate — don't add the background tracer twice
      } else if (tracer instanceof TracerAggregator) {
        backgroundTracers.add(
            createBackgroundTracerAggregator((TracerAggregator) tracer, backgroundMetricsTracer));
      } else {
        backgroundTracers.add(tracer);
      }
    }

    return TracerAggregator.of(backgroundTracers.toArray(new OperationTracer[0]));
  }

  /**
   * Consolidates tracer results from successful parallel execution into the block's main tracer.
   * This implements matkt's suggestion to merge background tracer results when there are no
   * conflicts. Also increments the SlowBlockTracer tx_count for confirmed parallel transactions.
   *
   * @param parallelContext the parallel transaction context containing the background tracer
   */
  private void consolidateTracerResults(final ParallelizedTransactionContext parallelContext) {

    parallelContext
        .backgroundTracer()
        .ifPresent(
            backgroundTracer -> {
              if (blockProcessingContext != null) {
                final OperationTracer blockTracer = blockProcessingContext.getOperationTracer();
                if (blockTracer != null) {
                  mergeTracerResults(backgroundTracer, blockTracer);
                  // Increment tx_count on the SlowBlockTracer for this confirmed parallel tx
                  findSlowBlockTracer(blockTracer)
                      .ifPresent(sbt -> sbt.getExecutionStats().incrementTransactionCount());
                }
              }
            });
  }

  /**
   * Merges tracer results from parallel execution into the block's main tracer. Currently focuses
   * on EVMExecutionMetricsTracer consolidation.
   */
  private void mergeTracerResults(
      final OperationTracer backgroundTracer, final OperationTracer blockTracer) {

    // Find EVMExecutionMetricsTracer instances in both tracers
    final Optional<EVMExecutionMetricsTracer> backgroundMetrics =
        findEVMExecutionMetricsTracer(backgroundTracer);
    final Optional<EVMExecutionMetricsTracer> blockMetrics =
        findEVMExecutionMetricsTracer(blockTracer);

    // Merge metrics if both tracers contain EVMExecutionMetricsTracer
    if (backgroundMetrics.isPresent() && blockMetrics.isPresent()) {
      blockMetrics.get().mergeFrom(backgroundMetrics.get());
    }
  }

  /**
   * Extracts an EVMExecutionMetricsTracer from a tracer, unwrapping SlowBlockTracer and
   * TracerAggregator as needed.
   *
   * @param tracer the tracer to search within
   * @return the EVMExecutionMetricsTracer if found
   */
  private Optional<EVMExecutionMetricsTracer> findEVMExecutionMetricsTracer(
      final OperationTracer tracer) {
    if (tracer instanceof EVMExecutionMetricsTracer) {
      return Optional.of((EVMExecutionMetricsTracer) tracer);
    } else if (tracer instanceof SlowBlockTracer) {
      final EVMExecutionMetricsTracer inner =
          ((SlowBlockTracer) tracer).getEVMExecutionMetricsTracer();
      return inner != null ? Optional.of(inner) : Optional.empty();
    } else if (tracer instanceof TracerAggregator) {
      // Search for EVMExecutionMetricsTracer directly, and also inside any SlowBlockTracer
      final Optional<EVMExecutionMetricsTracer> direct =
          ((TracerAggregator) tracer).findTracer(EVMExecutionMetricsTracer.class);
      if (direct.isPresent()) {
        return direct;
      }
      final Optional<SlowBlockTracer> sbt =
          ((TracerAggregator) tracer).findTracer(SlowBlockTracer.class);
      if (sbt.isPresent()) {
        final EVMExecutionMetricsTracer inner = sbt.get().getEVMExecutionMetricsTracer();
        return inner != null ? Optional.of(inner) : Optional.empty();
      }
    }
    return Optional.empty();
  }

  /**
   * Checks whether the given tracer contains an EVMExecutionMetricsTracer, either directly, inside
   * a SlowBlockTracer, or inside a TracerAggregator.
   */
  private boolean hasMetricsTracer(final OperationTracer tracer) {
    if (tracer instanceof EVMExecutionMetricsTracer) {
      return true;
    } else if (tracer instanceof SlowBlockTracer) {
      return true; // SlowBlockTracer always wraps an EVMExecutionMetricsTracer
    } else if (tracer instanceof TracerAggregator) {
      return TracerAggregator.hasTracer(tracer, EVMExecutionMetricsTracer.class)
          || TracerAggregator.hasTracer(tracer, SlowBlockTracer.class);
    }
    return false;
  }

  /** Finds a SlowBlockTracer in the given tracer, checking directly and inside TracerAggregator. */
  private Optional<SlowBlockTracer> findSlowBlockTracer(final OperationTracer tracer) {
    if (tracer instanceof SlowBlockTracer) {
      return Optional.of((SlowBlockTracer) tracer);
    } else if (tracer instanceof TracerAggregator) {
      return ((TracerAggregator) tracer).findTracer(SlowBlockTracer.class);
    }
    return Optional.empty();
  }
}
