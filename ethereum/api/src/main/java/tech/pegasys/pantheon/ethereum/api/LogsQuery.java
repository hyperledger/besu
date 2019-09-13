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
package tech.pegasys.pantheon.ethereum.api;

import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Log;
import tech.pegasys.pantheon.ethereum.core.LogTopic;

import java.util.Arrays;
import java.util.List;

import com.google.common.collect.Lists;

public class LogsQuery {

  private final List<Address> queryAddresses;
  private final List<List<LogTopic>> queryTopics;

  private LogsQuery(final List<Address> addresses, final List<List<LogTopic>> topics) {
    this.queryAddresses = addresses;
    this.queryTopics = topics;
  }

  public boolean matches(final Log log) {
    return matchesAddresses(log.getLogger()) && matchesTopics(log.getTopics());
  }

  private boolean matchesAddresses(final Address address) {
    return queryAddresses.isEmpty() || queryAddresses.contains(address);
  }

  private boolean matchesTopics(final List<LogTopic> topics) {
    if (queryTopics.isEmpty()) {
      return true;
    }
    if (topics.size() < queryTopics.size()) {
      return false;
    }
    for (int i = 0; i < queryTopics.size(); ++i) {
      if (!matchesTopic(topics.get(i), queryTopics.get(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean matchesTopic(final LogTopic topic, final List<LogTopic> matchCriteria) {
    for (final LogTopic candidate : matchCriteria) {
      if (candidate == null) {
        return true;
      }
      if (candidate.equals(topic)) {
        return true;
      }
    }
    return false;
  }

  public static class Builder {
    private final List<Address> queryAddresses = Lists.newArrayList();
    private final List<List<LogTopic>> queryTopics = Lists.newArrayList();

    public Builder address(final Address address) {
      if (address != null) {
        queryAddresses.add(address);
      }
      return this;
    }

    public Builder addresses(final Address... addresses) {
      if (addresses != null && addresses.length > 0) {
        queryAddresses.addAll(Arrays.asList(addresses));
      }
      return this;
    }

    public Builder addresses(final List<Address> addresses) {
      if (addresses != null && !addresses.isEmpty()) {
        queryAddresses.addAll(addresses);
      }
      return this;
    }

    public Builder topics(final List<List<LogTopic>> topics) {
      if (topics != null && !topics.isEmpty()) {
        queryTopics.addAll(topics);
      }
      return this;
    }

    public Builder topics(final TopicsParameter topicsParameter) {
      if (topicsParameter != null) {
        topics(topicsParameter.getTopics());
      }
      return this;
    }

    public LogsQuery build() {
      return new LogsQuery(queryAddresses, queryTopics);
    }
  }
}
