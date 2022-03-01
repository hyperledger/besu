package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonCreator;
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

  public TraceCallParamterTuple getTuple() {
    return this.params;
  }
}

class TraceCallParameterDeserialiser extends StdDeserializer<TraceCallParamterTuple> {

  public TraceCallParameterDeserialiser(final Class<?> vc) {
    super(vc);
  }

  public TraceCallParameterDeserialiser() {
    this(null);
  }

  @Override
  public TraceCallParamterTuple deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode tupleNode = p.getCodec().readTree(p);
    return new TraceCallParamterTuple(
        mapper.readValue(tupleNode.get(0).toString(), JsonCallParameter.class),
        mapper.readValue(tupleNode.get(1).toString(), TraceTypeParameter.class));
  }
}
