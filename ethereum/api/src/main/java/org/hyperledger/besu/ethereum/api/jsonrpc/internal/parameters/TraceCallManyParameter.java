package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.ethereum.transaction.CallParameter;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

public class TraceCallManyParameter {
  List<TraceCallParamterTuple> list;

  @JsonCreator
  public TraceCallManyParameter(final List<TraceCallParamterTuple> traceCallParamterList) {
    this.list = traceCallParamterList;
  }

  public List<TraceCallParamterTuple> getList() {
    return list;
  }

  //    public CallParameter getJsonCallParameter() {
  //        return jsonCallParameter;
  //    }

  //    public TraceTypeParameter getTraceTypeParameter() {
  //        return traceTypeParameter;
  //    }

  //    public static List<TraceCallManyParameter> fromStringList(final List<String> input) {
  //        final ArrayList<TraceCallManyParameter> parameterList = new ArrayList<>();
  //        input.forEach(p -> parameterList.add(parseParameter((Map)p.get(0),
  // (List<String>)p.get(1))));
  //        return parameterList;
  //    }

  //    private static TraceCallManyParameters parseParameter(final Map callParamMap, final
  // List<String>traceTypeList) {
  //        final TraceTypeParameter traceTypeParameter = new TraceTypeParameter(traceTypeList);
  //        final CallParameter callParameter = new CallParameter(
  //                Address.fromHexString((String) callParamMap.get("from")),
  //                Address.fromHexString((String) callParamMap.get("to")),
  //                Long.decode((String) callParamMap.get("gasLimit")),
  //                Wei.fromHexString((String)callParamMap.get("gasPrice")),
  //
  // Optional.ofNullable(Wei.fromHexString((String)callParamMap.get("maxPriorityFeePerGas"))),
  //
  // Optional.ofNullable(Wei.fromHexString((String)callParamMap.get("maxFeePerGas"))),
  //                Wei.fromHexString((String)callParamMap.get("value")),
  //                Bytes.fromHexString((String)callParamMap.get("payload")));
  //        return new TraceCallManyParameters(callParameter, traceTypeParameter);
  //    }
}

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder({"jsonCallParameter", "traceTypeParameter"})
class TraceCallParamterTuple {
  private final CallParameter jsonCallParameter;
  private final TraceTypeParameter traceTypeParameter;

  @JsonCreator
  public TraceCallParamterTuple(
      final CallParameter callParameter, final TraceTypeParameter traceTypeParameter) {
    this.jsonCallParameter = callParameter;
    this.traceTypeParameter = traceTypeParameter;
  }

  public CallParameter getJsonCallParameter() {
    return jsonCallParameter;
  }

  public TraceTypeParameter getTraceTypeParameter() {
    return traceTypeParameter;
  }
}
