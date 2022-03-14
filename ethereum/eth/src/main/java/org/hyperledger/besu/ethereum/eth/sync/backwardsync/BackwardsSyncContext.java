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

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.sync.state.SyncState;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackwardsSyncContext {
  private static final Logger LOG = LoggerFactory.getLogger(BackwardsSyncContext.class);
  public static final int BATCH_SIZE = 200;

  private final ProtocolContext protocolContext;
  private final ProtocolSchedule protocolSchedule;
  private final EthContext ethContext;
  private final MetricsSystem metricsSystem;
  private final SyncState syncState;

  private final Map<Long, BackwardSyncStorage> backwardChainMap = new ConcurrentHashMap<>();
  private final AtomicReference<BackwardSyncStorage> currentChain = new AtomicReference<>();
  private final AtomicReference<CompletableFuture<Void>> currentBackwardSyncFuture =
      new AtomicReference<>();
  private final BackwardSyncLookupService service;
  //  private static final int MAX_RETRIES = 10;

  public BackwardsSyncContext(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final EthContext ethContext,
      final SyncState syncState,
      final BackwardSyncLookupService backwardSyncLookupService) {

    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
    this.syncState = syncState;
    this.service = backwardSyncLookupService;
  }

  public boolean isSyncing() {
    return Optional.ofNullable(currentBackwardSyncFuture.get())
        .map(CompletableFuture::isDone)
        .orElse(Boolean.FALSE);
  }

  public CompletableFuture<Void> syncBackwardsUntil(final Hash newBlockhash) {
    final Optional<BackwardSyncStorage> chain = getCurrentChain();
    if (chain.isPresent() && chain.get().isTrusted(newBlockhash)) {
      debugLambda(
          LOG,
          "not fetching and appending hash {} to backwards sync since it is present in successors",
          newBlockhash::toHexString);
      return CompletableFuture.completedFuture(null);
    }

    // kick off async process to fetch this block by hash then delegate to syncBackwardsUntil
    final CompletableFuture<Void> completableFuture =
        service
            .lookup(newBlockhash)
            .thenCompose(
                blocks -> {
                  if (blocks.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                  } else return this.syncBackwardsUntil(blocks);
                });
    this.currentBackwardSyncFuture.set(completableFuture);
    return completableFuture;
  }

  private CompletionStage<Void> syncBackwardsUntil(final List<Block> blocks) {
    CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
    for (Block block : blocks) {
      future = future.thenCompose(unused -> syncBackwardsUntil(block));
    }
    return future;
  }

  public CompletableFuture<Void> syncBackwardsUntil(final Block newPivot) {
    final BackwardSyncStorage backwardChain = currentChain.get();
    if (backwardChain == null) {
      debugLambda(
          LOG,
          "Starting new backward sync towards a pivot {} at height {}",
          () -> newPivot.getHash().toString().substring(0, 20),
          () -> newPivot.getHeader().getNumber());
      final BackwardSyncStorage newChain = new InMemoryBackwardChain(newPivot);
      this.currentChain.set(newChain);
      backwardChainMap.put(newPivot.getHeader().getNumber(), newChain);
      currentBackwardSyncFuture.set(prepareBackwardSyncFutureWithRetry(newChain));
      return currentBackwardSyncFuture.get();
    }
    if (newPivot.getHeader().getParentHash().equals(currentChain.get().getPivot().getHash())) {
      LOG.debug(
          "Backward sync is ongoing. Appending expected next block to the end of backward sync chain");
      backwardChain.appendExpectedBlock(newPivot);
      backwardChainMap.put(newPivot.getHeader().getNumber(), backwardChain);
      return currentBackwardSyncFuture.get();
    }
    debugLambda(
        LOG,
        "Stopping existing backward sync from pivot {} at height {} and restarting with pivot {} at height {}",
        () -> backwardChain.getPivot().getHash().toString().substring(0, 20),
        () -> backwardChain.getPivot().getHeader().getNumber(),
        () -> newPivot.getHash().toString().substring(0, 20),
        () -> newPivot.getHeader().getNumber());

    BackwardSyncStorage newBackwardChain = new InMemoryBackwardChain(newPivot);
    backwardChainMap.put(newPivot.getHeader().getNumber(), newBackwardChain);
    this.currentChain.set(
        newBackwardChain); // the current ongoing backward sync will finish its current step and end

    currentBackwardSyncFuture.set(
        currentBackwardSyncFuture
            .get()
            .handle(
                (unused, error) -> {
                  if (error != null) {
                    if ((error.getCause() != null)
                        && (error.getCause() instanceof BackwardSyncException)) {
                      LOG.debug(
                          "Previous Backward sync ended exceptionally with message {}",
                          error.getMessage());
                    } else {
                      LOG.debug(
                          "Previous Backward sync ended exceptionally with message {}",
                          error.getMessage());
                      if (error instanceof RuntimeException) {
                        throw (RuntimeException) error;
                      } else {
                        throw new BackwardSyncException(error);
                      }
                    }
                  } else {
                    LOG.debug("The previous backward sync finished without and exception");
                  }

                  return newBackwardChain;
                })
            .thenCompose(this::prepareBackwardSyncFutureWithRetry));
    return currentBackwardSyncFuture.get();
  }

  private CompletableFuture<Void> prepareBackwardSyncFutureWithRetry(
      final BackwardSyncStorage backwardChain) {
    CompletableFuture<Void> f = prepareBackwardSyncFuture(backwardChain);
    //    for (int i = 0; i < MAX_RETRIES; i++) {
    //      f =
    //          f.thenApply(CompletableFuture::completedFuture)
    //              .exceptionally(
    //                  throwable -> {
    //                    if (!(throwable instanceof BackwardSyncException)) {
    //                      LOG.warn(
    //                          "There was an uncaught exception raised during the backward sync,
    // this represent an unexpected scenario. Copy paste the exception into a bug on github",
    //                          throwable);
    //                      throw new BackwardSyncException(throwable, true);
    //                    }
    //                    if (((BackwardSyncException) throwable).shouldRestart()) {
    //                      LOG.warn(
    //                          "A backward sync task failed, restarting... Reason: {}",
    //                          throwable.getMessage());
    //                      return prepareBackwardSyncFuture(backwardChain);
    //                    }
    //                    throw (BackwardSyncException) throwable;
    //                  })
    //              .thenCompose(Function.identity());
    //    }
    return f.thenApply(unused -> cleanup(backwardChain));
  }

  private CompletableFuture<Void> prepareBackwardSyncFuture(
      final BackwardSyncStorage backwardChain) {
    return new BackwardSyncStep(this, backwardChain)
        .executeAsync(null)
        .thenCompose(new ForwardSyncStep(this, backwardChain)::executeAsync);
  }

  private Void cleanup(final BackwardSyncStorage chain) {
    if (currentChain.compareAndSet(chain, null)) {
      this.currentBackwardSyncFuture.set(null);
    }
    return null;
  }

  public Optional<BackwardSyncStorage> getCurrentChain() {
    return Optional.ofNullable(currentChain.get());
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

  public BackwardSyncStorage findCorrectChainFromPivot(final long number) {
    return backwardChainMap.get(number);
  }

  public void putCurrentChainToHeight(final long height, final BackwardSyncStorage backwardChain) {
    backwardChainMap.put(height, backwardChain);
  }

  public boolean isOnTTD() {
    return syncState.hasReachedTerminalDifficulty().orElse(false);
  }
}
