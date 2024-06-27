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

/**
 * This class represents an Ethereum statistics request. It provides methods to get the type of the
 * request and the parameters associated with it.
 */
public class EthStatsRequest {

  /** The constant MAPPER. */
  public static final ObjectMapper MAPPER = new ObjectMapper();

  /** The constant EMIT_FIELD. */
  public static final String EMIT_FIELD = "emit";

  @JsonProperty(EMIT_FIELD)
  private List<Object> emit;

  private EthStatsRequest() {}

  /**
   * Constructs a new EthStatsRequest with the given type and parameters.
   *
   * @param type the type of the request
   * @param parameters the parameters of the request
   */
  public EthStatsRequest(final Type type, final Object... parameters) {
    this.emit =
        Stream.concat(Stream.of(type.value), Stream.of(parameters)).collect(Collectors.toList());
  }

  /**
   * Gets the type of the request.
   *
   * @return the type of the request
   */
  @JsonIgnore
  public Type getType() {
    return getEmit().stream()
        .findFirst()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .map(Type::fromValue)
        .orElse(Type.UNKNOWN);
  }

  /**
   * Gets the parameters of the request.
   *
   * @return the parameters of the request
   */
  public List<Object> getEmit() {
    return emit;
  }

  /**
   * Generates a command string from the request.
   *
   * @return the command string
   * @throws JsonProcessingException if there is an error processing the JSON
   */
  public String generateCommand() throws JsonProcessingException {
    return MAPPER.writeValueAsString(this);
  }

  /**
   * Creates an EthStatsRequest from a response string.
   *
   * @param value the response string
   * @return the EthStatsRequest
   */
  public static EthStatsRequest fromResponse(final String value) {
    try {
      return MAPPER.readValue(value, EthStatsRequest.class);
    } catch (JsonProcessingException e) {
      return new EthStatsRequest(Type.UNKNOWN);
    }
  }

  /** The enum Type represents the type of the request. */
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

    /**
     * Gets the value of the type.
     *
     * @return the value of the type
     */
    public String getValue() {
      return value;
    }

    /**
     * Gets the type from a value string.
     *
     * @param value the value string
     * @return the type
     */
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
