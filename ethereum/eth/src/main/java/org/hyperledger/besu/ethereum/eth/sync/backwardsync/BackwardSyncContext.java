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
package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import static org.hyperledger.besu.util.FutureUtils.exceptionallyCompose;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.traceLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.util.Subscribers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackwardSyncContext {
  private static final Logger LOG = LoggerFactory.getLogger(BackwardSyncContext.class);
  public static final int BATCH_SIZE = 200;
  private static final int DEFAULT_MAX_RETRIES = 20;
  private static final long DEFAULT_MILLIS_BETWEEN_RETRIES = 5000;

  protected final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final SyncState syncState;
  private final AtomicReference<Status> currentBackwardSyncStatus = new AtomicReference<>();
  private final BackwardChain backwardChain;
  private int batchSize = BATCH_SIZE;
  private final int maxRetries;
  private final long millisBetweenRetries = DEFAULT_MILLIS_BETWEEN_RETRIES;
  private final Subscribers<BadChainListener> badChainListeners = Subscribers.create();

  public BackwardSyncContext(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final EthContext ethContext,
      final SyncState syncState,
      final BackwardChain backwardChain) {
    this(
        protocolContext,
        protocolSchedule,
        metricsSystem,
        ethContext,
        syncState,
        backwardChain,
        DEFAULT_MAX_RETRIES);
  }

  public BackwardSyncContext(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final EthContext ethContext,
      final SyncState syncState,
      final BackwardChain backwardChain,
      final int maxRetries) {

    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
    this.syncState = syncState;
    this.backwardChain = backwardChain;
    this.maxRetries = maxRetries;
  }

  public synchronized boolean isSyncing() {
    return Optional.ofNullable(currentBackwardSyncStatus.get())
        .map(status -> status.currentFuture.isDone())
        .orElse(Boolean.FALSE);
  }

  public synchronized CompletableFuture<Void> syncBackwardsUntil(final Hash newBlockHash) {
    return syncBackwardsUntil(() -> newBlockHash, () -> backwardChain.addNewHash(newBlockHash));
  }

  public synchronized CompletableFuture<Void> syncBackwardsUntil(final Block newPivot) {
    return syncBackwardsUntil(newPivot::getHash, () -> backwardChain.appendTrustedBlock(newPivot));
  }

  private CompletableFuture<Void> syncBackwardsUntil(
      final Supplier<Hash> hashProvider, final Runnable saveAction) {
    Optional<CompletableFuture<Void>> maybeFuture =
        Optional.ofNullable(this.currentBackwardSyncStatus.get())
            .map(status -> status.currentFuture);
    if (isTrusted(hashProvider.get())) {
      return maybeFuture.orElseGet(() -> CompletableFuture.completedFuture(null));
    }
    saveAction.run();
    return maybeFuture.orElseGet(
        () -> {
          Status status = new Status(prepareBackwardSyncFutureWithRetry());
          this.currentBackwardSyncStatus.set(status);
          return status.currentFuture;
        });
  }

  private boolean isTrusted(final Hash hash) {
    if (backwardChain.isTrusted(hash)) {
      debugLambda(
          LOG,
          "not fetching or appending hash {} to backwards sync since it is present in successors",
          hash::toHexString);
      return true;
    }
    return false;
  }

  private CompletableFuture<Void> prepareBackwardSyncFutureWithRetry() {
    return prepareBackwardSyncFutureWithRetry(maxRetries)
        .handle(
            (unused, throwable) -> {
              this.currentBackwardSyncStatus.set(null);
              if (throwable != null) {
                throw extractBackwardSyncException(throwable)
                    .orElse(new BackwardSyncException(throwable));
              }
              return null;
            });
  }

  private CompletableFuture<Void> prepareBackwardSyncFutureWithRetry(final int retries) {
    if (retries == 0) {
      return CompletableFuture.failedFuture(
          new BackwardSyncException("Max number of retries " + maxRetries + " reached"));
    }

    return exceptionallyCompose(
        prepareBackwardSyncFuture(),
        throwable -> {
          processException(throwable);
          return ethContext
              .getScheduler()
              .scheduleFutureTask(
                  () -> prepareBackwardSyncFutureWithRetry(retries - 1),
                  Duration.ofMillis(millisBetweenRetries));
        });
  }

  @VisibleForTesting
  protected void processException(final Throwable throwable) {
    extractBackwardSyncException(throwable)
        .ifPresentOrElse(
            backwardSyncException -> {
              if (backwardSyncException.shouldRestart()) {
                LOG.debug(
                    "Backward sync failed ({}). Current Peers: {}. Retrying in {} milliseconds",
                    throwable.getMessage(),
                    ethContext.getEthPeers().peerCount(),
                    millisBetweenRetries);
              } else {
                debugLambda(
                    LOG, "Not recoverable backward sync exception {}", throwable::getMessage);
                throw backwardSyncException;
              }
            },
            () -> {
              LOG.debug(
                  "Backward sync failed ({}). Current Peers: {}. Retrying in {} milliseconds",
                  throwable.getMessage(),
                  ethContext.getEthPeers().peerCount(),
                  millisBetweenRetries);
              LOG.debug("Exception details:", throwable);
            });
  }

  private Optional<BackwardSyncException> extractBackwardSyncException(final Throwable throwable) {
    Throwable currentCause = throwable;

    while (currentCause != null) {
      if (currentCause instanceof BackwardSyncException) {
        return Optional.of((BackwardSyncException) currentCause);
      }
      currentCause = currentCause.getCause();
    }
    return Optional.empty();
  }

  @VisibleForTesting
  CompletableFuture<Void> prepareBackwardSyncFuture() {
    final MutableBlockchain blockchain = getProtocolContext().getBlockchain();
    return new BackwardsSyncAlgorithm(
            this,
            FinalBlockConfirmation.confirmationChain(
                FinalBlockConfirmation.genesisConfirmation(blockchain),
                FinalBlockConfirmation.ancestorConfirmation(blockchain)))
        .executeBackwardsSync(null);
  }

  public ProtocolSchedule getProtocolSchedule() {
    return protocolSchedule;
  }

  public EthContext getEthContext() {
    return ethContext;
  }

  public MetricsSystem getMetricsSystem() {
    return metricsSystem;
  }

  public ProtocolContext getProtocolContext() {
    return protocolContext;
  }

  public BlockValidator getBlockValidator(final long blockNumber) {
    return protocolSchedule.getByBlockNumber(blockNumber).getBlockValidator();
  }

  public BlockValidator getBlockValidatorForBlock(final Block block) {
    return getBlockValidator(block.getHeader().getNumber());
  }

  public boolean isReady() {
    LOG.debug(
        "checking if BWS is ready: ttd reached {}, initial sync done {}",
        syncState.hasReachedTerminalDifficulty().orElse(Boolean.FALSE),
        syncState.isInitialSyncPhaseDone());
    return syncState.hasReachedTerminalDifficulty().orElse(Boolean.FALSE)
        && syncState.isInitialSyncPhaseDone();
  }

  public void subscribeBadChainListener(final BadChainListener badChainListener) {
    badChainListeners.subscribe(badChainListener);
  }

  // In rare case when we request too many headers/blocks we get response that does not contain all
  // data and we might want to retry with smaller batch size
  public int getBatchSize() {
    return batchSize;
  }

  public void halveBatchSize() {
    this.batchSize = batchSize / 2 + 1;
  }

  public void resetBatchSize() {
    this.batchSize = BATCH_SIZE;
  }

  protected Void saveBlock(final Block block) {
    traceLambda(LOG, "Going to validate block {}", block::toLogString);
    var optResult =
        this.getBlockValidatorForBlock(block)
            .validateAndProcessBlock(
                this.getProtocolContext(),
                block,
                HeaderValidationMode.FULL,
                HeaderValidationMode.NONE);
    if (optResult.getYield().isPresent()) {
      traceLambda(LOG, "Block {} was validated, going to import it", block::toLogString);
      optResult.getYield().get().getWorldState().persist(block.getHeader());
      this.getProtocolContext()
          .getBlockchain()
          .appendBlock(block, optResult.getYield().get().getReceipts());
    } else {
      emitBadChainEvent(block);
      throw new BackwardSyncException(
          "Cannot save block "
              + block.toLogString()
              + " because of "
              + optResult.errorMessage.orElseThrow());
    }

    return null;
  }

  public SyncState getSyncState() {
    return syncState;
  }

  public synchronized BackwardChain getBackwardChain() {
    return backwardChain;
  }

  public Status getStatus() {
    return currentBackwardSyncStatus.get();
  }

  private void emitBadChainEvent(final Block badBlock) {
    final List<Block> badBlockDescendants = new ArrayList<>();
    final List<BlockHeader> badBlockHeaderDescendants = new ArrayList<>();

    Optional<Hash> descendant = backwardChain.getDescendant(badBlock.getHash());

    while (descendant.isPresent()) {
      final Optional<Block> block = backwardChain.getBlock(descendant.get());
      if (block.isPresent()) {
        badBlockDescendants.add(block.get());
      } else {
        backwardChain.getHeader(descendant.get()).ifPresent(badBlockHeaderDescendants::add);
      }

      descendant = backwardChain.getDescendant(descendant.get());
    }

    badChainListeners.forEach(
        listener -> listener.onBadChain(badBlock, badBlockDescendants, badBlockHeaderDescendants));
  }

  static class Status {
    private final CompletableFuture<Void> currentFuture;
    private long targetChainHeight;
    private long initialChainHeight;

    public Status(final CompletableFuture<Void> currentFuture) {
      this.currentFuture = currentFuture;
    }

    public void setSyncRange(final long initialHeight, final long targetHeight) {
      initialChainHeight = initialHeight;
      targetChainHeight = targetHeight;
    }

    public long getTargetChainHeight() {
      return targetChainHeight;
    }

    public long getInitialChainHeight() {
      return initialChainHeight;
    }

    public long getBlockCount() {
      return targetChainHeight - initialChainHeight;
    }
  }
}
