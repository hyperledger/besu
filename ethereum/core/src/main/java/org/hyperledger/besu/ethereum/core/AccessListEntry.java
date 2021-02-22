/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.core;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.apache.tuweni.bytes.Bytes32;

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
