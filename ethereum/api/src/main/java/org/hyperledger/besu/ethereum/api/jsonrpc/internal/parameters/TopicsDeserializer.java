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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.parameters;

import org.hyperledger.besu.ethereum.api.query.TopicsParameter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.google.common.collect.Lists;

public class TopicsDeserializer extends StdDeserializer<TopicsParameter> {
  public TopicsDeserializer() {
    this(null);
  }

  public TopicsDeserializer(final Class<?> vc) {
    super(vc);
  }

  @Override
  public TopicsParameter deserialize(
      final JsonParser jsonparser, final DeserializationContext context) throws IOException {
    final JsonNode topicsNode = jsonparser.getCodec().readTree(jsonparser);
    final List<List<String>> topics = Lists.newArrayList();

    if (!topicsNode.isArray()) {
      topics.add(Collections.singletonList(topicsNode.textValue()));
    } else {
      for (JsonNode child : topicsNode) {
        if (child.isArray()) {
          final List<String> childItems = Lists.newArrayList();
          for (JsonNode subChild : child) {
            childItems.add(subChild.textValue());
          }
          topics.add(childItems);
        } else {
          topics.add(Collections.singletonList(child.textValue()));
        }
      }
    }

    return new TopicsParameter(topics);
  }
}
