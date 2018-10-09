package net.consensys.pantheon.ethereum.jsonrpc.internal.processor;

import net.consensys.pantheon.ethereum.core.Hash;
import net.consensys.pantheon.ethereum.mainnet.TransactionProcessor.Result;
import net.consensys.pantheon.ethereum.vm.DebugOperationTracer;

import java.util.Optional;

/** Used to produce debug traces of transactions */
public class TransactionTracer {

  private final BlockReplay blockReplay;

  public TransactionTracer(final BlockReplay blockReplay) {
    this.blockReplay = blockReplay;
  }

  public Optional<TransactionTrace> traceTransaction(
      final Hash blockHash, final Hash transactionHash, final DebugOperationTracer tracer) {
    return blockReplay.beforeTransactionInBlock(
        blockHash,
        transactionHash,
        (transaction, header, blockchain, mutableWorldState, transactionProcessor) -> {
          final Result result =
              transactionProcessor.processTransaction(
                  blockchain,
                  mutableWorldState.updater(),
                  header,
                  transaction,
                  header.getCoinbase(),
                  tracer);
          return new TransactionTrace(transaction, result, tracer.getTraceFrames());
        });
  }
}
