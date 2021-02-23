package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.ethereum.core.AccessListEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StateTestAccessListDeserializer extends JsonDeserializer<List<List<AccessListEntry>>> {
  @Override
  public List<List<AccessListEntry>> deserialize(
      final JsonParser p, final DeserializationContext ctxt) throws IOException {
    final ObjectMapper objectMapper = new ObjectMapper();
    final List<List<AccessListEntry>> accessLists = new ArrayList<>();
    while (!p.nextToken().equals(JsonToken.END_ARRAY)) {
      accessLists.add(
          p.currentToken().equals(JsonToken.VALUE_NULL)
              ? null
              : Arrays.asList(objectMapper.readValue(p, AccessListEntry[].class)));
    }
    return accessLists;
  }
}
