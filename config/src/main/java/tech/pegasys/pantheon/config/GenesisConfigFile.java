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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;

public class GenesisConfigFile {

  public static final GenesisConfigFile DEFAULT = new GenesisConfigFile(new JsonObject());

  private final JsonObject configRoot;

  private GenesisConfigFile(final JsonObject config) {
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

  public static GenesisConfigFile fromConfig(final String config) {
    return fromConfig(new JsonObject(config));
  }

  public static GenesisConfigFile fromConfig(final JsonObject config) {
    return new GenesisConfigFile(normalizeKeys(config));
  }

  public GenesisConfigOptions getConfigOptions() {
    return new JsonGenesisConfigOptions(configRoot.getJsonObject("config"));
  }

  public Stream<GenesisAllocation> getAllocations() {
    final JsonObject allocations = configRoot.getJsonObject("alloc", new JsonObject());
    return allocations.fieldNames().stream()
        .map(key -> new GenesisAllocation(key, allocations.getJsonObject(key)));
  }

  public String getParentHash() {
    return configRoot.getString("parenthash", "");
  }

  public String getDifficulty() {
    return getRequiredString("difficulty");
  }

  public String getExtraData() {
    return configRoot.getString("extradata", "");
  }

  public long getGasLimit() {
    return parseLong("gasLimit", getRequiredString("gaslimit"));
  }

  public String getMixHash() {
    return configRoot.getString("mixhash", "");
  }

  public String getNonce() {
    return configRoot.getString("nonce", "0x0");
  }

  public Optional<String> getCoinbase() {
    return Optional.ofNullable(configRoot.getString("coinbase"));
  }

  public long getTimestamp() {
    return parseLong("timestamp", configRoot.getString("timestamp", "0x0"));
  }

  private String getRequiredString(final String key) {
    if (!configRoot.containsKey(key)) {
      throw new IllegalArgumentException(
          String.format("Invalid genesis block configuration, missing value for '%s'", key));
    }
    return configRoot.getString(key);
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

  /* Converts the {@link JsonObject} describing the Genesis Block to a {@link Map}. This method
   * converts all nested {@link JsonObject} to {@link Map} as well. Also, note that all keys are
   * converted to lowercase for easier lookup since the keys in a 'genesis.json' file are assumed
   * case insensitive.
   */
  @SuppressWarnings("unchecked")
  private static JsonObject normalizeKeys(final JsonObject genesis) {
    final Map<String, Object> normalized = new HashMap<>();
    genesis
        .getMap()
        .forEach(
            (key, value) -> {
              final String normalizedKey = key.toLowerCase(Locale.US);
              if (value instanceof JsonObject) {
                normalized.put(normalizedKey, normalizeKeys((JsonObject) value));
              } else if (value instanceof Map) {
                normalized.put(
                    normalizedKey, normalizeKeys(new JsonObject((Map<String, Object>) value)));
              } else {
                normalized.put(normalizedKey, value);
              }
            });
    return new JsonObject(normalized);
  }
}
