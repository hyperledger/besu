package org.hyperledger.besu.consensus.qbt.support.provider;

import org.hyperledger.besu.consensus.common.VoteType;
import org.hyperledger.besu.consensus.common.bft.Vote;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.tuweni.bytes.Bytes;

public class VoteTypeDeserializer extends JsonDeserializer<VoteType> {

  @Override
  public VoteType deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException, JsonProcessingException {
    final Bytes bytes = Bytes.fromHexString(p.getValueAsString());
    final VoteType voteType;
    if (bytes.equals(Bytes.of(Vote.ADD_BYTE_VALUE))) {
      return VoteType.ADD;
    } else if (bytes.equals(Bytes.of(Vote.DROP_BYTE_VALUE))) {
      return VoteType.DROP;
    }

    throw new JsonMappingException(p, "Unexpected value for voteType");
  }
}
