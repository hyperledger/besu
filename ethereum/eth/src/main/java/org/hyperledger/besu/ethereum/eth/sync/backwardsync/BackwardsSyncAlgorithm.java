/*
 *
 *  * Copyright Hyperledger Besu Contributors.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  * the License. You may obtain a copy of the License at
 *  *
 *  * http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  * specific language governing permissions and limitations under the License.
 *  *
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.hyperledger.besu.ethereum.eth.sync.backwardsync;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.infoLambda;
import static org.slf4j.LoggerFactory.getLogger;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;

public class BackwardsSyncAlgorithm {
  private static final Logger LOG = getLogger(BackwardsSyncAlgorithm.class);

  private final BackwardSyncContext context;
  private volatile boolean finished = false;

  public BackwardsSyncAlgorithm(final BackwardSyncContext context) {
    this.context = context;
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
      return executeSyncStep(firstHash.get());
    }
    if (!context.isReady()) {
      return waitForReady();
    }
    runFinalizedSuccessionRule(
        context.getProtocolContext().getBlockchain(), context.findMaybeFinalized());
    final Optional<BlockHeader> firstAncestorHeader =
        context.getBackwardChain().getFirstAncestorHeader();
    if (firstAncestorHeader.isEmpty()) {
      this.finished = true;
      LOG.info("The Backward sync is done...");
      context.getBackwardChain().clear();
      return CompletableFuture.completedFuture(null);
    }
    final MutableBlockchain blockchain = context.getProtocolContext().getBlockchain();
    if (blockchain.contains(firstAncestorHeader.get().getHash())) {
      return executeProcessKnownAncestors();
    }
    if (blockchain.getChainHead().getHeight() > firstAncestorHeader.get().getNumber() - 1) {
      infoLambda(
          LOG,
          "Backward reached bellow previous head {}({}) : {} ({})",
          () -> blockchain.getChainHead().getHeight(),
          () -> blockchain.getChainHead().getHash().toHexString(),
          () -> firstAncestorHeader.get().getNumber(),
          () -> firstAncestorHeader.get().getHash());
    }
    final Optional<Block> finalized = blockchain.getFinalized().flatMap(blockchain::getBlockByHash);
    if (finalized.isPresent()
        && (finalized.get().getHeader().getNumber() >= firstAncestorHeader.get().getNumber())) {
      throw new BackwardSyncException("Cannot continue bellow finalized...");
    }
    if (blockchain.contains(firstAncestorHeader.get().getParentHash())) {
      return executeForwardAsync();
    }
    return executeBackwardAsync(firstAncestorHeader.get());
  }

  @VisibleForTesting
  public CompletableFuture<Void> executeProcessKnownAncestors() {
    return new ProcessKnownAncestorsStep(context, context.getBackwardChain()).executeAsync();
  }

  @VisibleForTesting
  public CompletableFuture<Void> executeSyncStep(final Hash hash) {
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
      context.getSyncState().unsubscribeTTDReached(idTTD);
      context.getSyncState().unsubscribeInitialConditionReached(idIS);
    }
  }

  private void countDownIfReady(final CountDownLatch latch) {
    if (context.isReady()) {
      latch.countDown();
    }
  }

  @VisibleForTesting
  protected void runFinalizedSuccessionRule(
      final MutableBlockchain blockchain, final Optional<Hash> maybeFinalized) {
    if (maybeFinalized.isEmpty()) {
      LOG.debug("Nothing to validate yet, consensus layer did not provide a new finalized block");
      return;
    }
    final Hash newFinalized = maybeFinalized.get();
    if (!blockchain.contains(newFinalized)) {
      LOG.debug("New finalized block {} is not imported yet", newFinalized);
      return;
    }

    final Optional<Hash> maybeOldFinalized = blockchain.getFinalized();
    if (maybeOldFinalized.isPresent()) {
      final Hash oldFinalized = maybeOldFinalized.get();
      if (newFinalized.equals(oldFinalized)) {
        LOG.debug("We already have this block as finalized");
        return;
      }
      BlockHeader newFinalizedHeader =
          blockchain
              .getBlockHeader(newFinalized)
              .orElseThrow(() -> new BackwardSyncException("Inconsistent Blockchain database...."));
      BlockHeader oldFinalizedHeader =
          blockchain
              .getBlockHeader(oldFinalized)
              .orElseThrow(() -> new BackwardSyncException("Inconsistent Blockchain database...."));
      if (newFinalizedHeader.getNumber() < oldFinalizedHeader.getNumber()) {
        throw new BackwardSyncException("Cannot finalize bellow already finalized...");
      }
      LOG.info(
          "Updating finalized {}({}) block to new finalized block {}({})",
          oldFinalizedHeader.getNumber(),
          oldFinalizedHeader.getHash(),
          newFinalizedHeader.getNumber(),
          newFinalizedHeader.getHash());
    } else {
      // Todo: should TTD test be here?
      LOG.info("Setting new finalized block to {}", newFinalized);
    }

    blockchain.setFinalized(newFinalized);
  }
}
