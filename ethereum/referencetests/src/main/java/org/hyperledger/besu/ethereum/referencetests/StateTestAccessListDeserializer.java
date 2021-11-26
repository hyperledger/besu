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
 *
 */
package org.hyperledger.besu.ethereum.referencetests;

import org.hyperledger.besu.evm.AccessListEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

public class StateTestAccessListDeserializer extends JsonDeserializer<List<List<AccessListEntry>>> {
  @Override
  public List<List<AccessListEntry>> deserialize(
      final JsonParser p, final DeserializationContext ctxt) throws IOException {
    final ObjectMapper objectMapper = new ObjectMapper();
    final List<List<AccessListEntry>> accessLists = new ArrayList<>();
    while (!p.nextToken().equals(JsonToken.END_ARRAY)) {
      accessLists.add(
          p.currentToken().equals(JsonToken.VALUE_NULL)
              ? null
              : Arrays.asList(objectMapper.readValue(p, AccessListEntry[].class)));
    }
    return accessLists;
  }
}
