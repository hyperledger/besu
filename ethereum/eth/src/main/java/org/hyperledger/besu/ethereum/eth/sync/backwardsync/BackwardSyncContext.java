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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.hyperledger.besu.util.Slf4jLambdaHelper.infoLambda;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackwardSyncContext {
  private static final Logger LOG = LoggerFactory.getLogger(BackwardSyncContext.class);
  public static final int BATCH_SIZE = 200;
  private static final int MAX_RETRIES = 100;

  protected final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final SyncState syncState;

  private final AtomicReference<CompletableFuture<Void>> currentBackwardSyncFuture =
      new AtomicReference<>();
  private final BackwardChain backwardChain;
  private int batchSize = BATCH_SIZE;

  public BackwardSyncContext(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final EthContext ethContext,
      final SyncState syncState,
      final BackwardChain backwardChain) {

    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
    this.syncState = syncState;
    this.backwardChain = backwardChain;
  }

  public boolean isSyncing() {
    return Optional.ofNullable(currentBackwardSyncFuture.get())
        .map(CompletableFuture::isDone)
        .orElse(Boolean.FALSE);
  }

  public CompletableFuture<Void> syncBackwardsUntil(final Hash newBlockHash) {
    final CompletableFuture<Void> future = this.currentBackwardSyncFuture.get();

    synchronized (backwardChain) {
      if (backwardChain.isTrusted(newBlockHash)) {
        debugLambda(
            LOG,
            "not fetching or appending hash {} to backwards sync since it is present in successors",
            newBlockHash::toHexString);
        return future;
      }
      backwardChain.addNewHash(newBlockHash);
    }
    if (future != null) {
      return future;
    }
    infoLambda(LOG, "Starting new backward sync towards a pivot {}", newBlockHash::toHexString);
    this.currentBackwardSyncFuture.set(prepareBackwardSyncFutureWithRetry());
    return this.currentBackwardSyncFuture.get();
  }

  public CompletableFuture<Void> syncBackwardsUntil(final Block newPivot) {
    final CompletableFuture<Void> future = this.currentBackwardSyncFuture.get();
    synchronized (backwardChain) {
      if (backwardChain.isTrusted(newPivot.getHash())) {
        debugLambda(
            LOG,
            "not fetching or appending hash {} to backwards sync since it is present in successors",
            () -> newPivot.getHash().toHexString());
        return future;
      }
      backwardChain.appendTrustedBlock(newPivot);
    }
    if (future != null) {
      return future;
    }
    infoLambda(
        LOG,
        "Starting new backward sync towards a pivot {}({})",
        () -> newPivot.getHeader().getNumber(),
        () -> newPivot.getHash().toHexString());
    this.currentBackwardSyncFuture.set(prepareBackwardSyncFutureWithRetry());
    return this.currentBackwardSyncFuture.get();
  }

  private CompletableFuture<Void> prepareBackwardSyncFutureWithRetry() {

    CompletableFuture<Void> f = prepareBackwardSyncFuture();
    for (int i = 0; i < MAX_RETRIES; i++) {
      f =
          f.thenApply(CompletableFuture::completedFuture)
              .exceptionally(
                  ex -> {
                    if (ex instanceof BackwardSyncException && ex.getCause() == null) {
                      LOG.info(
                          "Backward sync failed ({}). Current Peers: {}. Retrying in few seconds... ",
                          ex.getMessage(),
                          ethContext.getEthPeers().peerCount());
                    } else {
                      LOG.warn("there was an uncaught exception during backward sync", ex);
                    }
                    return ethContext
                        .getScheduler()
                        .scheduleFutureTask(
                            () -> prepareBackwardSyncFuture(), Duration.ofSeconds(5));
                  })
              .thenCompose(Function.identity());
    }
    return f.handle(
        (unused, throwable) -> {
          this.currentBackwardSyncFuture.set(null);
          if (throwable != null) {
            throw new BackwardSyncException(throwable);
          }
          return null;
        });
  }

  private CompletableFuture<Void> prepareBackwardSyncFuture() {
    return executeNextStep(null);
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
    return syncState.hasReachedTerminalDifficulty().orElse(Boolean.FALSE)
        && syncState.isInitialSyncPhaseDone();
  }

  public CompletableFuture<Void> stop() {
    return currentBackwardSyncFuture.get();
  }

  public CompletableFuture<Void> executeNextStep(final Void unused) {
    final Optional<Hash> firstHash = backwardChain.getFirstHash();
    if (firstHash.isPresent()) {
      return executeSyncStep(firstHash.get());
    }
    if (!isReady()) {
      return waitForTTD().thenCompose(this::executeNextStep);
    }

    final Optional<BlockHeader> maybeFirstAncestorHeader = backwardChain.getFirstAncestorHeader();
    if (maybeFirstAncestorHeader.isEmpty()) {
      LOG.info("The Backward sync is done...");
      return CompletableFuture.completedFuture(null);
    }

    final BlockHeader firstAncestorHeader = maybeFirstAncestorHeader.get();

    if (getProtocolContext().getBlockchain().getChainHead().getHeight()
        > firstAncestorHeader.getNumber() - 1) {
      LOG.info(
          "Backward reached below previous head {}({}) : {} ({})",
          getProtocolContext().getBlockchain().getChainHead().getHeight(),
          getProtocolContext().getBlockchain().getChainHead().getHash().toHexString(),
          firstAncestorHeader.getNumber(),
          firstAncestorHeader.getHash());
    }

    if (firstAncestorHeader.getNumber() == 0) {
      final BlockHeader genesisBlockHeader =
          getProtocolContext()
              .getBlockchain()
              .getBlockHeader(0)
              .orElseThrow(
                  () -> new IllegalStateException("Really we do not have the genesis block?"));

      if (genesisBlockHeader.getHash().equals(firstAncestorHeader.getHash())) {
        LOG.info("Backward sync reached genesis, starting Forward sync");
        return executeForwardAsync(genesisBlockHeader);
      } else {
        return CompletableFuture.failedFuture(
            new BackwardSyncException(
                String.format(
                    "Backward sync reached genesis, but ancestor header hash %s does not match our genesis header hash %s",
                    firstAncestorHeader.getHash(), genesisBlockHeader.getHash())));
      }
    }

    if (getProtocolContext().getBlockchain().contains(firstAncestorHeader.getParentHash())) {
      return executeForwardAsync(firstAncestorHeader);
    }
    return executeBackwardAsync(firstAncestorHeader);
  }

  private CompletableFuture<Void> executeSyncStep(final Hash hash) {
    return new SyncStepStep(this, backwardChain).executeAsync(hash);
  }

  @VisibleForTesting
  protected CompletableFuture<Void> executeBackwardAsync(final BlockHeader firstHeader) {
    return new BackwardSyncStep(this, backwardChain).executeAsync(firstHeader);
  }

  @VisibleForTesting
  protected CompletableFuture<Void> executeForwardAsync(final BlockHeader firstHeader) {
    return new ForwardSyncStep(this, backwardChain).executeAsync();
  }

  @VisibleForTesting
  protected CompletableFuture<Void> waitForTTD() {
    final CountDownLatch latch = new CountDownLatch(1);
    final long id =
        syncState.subscribeTTDReached(
            reached -> {
              if (reached && syncState.isInitialSyncPhaseDone()) {
                latch.countDown();
              }
            });
    return CompletableFuture.runAsync(
        () -> {
          try {
            if (!isReady()) {
              LOG.info("Waiting for preconditions...");
              final boolean await = latch.await(2, TimeUnit.MINUTES);
              if (await) {
                LOG.info("Preconditions meet...");
              }
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackwardSyncException("Wait for TTD preconditions interrupted");
          } finally {
            syncState.unsubscribeTTDReached(id);
          }
        });
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
}
