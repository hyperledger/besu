package org.hyperledger.besu.ethereum.core;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.tuweni.bytes.Bytes32;

import java.io.IOException;
import java.util.List;

@JsonSerialize(using = AccessListEntry.Serializer.class)
public class AccessListEntry {
  private final Address address;
  private final List<Bytes32> storageKeys;

  public AccessListEntry(final Address address, final List<Bytes32> storageKeys) {

    this.address = address;
    this.storageKeys = storageKeys;
  }

  public Address getAddress() {
    return address;
  }

  public List<Bytes32> getStorageKeys() {
    return storageKeys;
  }

  public static class Serializer extends StdSerializer<AccessListEntry> {

    Serializer() {
      this(null);
    }

    protected Serializer(final Class<AccessListEntry> t) {
      super(t);
    }

    @Override
    public void serialize(
        final AccessListEntry accessListEntry,
        final JsonGenerator gen,
        final SerializerProvider provider)
        throws IOException {
      gen.writeStartObject();
      gen.writeFieldName("address");
      gen.writeString(accessListEntry.getAddress().toHexString());
      gen.writeFieldName("storageKeys");
      final List<Bytes32> storageKeys = accessListEntry.getStorageKeys();
      gen.writeArray(
          storageKeys.stream().map(Bytes32::toHexString).toArray(String[]::new),
          0,
          storageKeys.size());
      gen.writeEndObject();
    }
  }
}
