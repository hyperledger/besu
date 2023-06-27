/*
 * Copyright Hyperledger Besu Contributors.
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
 *
 */

package org.hyperledger.besu.evmtool;

import org.hyperledger.besu.datatypes.Address;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.apache.tuweni.bytes.Bytes;

public class JsonUtils {

  private JsonUtils() {}

  /**
   * Create an object mapper with all the standard bells and whistles needed for evmtool
   *
   * @return a properly constructed ObjectMapper
   */
  public static ObjectMapper createObjectMapper() {
    final ObjectMapper objectMapper = new ObjectMapper();

    // Attempting to get byte-perfect to go's standard json output
    objectMapper.setDefaultPrettyPrinter(
        (new DefaultPrettyPrinter())
            .withSpacesInObjectEntries()
            .withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent("  "))
            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent("  ")));

    // When we stream stdin we cannot close the stream
    objectMapper.disable(Feature.AUTO_CLOSE_SOURCE);

    // GraalVM has a hard time reflecting these classes for serialization
    SimpleModule serializers = new SimpleModule("Serializers");
    serializers.addSerializer(Address.class, ToStringSerializer.instance);
    serializers.addSerializer(Bytes.class, ToStringSerializer.instance);
    objectMapper.registerModule(serializers);

    return objectMapper;
  }
}
