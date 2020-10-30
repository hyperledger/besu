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
package org.hyperledger.besu.ethstats.request;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EthStatsRequest {

  public static final ObjectMapper MAPPER = new ObjectMapper();

  public static final String EMIT_FIELD = "emit";

  @JsonProperty(EMIT_FIELD)
  private List<Object> emit;

  private EthStatsRequest() {}

  public EthStatsRequest(final Type type, final Object... parameters) {
    this.emit =
        Stream.concat(Stream.of(type.value), Stream.of(parameters)).collect(Collectors.toList());
  }

  @JsonIgnore
  public Type getType() {
    return getEmit().stream()
        .findFirst()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .map(Type::fromValue)
        .orElse(Type.UNKNOWN);
  }

  public List<Object> getEmit() {
    return emit;
  }

  public String generateCommand() throws JsonProcessingException {
    return MAPPER.writeValueAsString(this);
  }

  public static EthStatsRequest fromResponse(final String value) {
    try {
      return MAPPER.readValue(value, EthStatsRequest.class);
    } catch (JsonProcessingException e) {
      return new EthStatsRequest(Type.UNKNOWN);
    }
  }

  public enum Type {
    HELLO("hello"),
    READY("ready"),
    NODE_PING("node-ping"),
    NODE_PONG("node-pong"),
    LATENCY("latency"),
    BLOCK("block"),
    HISTORY("history"),
    PENDING("pending"),
    STATS("stats"),
    UNKNOWN("");

    String value;

    Type(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    public static Type fromValue(final String value) {
      for (Type type : values()) {
        if (type.value.equalsIgnoreCase(value)) {
          return type;
        }
      }
      return UNKNOWN;
    }
  }
}
