package org.hyperledger.besu.ethereum.core.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.core.AccessListEntry;

import java.io.IOException;
import java.util.List;

public class AccessListEntrySerializer extends StdSerializer<AccessListEntry> {

    AccessListEntrySerializer() {
        this(null);
    }

    protected AccessListEntrySerializer(final Class<AccessListEntry> t) {
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
