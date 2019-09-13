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
package tech.pegasys.pantheon.ethereum.api.jsonrpc.internal.parameters;

import tech.pegasys.pantheon.ethereum.api.TopicsParameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

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
    List<String> topicsList = new ArrayList<>();

    try {
      // parse as list of lists
      return jsonparser.readValueAs(TopicsParameter.class);
    } catch (MismatchedInputException mie) {
      // single list case
      String topics = jsonparser.getText();
      jsonparser.nextToken(); // consume end of array character
      if (topics == null) {
        return new TopicsParameter(Collections.singletonList(topicsList));
      } else {
        // make it list of list
        return new TopicsParameter(Collections.singletonList(Collections.singletonList(topics)));
      }
    }
  }
}
