package org.hyperledger.besu.consensus.qbt.support.provider;

import org.hyperledger.besu.crypto.SECP256K1;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class SignatureSerializer extends JsonSerializer<SECP256K1.Signature> {
  @Override
  public void serialize(
      final SECP256K1.Signature value,
      final JsonGenerator gen,
      final SerializerProvider serializers)
      throws IOException {
    gen.writeString(value.encodedBytes().toHexString().toLowerCase());
  }
}
