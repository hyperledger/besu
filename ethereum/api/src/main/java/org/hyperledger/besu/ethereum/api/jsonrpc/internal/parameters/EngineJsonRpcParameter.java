/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

/**
 * Engine-API specific JsonRpcParameter that allows large payloads. This is needed for
 * engine_newPayload which can contain very large transactions.
 */
public class EngineJsonRpcParameter extends JsonRpcParameter {

  private static final ObjectMapper engineMapper =
      new ObjectMapper()
          .registerModule(new Jdk8Module()); // Handle JDK8 Optionals (de)serialization

  static {
    // Configure with max string length for large payload parameters
    engineMapper
        .getFactory()
        .setStreamReadConstraints(
            StreamReadConstraints.builder().maxStringLength(Integer.MAX_VALUE).build());
  }

  @Override
  protected ObjectMapper getObjectMapper() {
    return engineMapper;
  }
}
