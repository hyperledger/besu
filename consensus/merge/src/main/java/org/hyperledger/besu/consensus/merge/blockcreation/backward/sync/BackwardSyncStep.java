package org.hyperledger.besu.consensus.merge.blockcreation.backward.sync;

import static org.apache.logging.log4j.LogManager.getLogger;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.task.GetHeadersFromPeerByHashTask;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.Logger;

public class BackwardSyncStep {
  private static final Logger LOG = getLogger();

  private final BackwardsSyncContext context;
  private final Block pivot;

  public BackwardSyncStep(final BackwardsSyncContext context, final Block pivot) {
    this.context = context;
    this.pivot = pivot;
  }

  CompletableFuture<Void> executeAsync(final Void unused) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    if (context.getCurrentPivot().isPresent()) {
      if (!pivot.equals(context.getCurrentPivot().get())) {
        result.completeExceptionally(
            new RuntimeException(
                "The pivot changed, we probably did not sync fast enough... Stopping the current run. There should be another run happening in parallel already"));
        return result;
      } else {
        return executeBackwardStep(result);
      }
    } else {
      result.completeExceptionally(
          new RuntimeException(
              "No pivot... that is weird and should not have happened. This method should have been called after the pivot was set..."));
      return result;
    }
  }

  private CompletableFuture<Void> executeBackwardStep(final CompletableFuture<Void> future) {
    return future
        .thenApply(this::earliestUnprocessedHash)
        .thenCompose(this::requestHeader)
        .thenApply(this::saveHeader)
        .thenApply(this::possibleMerge)
        .thenCompose(this::possiblyMoreBackwardSteps);
  }

  private Hash earliestUnprocessedHash(final Void unused) {
    BackwardChain backwardChain = context.getBackwardChainMap().get(pivot.getHeader().getNumber());
    Hash parentHash = backwardChain.getFirstHeader().getParentHash();
    LOG.debug(
        "First unprocessed hash for current pivot is {} expected on height {}",
        () -> parentHash.toString().substring(0, 20),
        () -> backwardChain.getFirstHeader().getNumber() - 1);
    return parentHash;
  }

  private CompletableFuture<BlockHeader> requestHeader(final Hash hash) {
    LOG.debug("Requesting header for hash {}", () -> hash.toString().substring(0, 20));
    return GetHeadersFromPeerByHashTask.forSingleHash(
            context.getProtocolSchedule(),
            context.getEthContext(),
            pivot.getHeader().getHash(),
            pivot.getHeader().getNumber(),
            context.getMetricsSystem())
        .run()
        .thenApply(
            peerResult -> {
              BlockHeader blockHeader = peerResult.getResult().get(0);
              LOG.debug(
                  "Got header {} with height {}",
                  () -> blockHeader.getHash().toString().substring(0, 20),
                  blockHeader::getNumber);
              return blockHeader;
            });
  }

  private BlockHeader saveHeader(final BlockHeader blockHeader) {
    BackwardChain backwardChain = context.getBackwardChainMap().get(pivot.getHeader().getNumber());
    backwardChain.saveHeader(blockHeader);
    return blockHeader;
  }

  private BlockHeader possibleMerge(final BlockHeader blockHeader) {
    BackwardChain backwardChain = context.getBackwardChainMap().get(pivot.getHeader().getNumber());
    Optional<BackwardChain> maybeHistoricalBackwardChain =
        Optional.of(context.getBackwardChainMap().get(backwardChain.getFirstHeader().getNumber()));
    maybeHistoricalBackwardChain.ifPresent(backwardChain::merge);
    return backwardChain.getFirstHeader();
  }

  // if the previous header is not present yet, we need to go deeper
  private CompletableFuture<Void> possiblyMoreBackwardSteps(final BlockHeader blockHeader) {
    CompletableFuture<Void> completableFuture = new CompletableFuture<>();
    if (context.getProtocolContext().getBlockchain().contains(blockHeader.getHash())) {
      LOG.debug(
          "The backward sync let us to a block that we already know... We will init forward sync...");
      completableFuture.complete(null); // we finished backward sync
      return completableFuture;
    }
    if (context.getProtocolContext().getBlockchain().getChainHead().getHeight()
        >= blockHeader.getNumber() - 1) {
      completableFuture.completeExceptionally(
          new RuntimeException("Backward sync would reach under know head of blockchain"));
      return completableFuture;
    }
    LOG.debug("Backward sync did not reach a know block, need to go deeper");
    return completableFuture.thenCompose(this::executeAsync);
  }
}
