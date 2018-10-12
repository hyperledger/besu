package tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor;

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.debug.TraceFrame;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionProcessor.Result;

import java.util.List;

public class TransactionTrace {

  private final Transaction transaction;
  private final Result result;
  private final List<TraceFrame> traceFrames;

  public TransactionTrace(
      final Transaction transaction, final Result result, final List<TraceFrame> traceFrames) {
    this.transaction = transaction;
    this.result = result;
    this.traceFrames = traceFrames;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  public long getGas() {
    return transaction.getGasLimit() - result.getGasRemaining();
  }

  public Result getResult() {
    return result;
  }

  public List<TraceFrame> getTraceFrames() {
    return traceFrames;
  }
}
