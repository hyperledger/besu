package org.hyperledger.besu.consensus.qbt.support.provider;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.apache.tuweni.bytes.Bytes;

public class BytesDeserializer extends JsonDeserializer<Bytes> {

  @Override
  public Bytes deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException, JsonProcessingException {
    return Bytes.fromHexString(p.getValueAsString());
  }
}
