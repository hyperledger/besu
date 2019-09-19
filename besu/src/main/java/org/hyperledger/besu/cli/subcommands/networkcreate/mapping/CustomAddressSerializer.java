/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.cli.subcommands.networkcreate.mapping;

import org.hyperledger.besu.ethereum.core.Address;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
// TODO Handle errors
class CustomAddressSerializer extends StdSerializer<Address> {

  CustomAddressSerializer() {
    this(null);
  }

  private CustomAddressSerializer(Class<Address> t) {
    super(t);
  }

  @Override
  public void serialize(Address address, JsonGenerator jsonGenerator, SerializerProvider serializer)
      throws IOException {
    jsonGenerator.writeString(address.toString());
  }
}
