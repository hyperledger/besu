package org.hyperledger.besu.consensus.qbt.support.provider;

import org.hyperledger.besu.consensus.common.VoteType;
import org.hyperledger.besu.consensus.common.bft.Vote;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.apache.tuweni.bytes.Bytes;

public class VoteTypeSerializer extends JsonSerializer<VoteType> {
  @Override
  public void serialize(
      final VoteType value, final JsonGenerator gen, final SerializerProvider serializers)
      throws IOException {
    gen.writeString(
        value == VoteType.ADD
            ? Bytes.of(Vote.ADD_BYTE_VALUE).toHexString().toLowerCase()
            : Bytes.of(Vote.DROP_BYTE_VALUE).toHexString().toLowerCase());
  }
}
