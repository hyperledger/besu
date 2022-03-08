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
import org.hyperledger.besu.ethereum.eth.manager.task.GetBodiesFromPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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

  private final Map<Long, BackwardChain> backwardChainMap = new ConcurrentHashMap<>();
  private final AtomicReference<BackwardChain> currentChain = new AtomicReference<>();
  private final AtomicReference<CompletableFuture<Void>> currentBackwardSyncFuture =
      new AtomicReference<>();

  public BackwardsSyncContext(
      final ProtocolContext protocolContext,
      final ProtocolSchedule protocolSchedule,
      final MetricsSystem metricsSystem,
      final EthContext ethContext) {

    this.protocolContext = protocolContext;
    this.protocolSchedule = protocolSchedule;
    this.ethContext = ethContext;
    this.metricsSystem = metricsSystem;
  }

  public boolean isSyncing() {
    return Optional.ofNullable(currentBackwardSyncFuture.get())
        .map(CompletableFuture::isDone)
        .orElse(Boolean.FALSE);
  }

  public CompletableFuture<Void> syncBackwardsUntil(final Hash newBlockhash) {
    if (getCurrentChain()
        .flatMap(
            chain ->
                chain.getSuccessors().stream()
                    .map(Block::getHash)
                    .filter(hash -> hash.equals(newBlockhash))
                    .findAny())
        .isPresent()) {
      debugLambda(
          LOG,
          "not fetching and appending hash {} to backwards sync since it is present in successors",
          () -> newBlockhash.toHexString());
      return CompletableFuture.completedFuture(null);
    }

    // kick off async process to fetch this block by hash then delegate to syncBackwardsUntil
    return GetHeadersFromPeerByHashTask.forSingleHash(
            protocolSchedule, ethContext, newBlockhash, 0L, metricsSystem)
        .run()
        .thenCompose(
            headers ->
                GetBodiesFromPeerTask.forHeaders(
                        protocolSchedule, ethContext, headers.getResult(), metricsSystem)
                    .run()
                    .thenCompose(blocks -> syncBackwardsUntil(blocks.getResult().get(0))))
        .exceptionally(
            ex -> {
              LOG.error("Failed to fetch block by hash " + newBlockhash.toHexString(), ex);
              throw new BackwardSyncException(ex);
            });
  }

  public CompletableFuture<Void> syncBackwardsUntil(final Block newPivot) {
    final BackwardChain backwardChain = currentChain.get();
    if (backwardChain == null) {
      debugLambda(
          LOG,
          "Starting new backward sync towards a pivot {} at height {}",
          () -> newPivot.getHash().toString().substring(0, 20),
          () -> newPivot.getHeader().getNumber());
      final BackwardChain newChain = new BackwardChain(newPivot);
      this.currentChain.set(newChain);
      backwardChainMap.put(newPivot.getHeader().getNumber(), newChain);
      this.currentBackwardSyncFuture.set(prepareBackwardSyncFuture(newChain));
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

    BackwardChain newBackwardChain = new BackwardChain(newPivot);
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
            .thenCompose(this::prepareBackwardSyncFuture));
    return currentBackwardSyncFuture.get();
  }

  private CompletableFuture<Void> prepareBackwardSyncFuture(final BackwardChain backwardChain) {
    return new BackwardSyncStep(this, backwardChain)
        .executeAsync(null)
        .thenCompose(new ForwardSyncStep(this, backwardChain)::executeAsync)
            .exceptionally(throwable -> {
              LOG.warn("A backward sync task failed because of: {}", throwable.getMessage());
              return null;
            });
  }

  public Optional<BackwardChain> getCurrentChain() {
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

  public BackwardChain findCorrectChainFromPivot(final long number) {
    return backwardChainMap.get(number);
  }

  public void putCurrentChainToHeight(final long height, final BackwardChain backwardChain) {
    backwardChainMap.put(height, backwardChain);
  }
}
