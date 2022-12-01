/*
 *
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
 *
 */
package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;
import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.task.WaitForPeersTask;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;

public class BackwardsSyncAlgorithm {
  private static final Logger LOG = getLogger(BackwardsSyncAlgorithm.class);

  private final BackwardSyncContext context;
  private final FinalBlockConfirmation finalBlockConfirmation;
  private volatile boolean finished = false;

  public BackwardsSyncAlgorithm(
      final BackwardSyncContext context, final FinalBlockConfirmation finalBlockConfirmation) {
    this.context = context;
    this.finalBlockConfirmation = finalBlockConfirmation;
  }

  public CompletableFuture<Void> executeBackwardsSync(final Void unused) {
    final CompletableFuture<Void> nextStep = pickNextStep();
    if (finished) {
      return nextStep;
    }
    return nextStep.thenCompose(this::executeBackwardsSync);
  }

  public CompletableFuture<Void> pickNextStep() {
    final Optional<Hash> firstHash = context.getBackwardChain().getFirstHashToAppend();
    if (firstHash.isPresent()) {
      return executeSyncStep(firstHash.get())
          .thenAccept(
              result -> {
                LOG.info("Backward sync target block is {}", result.toLogString());
                context.getBackwardChain().removeFromHashToAppend(firstHash.get());
                context.getStatus().updateTargetHeight(result.getHeader().getNumber());
              });
    }
    if (!context.isReady()) {
      return waitForReady();
    }
    final Optional<BlockHeader> maybeFirstAncestorHeader =
        context.getBackwardChain().getFirstAncestorHeader();
    if (maybeFirstAncestorHeader.isEmpty()) {
      this.finished = true;
      LOG.info("Current backward sync session is done");
      context.getBackwardChain().clear();
      return CompletableFuture.completedFuture(null);
    }

    final MutableBlockchain blockchain = context.getProtocolContext().getBlockchain();
    final BlockHeader firstAncestorHeader = maybeFirstAncestorHeader.get();
    if (blockchain.contains(firstAncestorHeader.getHash())) {
      return executeProcessKnownAncestors();
    }

    if (blockchain.getChainHead().getHeight() > firstAncestorHeader.getNumber()) {
      debugLambda(
          LOG,
          "Backward reached below current chain head {} : {}",
          () -> blockchain.getChainHead().toLogString(),
          firstAncestorHeader::toLogString);
    }

    if (finalBlockConfirmation.ancestorHeaderReached(firstAncestorHeader)) {
      debugLambda(
          LOG,
          "Backward sync reached ancestor header with {}, starting forward sync",
          firstAncestorHeader::toLogString);
      return executeForwardAsync();
    }

    return executeBackwardAsync(firstAncestorHeader);
  }

  @VisibleForTesting
  public CompletableFuture<Void> executeProcessKnownAncestors() {
    return new ProcessKnownAncestorsStep(context, context.getBackwardChain()).executeAsync();
  }

  @VisibleForTesting
  public CompletableFuture<Block> executeSyncStep(final Hash hash) {
    return new SyncStepStep(context, context.getBackwardChain()).executeAsync(hash);
  }

  @VisibleForTesting
  protected CompletableFuture<Void> executeBackwardAsync(final BlockHeader firstHeader) {
    return new BackwardSyncStep(context, context.getBackwardChain()).executeAsync(firstHeader);
  }

  @VisibleForTesting
  protected CompletableFuture<Void> executeForwardAsync() {
    return new ForwardSyncStep(context, context.getBackwardChain()).executeAsync();
  }

  @VisibleForTesting
  protected CompletableFuture<Void> waitForReady() {
    final CountDownLatch latch = new CountDownLatch(1);
    final long idTTD =
        context.getSyncState().subscribeTTDReached(reached -> countDownIfReady(latch));
    final long idIS =
        context.getSyncState().subscribeCompletionReached(() -> countDownIfReady(latch));
    return CompletableFuture.runAsync(() -> checkReadiness(latch, idTTD, idIS));
  }

  private void checkReadiness(final CountDownLatch latch, final long idTTD, final long idIS) {
    try {
      if (!context.isReady()) {
        LOG.debug("Waiting for preconditions...");
        final boolean await = latch.await(2, TimeUnit.MINUTES);
        if (await) {
          LOG.debug("Preconditions meet, ensure at least one peer is connected");
          waitForPeers(1).get();
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BackwardSyncException(
          "Wait for TTD preconditions interrupted (" + e.getMessage() + ")");
    } catch (ExecutionException e) {
      throw new BackwardSyncException(
          "Error while waiting for at least one connected peer (" + e.getMessage() + ")", true);
    } finally {
      context.getSyncState().unsubscribeTTDReached(idTTD);
      context.getSyncState().unsubscribeInitialConditionReached(idIS);
    }
  }

  private void countDownIfReady(final CountDownLatch latch) {
    if (context.isReady()) {
      latch.countDown();
    }
  }

  private CompletableFuture<Void> waitForPeers(final int count) {
    final WaitForPeersTask waitForPeersTask =
        WaitForPeersTask.create(context.getEthContext(), count, context.getMetricsSystem());
    return waitForPeersTask.run();
  }
}
