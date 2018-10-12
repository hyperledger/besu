package tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor;

import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionProcessor.Result;
import tech.pegasys.pantheon.ethereum.vm.BlockHashLookup;
import tech.pegasys.pantheon.ethereum.vm.DebugOperationTracer;

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
                  tracer,
                  new BlockHashLookup(header, blockchain));
          return new TransactionTrace(transaction, result, tracer.getTraceFrames());
        });
  }
}
