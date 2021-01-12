package org.hyperledger.besu.ethereum.core.deserializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class QuantityToByteDeserializer extends JsonDeserializer<Byte> {
  @Override
  public Byte deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    return Byte.parseByte(p.getValueAsString().substring(2), 16);
  }
}
