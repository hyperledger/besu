package org.hyperledger.besu.ethereum.core.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

public class QuantityToByteDeserializer extends JsonDeserializer<Byte> {
  @Override
  public Byte deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    return Byte.parseByte(p.getValueAsString().substring(2), 16);
  }
}
