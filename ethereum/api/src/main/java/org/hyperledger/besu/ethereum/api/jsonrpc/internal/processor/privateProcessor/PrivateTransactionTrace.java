package org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.privateProcessor;

import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.privacy.ExecutedPrivateTransaction;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;

import java.util.List;
import java.util.Optional;

public class PrivateTransactionTrace {

  private final ExecutedPrivateTransaction privateTransaction;
  private final TransactionProcessingResult result;
  private final List<TraceFrame> traceFrames;
  private final Optional<Block> block;

  public PrivateTransactionTrace(final Optional<Block> block) {
    this.privateTransaction = null;
    this.result = null;
    this.traceFrames = null;
    this.block = block;
  }

  public PrivateTransactionTrace(
      final ExecutedPrivateTransaction privateTransaction,
      final TransactionProcessingResult result,
      final List<TraceFrame> traceFrames) {
    this.privateTransaction = privateTransaction;
    this.result = result;
    this.traceFrames = traceFrames;
    this.block = Optional.empty();
  }

  public PrivateTransactionTrace(
      final ExecutedPrivateTransaction privateTransaction,
      final TransactionProcessingResult result,
      final List<TraceFrame> traceFrames,
      final Optional<Block> block) {
    this.privateTransaction = privateTransaction;
    this.result = result;
    this.traceFrames = traceFrames;
    this.block = block;
  }

  public PrivateTransactionTrace(
      final ExecutedPrivateTransaction privateTransaction, final Optional<Block> block) {
    this.privateTransaction = privateTransaction;
    this.result = null;
    this.traceFrames = null;
    this.block = block;
  }

  public ExecutedPrivateTransaction getPrivateTransaction() {
    return privateTransaction;
  }

  public long getGas() {
    return privateTransaction.getGasLimit() - result.getGasRemaining();
  }

  public long getGasLimit() {
    return privateTransaction.getGasLimit();
  }

  public TransactionProcessingResult getResult() {
    return result;
  }

  public List<TraceFrame> getTraceFrames() {
    return traceFrames;
  }

  public Optional<Block> getBlock() {
    return block;
  }
}
