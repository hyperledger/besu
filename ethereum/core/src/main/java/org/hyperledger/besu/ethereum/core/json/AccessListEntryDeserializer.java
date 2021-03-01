package org.hyperledger.besu.ethereum.core.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.core.AccessListEntry;
import org.hyperledger.besu.ethereum.core.Address;

import java.io.IOException;
import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkState;

public class AccessListEntryDeserializer extends StdDeserializer<AccessListEntry> {
  private AccessListEntryDeserializer() {
    this(null);
  }

  protected AccessListEntryDeserializer(final Class<?> vc) {
    super(vc);
  }

  @Override
  public AccessListEntry deserialize(final JsonParser p, final DeserializationContext ctxt)
      throws IOException {
    checkState(p.nextFieldName().equals("address"));
    final Address address = Address.fromHexString(p.nextTextValue());
    checkState(p.nextFieldName().equals("storageKeys"));
    checkState(p.nextToken().equals(JsonToken.START_ARRAY));
    final ArrayList<Bytes32> storageKeys = new ArrayList<>();
    while (!p.nextToken().equals(JsonToken.END_ARRAY)) {
      storageKeys.add(Bytes32.fromHexString(p.getText()));
    }
    p.nextToken(); // consume end of object
    return new AccessListEntry(address, storageKeys);
  }
}
