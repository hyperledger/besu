package tech.pegasys.pantheon.ethereum.jsonrpc.internal.processor;

import tech.pegasys.pantheon.ethereum.debug.TraceOptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransactionTraceParams {

  private final boolean disableStorage;
  private final boolean disableMemory;
  private final boolean disableStack;

  @JsonCreator()
  public TransactionTraceParams(
      @JsonProperty("disableStorage") final boolean disableStorage,
      @JsonProperty("disableMemory") final boolean disableMemory,
      @JsonProperty("disableStack") final boolean disableStack) {
    this.disableStorage = disableStorage;
    this.disableMemory = disableMemory;
    this.disableStack = disableStack;
  }

  public TraceOptions traceOptions() {
    return new TraceOptions(!disableStorage, !disableMemory, !disableStack);
  }
}
