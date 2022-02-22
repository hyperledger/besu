package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class TraceCallManyParameter {
  TraceCallParamterTuple params;

  @JsonCreator
  public TraceCallManyParameter(
      @JsonDeserialize(using = TraceCallParameterDeserialiser.class)
          final TraceCallParamterTuple parameters) {
    this.params = parameters;
  }

  public TraceCallParamterTuple getParams() {
    return this.params;
  }
}

class TraceCallParameterDeserialiser extends StdDeserializer<TraceCallParamterTuple> {
  public TraceCallParameterDeserialiser() {
    this(null);
  }

  public TraceCallParameterDeserialiser(final Class<?> vc) {
    super(vc);
  }

  @Override
  public TraceCallParamterTuple deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException, JacksonException {
    @SuppressWarnings("unused")
    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode tupleNode = p.getCodec().readTree(p);
    return new TraceCallParamterTuple(
        mapper.readValue(tupleNode.get(0).toString(), JsonCallParameter.class),
        mapper.readValue(tupleNode.get(1).toString(), TraceTypeParameter.class));
  }
}

class TraceCallParamterTuple {
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
