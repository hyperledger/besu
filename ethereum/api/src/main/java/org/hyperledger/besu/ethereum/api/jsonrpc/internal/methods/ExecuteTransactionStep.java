package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

import java.util.List;
import java.util.function.Function;

public class ExecuteTransactionStep implements Function<Transaction, TransactionTrace> {

  private final Block block;
  private final DebugOperationTracer tracer;
  private final MainnetTransactionProcessor transactionProcessor;
  private final Blockchain blockchain;
  private final WorldUpdater chainedUpdater;

  public ExecuteTransactionStep(
      final Block block,
      final MainnetTransactionProcessor transactionProcessor,
      final Blockchain blockchain,
      final WorldUpdater chainedUpdater,
      final DebugOperationTracer tracer) {
    this.block = block;
    this.transactionProcessor = transactionProcessor;
    this.blockchain = blockchain;
    this.chainedUpdater = chainedUpdater;
    this.tracer = tracer;
  }

  @Override
  public TransactionTrace apply(final Transaction transaction) {
    BlockHeader header = block.getHeader();
    /*
    if (block.getHeader() == null) {
        return Optional.empty();
    }
    if (block.getBody() == null) {
        return Optional.empty();
    }
    */
    final TransactionProcessingResult result =
        transactionProcessor.processTransaction(
            blockchain,
            chainedUpdater,
            header,
            transaction,
            header.getCoinbase(),
            tracer,
            new BlockHashLookup(header, blockchain),
            false);

    final List<TraceFrame> traceFrames = tracer.copyTraceFrames();
    tracer.reset();
    return new TransactionTrace(transaction, result, traceFrames);
  }
}
