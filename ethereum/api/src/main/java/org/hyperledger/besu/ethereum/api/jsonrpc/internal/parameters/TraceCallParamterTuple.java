package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

public class TraceCallParamterTuple {
  private final JsonCallParameter jsonCallParameter;
  private final TraceTypeParameter traceTypeParameter;

  public TraceCallParamterTuple(
      final JsonCallParameter callParameter, final TraceTypeParameter traceTypeParameter) {
    this.jsonCallParameter = callParameter;
    this.traceTypeParameter = traceTypeParameter;
  }

  public JsonCallParameter getJsonCallParameter() {
    return jsonCallParameter;
  }

  public TraceTypeParameter getTraceTypeParameter() {
    return traceTypeParameter;
  }
}
