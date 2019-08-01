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
package tech.pegasys.pantheon.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
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

  public static GenesisConfigFile development() {
    try {
      return fromConfig(
          Resources.toString(GenesisConfigFile.class.getResource("/dev.json"), UTF_8));
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public static GenesisConfigFile fromConfig(final String jsonString) {
    // TODO: Should we disable comments?
    final boolean allowComments = true;
    final ObjectNode rootNode = JsonUtil.objectNodeFromString(jsonString, allowComments);
    return fromConfig(rootNode);
  }

  public static GenesisConfigFile fromConfig(final ObjectNode config) {
    return new GenesisConfigFile(normalizeKeys(config));
  }

  public GenesisConfigOptions getConfigOptions() {
    ObjectNode config =
        JsonUtil.getObjectNode(configRoot, "config").orElse(JsonUtil.createEmptyObjectNode());
    return new JsonGenesisConfigOptions(config);
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

  /* Converts all to lowercase for easier lookup since the keys in a 'genesis.json' file are assumed
   * case insensitive.
   */
  private static ObjectNode normalizeKeys(final ObjectNode genesis) {
    final ObjectNode normalized = JsonUtil.createEmptyObjectNode();
    genesis
        .fields()
        .forEachRemaining(
            entry -> {
              final String key = entry.getKey();
              final JsonNode value = entry.getValue();
              final String normalizedKey = key.toLowerCase(Locale.US);
              if (value instanceof ObjectNode) {
                normalized.set(normalizedKey, normalizeKeys((ObjectNode) value));
              } else {
                normalized.set(normalizedKey, value);
              }
            });
    return normalized;
  }
}
