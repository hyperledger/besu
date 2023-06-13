package org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.privateProcessor;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockReplay;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.BlockTrace;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateBlockMetadata;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.CachingBlockHashLookup;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.evm.worldstate.StackedUpdater;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;
import java.util.Optional;

public class PrivateBlockTracer {
  private final PrivateBlockReplay blockReplay;
  // Either the initial block state or the state of the prior TX, including miner rewards.
  private WorldUpdater chainedUpdater;

  public PrivateBlockTracer(final PrivateBlockReplay blockReplay) {
    this.blockReplay = blockReplay;
  }

  public Optional<BlockTrace> trace(
          final Tracer.TraceableState mutableWorldState,
          final Hash blockHash,
          final DebugOperationTracer tracer) {
    return blockReplay.block(blockHash, prepareReplayAction(mutableWorldState, tracer));
  }

  public Optional<BlockTrace> trace(
      final Tracer.TraceableState mutableWorldState,
      final Block block,
      final DebugOperationTracer tracer,
      final String enclaveKey,
      final PrivateBlockMetadata privateBlockMetadata) {
    return blockReplay.block(block, privateBlockMetadata, enclaveKey, prepareReplayAction(mutableWorldState, tracer));
  }

  private BlockReplay.TransactionAction<TransactionTrace> prepareReplayAction(
      final MutableWorldState mutableWorldState, final DebugOperationTracer tracer) {
    return (transaction, header, blockchain, transactionProcessor, dataGasPrice) -> {
      // if we have no prior updater, it must be the first TX, so use the block's initial state
      if (chainedUpdater == null) {
        chainedUpdater = mutableWorldState.updater();
      } else if (chainedUpdater instanceof StackedUpdater<?, ?> stackedUpdater) {
        stackedUpdater.markTransactionBoundary();
      }
      // create an updater for just this tx
      chainedUpdater = chainedUpdater.updater();
      final TransactionProcessingResult result =
          transactionProcessor.processTransaction(
              blockchain,
              chainedUpdater,
              header,
              transaction,
              header.getCoinbase(),
              tracer,
              new CachingBlockHashLookup(header, blockchain),
              false,
              dataGasPrice);
      final List<TraceFrame> traceFrames = tracer.copyTraceFrames();
      tracer.reset();
      return new TransactionTrace(transaction, result, traceFrames);
    };
  }
}
