package tech.pegasys.pantheon.ethereum.jsonrpc.internal.results;

import tech.pegasys.pantheon.ethereum.debug.TraceFrame;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor.TransactionTrace;

import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"gas", "failed", "returnValue", "structLogs"})
public class DebugTraceTransactionResult {

  private final List<StructLog> structLogs;
  private final String returnValue;
  private final long gas;
  private final boolean failed;

  public DebugTraceTransactionResult(final TransactionTrace transactionTrace) {
    gas = transactionTrace.getGas();
    returnValue = transactionTrace.getResult().getOutput().toString().substring(2);
    structLogs =
        transactionTrace
            .getTraceFrames()
            .stream()
            .map(DebugTraceTransactionResult::createStructLog)
            .collect(Collectors.toList());
    failed = !transactionTrace.getResult().isSuccessful();
  }

  private static StructLog createStructLog(final TraceFrame frame) {
    return frame.getExceptionalHaltReasons().isEmpty()
        ? new StructLog(frame)
        : new StructLogWithError(frame);
  }

  @JsonGetter(value = "structLogs")
  public List<StructLog> getStructLogs() {
    return structLogs;
  }

  @JsonGetter(value = "returnValue")
  public String getReturnValue() {
    return returnValue;
  }

  @JsonGetter(value = "gas")
  public long getGas() {
    return gas;
  }

  @JsonGetter(value = "failed")
  public boolean failed() {
    return failed;
  }
}
