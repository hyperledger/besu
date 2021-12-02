package org.hyperledger.besu.consensus.merge.blockcreation.backward.sync;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.manager.task.AbstractPeerTask;
import org.hyperledger.besu.ethereum.eth.manager.task.GetBlockFromPeerTask;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class ForwardSyncStep {
  private final BackwardsSyncContext context;
  private final Block pivot;

  public ForwardSyncStep(final BackwardsSyncContext context, final Block pivot) {
    this.context = context;
    this.pivot = pivot;
  }

  public CompletableFuture<Void> executeAsync(final Void unused) {
    CompletableFuture<Void> result = new CompletableFuture<>();
    if (context.getCurrentPivot().isPresent()) {
      if (!pivot.equals(context.getCurrentPivot().get())) {
        result.completeExceptionally(
            new RuntimeException(
                "The pivot changed, we should stop current flow, some new flow is ongoing..."));
        return result;
      } else {
        return executeForwardStep(result);
      }
    } else {
      result.completeExceptionally(new RuntimeException("No pivot... that is weird"));
      return result;
    }
  }

  private CompletableFuture<Void> executeForwardStep(final CompletableFuture<Void> future) {
    return future
        .thenApply(this::dropKnown) // todo: this will throw exception when we are completely synced
        .thenApply(this::firstHeader)
        .thenCompose(this::requestBlock)
        .thenApply(this::saveBlock)
        .thenCompose(this::possiblyMoreForwardSteps);
  }

  private CompletableFuture<Void> possiblyMoreForwardSteps(final Block block) {
    CompletableFuture<Void> completableFuture = new CompletableFuture<>();
    if (block.equals(pivot)) {
      completableFuture.complete(null); // we finished backward sync
      return completableFuture;
    }
    // we need to do another step of forward sync
    return completableFuture.thenCompose(this::executeAsync);
  }

  private Block saveBlock(final Block block) {
    var optResult =
        context
            .getBlockValidator()
            .validateAndProcessBlock(
                context.getProtocolContext(),
                block,
                HeaderValidationMode.FULL,
                HeaderValidationMode.NONE);

    optResult.ifPresent(
        result -> {
          result.worldState.persist(block.getHeader());
          context.getProtocolContext().getBlockchain().appendBlock(block, result.receipts);
        });
    return block;
  }

  private Void dropKnown(final Void unused) {
    BackwardChain backwardChain = context.getBackwardChainMap().get(pivot.getHeader().getNumber());
    for (BlockHeader firstHeader = backwardChain.getFirstHeader();
        context.getProtocolContext().getBlockchain().contains(firstHeader.getHash());
        firstHeader = backwardChain.getFirstHeader()) {
      backwardChain.dropFirstHeader();
    }
    return null;
  }

  private CompletableFuture<Block> requestBlock(final BlockHeader blockHeader) {
    return GetBlockFromPeerTask.create(
            context.getProtocolSchedule(),
            context.getEthContext(),
            Optional.of(blockHeader.getHash()),
            blockHeader.getNumber(),
            context.getMetricsSystem())
        .run()
        .thenApply(AbstractPeerTask.PeerTaskResult::getResult);
  }

  private BlockHeader firstHeader(final Void unused) {
    BackwardChain backwardChain = context.getBackwardChainMap().get(pivot.getHeader().getNumber());
    return backwardChain.getFirstHeader();
  }
}
