package org.hyperledger.besu.consensus.qbt.support.provider;

import org.hyperledger.besu.crypto.SECP256K1;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.apache.tuweni.bytes.Bytes;

public class SignatureDeserializer extends JsonDeserializer<SECP256K1.Signature> {
  @Override
  public SECP256K1.Signature deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException, JsonProcessingException {
    return SECP256K1.Signature.decode(Bytes.fromHexString(p.getValueAsString()));
  }
}
