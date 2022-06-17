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

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

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
  private Optional<Hash> maybeFinalized = Optional.empty();
  private Optional<Hash> maybeHead = Optional.empty();

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

  public synchronized boolean isSyncing() {
    return Optional.ofNullable(currentBackwardSyncFuture.get())
        .map(CompletableFuture::isDone)
        .orElse(Boolean.FALSE);
  }

  public synchronized void updateHeads(final Hash head, final Hash finalizedBlockHash) {
    if (Hash.ZERO.equals(finalizedBlockHash)) {
      this.maybeFinalized = Optional.empty();
    } else {
      this.maybeFinalized = Optional.ofNullable(finalizedBlockHash);
    }
    if (Hash.ZERO.equals(head)) {
      this.maybeHead = Optional.empty();
    } else {
      this.maybeHead = Optional.ofNullable(head);
    }
  }

  public synchronized CompletableFuture<Void> syncBackwardsUntil(final Hash newBlockHash) {
    final CompletableFuture<Void> future = this.currentBackwardSyncFuture.get();
    if (isTrusted(newBlockHash)) return future;
    backwardChain.addNewHash(newBlockHash);
    if (future != null) {
      return future;
    }
    infoLambda(LOG, "Starting new backward sync towards a pivot {}", newBlockHash::toHexString);
    this.currentBackwardSyncFuture.set(prepareBackwardSyncFutureWithRetry());
    return this.currentBackwardSyncFuture.get();
  }

  public synchronized CompletableFuture<Void> syncBackwardsUntil(final Block newPivot) {
    final CompletableFuture<Void> future = this.currentBackwardSyncFuture.get();
    if (isTrusted(newPivot.getHash())) return future;
    backwardChain.appendTrustedBlock(newPivot);
    if (future != null) {
      return future;
    }
    infoLambda(LOG, "Starting new backward sync towards a pivot {}", newPivot::toLogString);
    this.currentBackwardSyncFuture.set(prepareBackwardSyncFutureWithRetry());
    return this.currentBackwardSyncFuture.get();
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

    CompletableFuture<Void> f = prepareBackwardSyncFuture();
    for (int i = 0; i < MAX_RETRIES; i++) {
      f =
          f.thenApply(CompletableFuture::completedFuture)
              .exceptionally(
                  ex -> {
                    processException(ex);
                    return ethContext
                        .getScheduler()
                        .scheduleFutureTask(this::prepareBackwardSyncFuture, Duration.ofSeconds(5));
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

  @VisibleForTesting
  protected void processException(final Throwable throwable) {
    Throwable currentCause = throwable;

    while (currentCause != null) {
      if (currentCause instanceof BackwardSyncException) {
        if (((BackwardSyncException) currentCause).shouldRestart()) {
          LOG.info(
              "Backward sync failed ({}). Current Peers: {}. Retrying in few seconds... ",
              currentCause.getMessage(),
              ethContext.getEthPeers().peerCount());
          return;
        } else {
          throw new BackwardSyncException(throwable);
        }
      }
      currentCause = currentCause.getCause();
    }
    LOG.warn(
        "There was an uncaught exception during Backwards Sync... Retrying in few seconds...",
        throwable);
  }

  private CompletableFuture<Void> prepareBackwardSyncFuture() {
    final MutableBlockchain blockchain = getProtocolContext().getBlockchain();
    return new BackwardsSyncAlgorithm(
            this,
            FinalBlockConfirmation.confirmationChain(
                FinalBlockConfirmation.genesisConfirmation(blockchain),
                FinalBlockConfirmation.finalizedConfirmation(blockchain)))
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
    return syncState.hasReachedTerminalDifficulty().orElse(Boolean.FALSE)
        && syncState.isInitialSyncPhaseDone();
  }

  public CompletableFuture<Void> stop() {
    return currentBackwardSyncFuture.get();
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
    checkFinalizedSuccessionRuleBeforeSave(block);
    var optResult =
        this.getBlockValidatorForBlock(block)
            .validateAndProcessBlock(
                this.getProtocolContext(),
                block,
                HeaderValidationMode.FULL,
                HeaderValidationMode.NONE);
    optResult.blockProcessingOutputs.ifPresent(
        result -> {
          traceLambda(LOG, "Block {} was validated, going to import it", block::toLogString);
          result.worldState.persist(block.getHeader());
          this.getProtocolContext().getBlockchain().appendBlock(block, result.receipts);
          possiblyMoveHead(block);
        });
    return null;
  }

  @VisibleForTesting
  protected synchronized void checkFinalizedSuccessionRuleBeforeSave(final Block block) {
    final Optional<Hash> finalized = findMaybeFinalized();
    if (finalized.isPresent()) {
      final Optional<BlockHeader> maybeFinalizedHeader =
          protocolContext
              .getBlockchain()
              .getBlockByHash(finalized.get())
              .map(Block::getHeader)
              .or(() -> backwardChain.getHeader(finalized.get()));
      if (maybeFinalizedHeader.isEmpty()) {
        throw new BackwardSyncException(
            "We know a block "
                + finalized.get().toHexString()
                + " was finalized, but we don't have it downloaded yet, cannot save new block",
            true);
      }
      final BlockHeader finalizedHeader = maybeFinalizedHeader.get();
      if (finalizedHeader.getHash().equals(block.getHash())) {
        debugLambda(LOG, "Saving new finalized block {}", block::toLogString);
        return;
      }

      if (finalizedHeader.getNumber() == block.getHeader().getNumber()) {
        throw new BackwardSyncException(
            "This block is not the target finalized block. Is "
                + block.toLogString()
                + " but was expecting "
                + finalizedHeader.toLogString());
      }
      if (!getProtocolContext().getBlockchain().contains(finalizedHeader.getHash())) {
        debugLambda(
            LOG,
            "Saving block {} before finalized {} reached",
            block::toLogString,
            finalizedHeader::toLogString); // todo: some check here??
        return;
      }
      final Hash canonicalHash =
          getProtocolContext()
              .getBlockchain()
              .getBlockByNumber(finalizedHeader.getNumber())
              .orElseThrow()
              .getHash();
      if (finalizedHeader.getNumber() < block.getHeader().getNumber()
          && !canonicalHash.equals(finalizedHeader.getHash())) {
        throw new BackwardSyncException(
            "Finalized block "
                + finalizedHeader.toLogString()
                + " is not on canonical chain. Canonical is"
                + canonicalHash.toHexString()
                + ". We need to reorg before saving this block.");
      }
    }
    LOG.debug("Finalized block not known yet...");
  }

  @VisibleForTesting
  protected void possiblyMoveHead(final Block lastSavedBlock) {
    final MutableBlockchain blockchain = getProtocolContext().getBlockchain();
    if (maybeHead.isEmpty()) {
      LOG.debug("Nothing to do with the head");
      return;
    }
    if (blockchain.getChainHead().getHash().equals(maybeHead.get())) {
      LOG.debug("Head is already properly set");
      return;
    }
    if (blockchain.contains(maybeHead.get())) {
      LOG.debug("Changing head to {}", maybeHead.get().toHexString());
      blockchain.rewindToBlock(maybeHead.get());
      return;
    }
    if (blockchain.getChainHead().getHash().equals(lastSavedBlock.getHash())) {
      LOG.debug("Rewinding head to lastSavedBlock {}", lastSavedBlock.getHash());
      blockchain.rewindToBlock(lastSavedBlock.getHash());
    }
  }

  public SyncState getSyncState() {
    return syncState;
  }

  public synchronized BackwardChain getBackwardChain() {
    return backwardChain;
  }

  public Optional<Hash> findMaybeFinalized() {
    return Stream.of(maybeFinalized, getProtocolContext().getBlockchain().getFinalized())
        .filter(Optional::isPresent)
        .map(Optional::get)
        .findFirst();
  }
}
