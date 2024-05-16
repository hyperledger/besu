/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.core.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.apache.tuweni.units.bigints.UInt64;
import org.apache.tuweni.units.bigints.UInt64s;

public class GasDeserializer extends StdDeserializer<Long> {
  private static final UInt64 GAS_MAX_VALUE = UInt64.valueOf(Long.MAX_VALUE);

  public GasDeserializer() {
    this(null);
  }

  public GasDeserializer(final Class<?> vc) {
    super(vc);
  }

  @Override
  public Long deserialize(final JsonParser jsonparser, final DeserializationContext context)
      throws IOException {
    final var uint64 =
        UInt64.fromHexString(jsonparser.getCodec().readValue(jsonparser, String.class));
    // we can safely cap the value to Long.MAX_VALUE since gas is not expected to reach these value
    // anytime soon if ever
    return UInt64s.min(uint64, GAS_MAX_VALUE).toLong();
  }
}
