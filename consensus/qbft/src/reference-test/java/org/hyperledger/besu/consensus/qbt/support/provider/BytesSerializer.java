package org.hyperledger.besu.consensus.qbt.support.provider;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.apache.tuweni.bytes.Bytes;

public class BytesSerializer extends JsonSerializer<Bytes> {
  @Override
  public void serialize(Bytes value, JsonGenerator gen, SerializerProvider provider)
      throws IOException {
    gen.writeString(value.toHexString().toLowerCase());
  }
}
