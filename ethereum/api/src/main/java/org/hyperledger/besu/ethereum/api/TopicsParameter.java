/*
 * Copyright 2018 ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api;

import org.hyperledger.besu.ethereum.core.LogTopic;
import org.hyperledger.besu.util.bytes.BytesValue;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;

public class TopicsParameter {

  private final List<List<LogTopic>> queryTopics = new ArrayList<>();

  @JsonCreator
  public TopicsParameter(final List<List<String>> topics) {
    if (topics != null) {
      for (final List<String> list : topics) {
        final List<LogTopic> inputTopics = new ArrayList<>();
        if (list != null) {
          for (final String input : list) {
            final LogTopic topic =
                input != null ? LogTopic.create(BytesValue.fromHexString(input)) : null;
            inputTopics.add(topic);
          }
        }
        queryTopics.add(inputTopics);
      }
    }
  }

  public List<List<LogTopic>> getTopics() {
    return queryTopics;
  }

  @Override
  public String toString() {
    return "TopicsParameter{" + "queryTopics=" + queryTopics + '}';
  }
}
