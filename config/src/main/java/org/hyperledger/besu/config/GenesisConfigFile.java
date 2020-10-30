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
package org.hyperledger.besu.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.hyperledger.besu.config.JsonUtil.normalizeKeys;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;
import com.google.common.io.Resources;

public class GenesisConfigFile {

  public static final GenesisConfigFile DEFAULT =
      new GenesisConfigFile(JsonUtil.createEmptyObjectNode());

  private final ObjectNode configRoot;

  private GenesisConfigFile(final ObjectNode config) {
    this.configRoot = config;
  }

  public static GenesisConfigFile mainnet() {
    try {
      return fromConfig(
          Resources.toString(GenesisConfigFile.class.getResource("/mainnet.json"), UTF_8));
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static ObjectNode mainnetJsonNode() {
    try {
      final String jsonString =
          Resources.toString(GenesisConfigFile.class.getResource("/mainnet.json"), UTF_8);
      return JsonUtil.objectNodeFromString(jsonString, false);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static GenesisConfigFile development() {
    try {
      return fromConfig(
          Resources.toString(GenesisConfigFile.class.getResource("/dev.json"), UTF_8));
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static GenesisConfigFile fromConfig(final String jsonString) {
    return fromConfig(JsonUtil.objectNodeFromString(jsonString, false));
  }

  public static GenesisConfigFile fromConfig(final ObjectNode config) {
    return new GenesisConfigFile(normalizeKeys(config));
  }

  public GenesisConfigOptions getConfigOptions() {
    return getConfigOptions(Collections.emptyMap());
  }

  public GenesisConfigOptions getConfigOptions(final Map<String, String> overrides) {
    final ObjectNode config =
        JsonUtil.getObjectNode(configRoot, "config").orElse(JsonUtil.createEmptyObjectNode());
    return JsonGenesisConfigOptions.fromJsonObjectWithOverrides(config, overrides);
  }

  public Stream<GenesisAllocation> streamAllocations() {
    return JsonUtil.getObjectNode(configRoot, "alloc").stream()
        .flatMap(
            allocations ->
                Streams.stream(allocations.fieldNames())
                    .map(
                        key ->
                            new GenesisAllocation(
                                key, JsonUtil.getObjectNode(allocations, key).get())));
  }

  public String getParentHash() {
    return JsonUtil.getString(configRoot, "parenthash", "");
  }

  public String getDifficulty() {
    return getRequiredString("difficulty");
  }

  public String getExtraData() {
    return JsonUtil.getString(configRoot, "extradata", "");
  }

  public long getGasLimit() {
    return parseLong("gasLimit", getRequiredString("gaslimit"));
  }

  public String getMixHash() {
    return JsonUtil.getString(configRoot, "mixhash", "");
  }

  public String getNonce() {
    return JsonUtil.getValueAsString(configRoot, "nonce", "0x0");
  }

  public Optional<String> getCoinbase() {
    return JsonUtil.getString(configRoot, "coinbase");
  }

  public long getTimestamp() {
    return parseLong("timestamp", JsonUtil.getValueAsString(configRoot, "timestamp", "0x0"));
  }

  public List<Long> getForks() {
    return JsonUtil.getObjectNode(configRoot, "config").stream()
        .flatMap(
            node ->
                Streams.stream(node.fieldNames())
                    .map(String::toLowerCase)
                    .filter(name -> !name.equals("chainid"))
                    .filter(name -> node.get(name).canConvertToLong())
                    .filter(name -> name.contains("block"))
                    .filter(name -> !name.equals("classicforkblock"))
                    .map(name -> node.get(name).asLong()))
        .sorted()
        .collect(toUnmodifiableList());
  }

  private String getRequiredString(final String key) {
    if (!configRoot.has(key)) {
      throw new IllegalArgumentException(
          String.format("Invalid genesis block configuration, missing value for '%s'", key));
    }
    return configRoot.get(key).asText();
  }

  private long parseLong(final String name, final String value) {
    try {
      return Long.decode(value);
    } catch (final NumberFormatException e) {
      throw new IllegalArgumentException(
          "Invalid genesis block configuration, "
              + name
              + " must be a number but was '"
              + value
              + "'");
    }
  }
}
