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

package org.hyperledger.besu.evm;

import static com.google.common.base.Preconditions.checkState;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.AccessListEntry;

import java.io.IOException;
import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.apache.tuweni.bytes.Bytes32;

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
