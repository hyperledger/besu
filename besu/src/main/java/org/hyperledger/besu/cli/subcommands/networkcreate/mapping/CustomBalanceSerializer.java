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

import org.hyperledger.besu.ethereum.core.Wei;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

// TODO Handle errors
class CustomBalanceSerializer extends StdSerializer<Wei> {

  CustomBalanceSerializer() {
    this(null);
  }

  private CustomBalanceSerializer(final Class<Wei> t) {
    super(t);
  }

  @Override
  public void serialize(
      final Wei balance, final JsonGenerator jsonGenerator, final SerializerProvider serializer)
      throws IOException {
    jsonGenerator.writeNumber(new BigDecimal(new BigInteger(balance.toUnprefixedHexString(), 16)));
  }
}
