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
import static org.hyperledger.besu.config.JsonUtil.normalizeKeys;

import org.hyperledger.besu.datatypes.Wei;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Streams;
import com.google.common.io.Resources;

/** The Genesis config file. */
public class GenesisConfigFile {

  /** The constant DEFAULT. */
  public static final GenesisConfigFile DEFAULT =
      new GenesisConfigFile(JsonUtil.createEmptyObjectNode());

  /** The constant BASEFEE_AT_GENESIS_DEFAULT_VALUE. */
  public static final Wei BASEFEE_AT_GENESIS_DEFAULT_VALUE = Wei.of(1_000_000_000L);

  private final ObjectNode configRoot;

  private GenesisConfigFile(final ObjectNode config) {
    this.configRoot = config;
  }

  /**
   * Mainnet genesis config file.
   *
   * @return the genesis config file
   */
  public static GenesisConfigFile mainnet() {
    return genesisFileFromResources("/mainnet.json");
  }

  /**
   * Mainnet json node object node.
   *
   * @return the object node
   */
  public static ObjectNode mainnetJsonNode() {
    try {
      final String jsonString =
          Resources.toString(GenesisConfigFile.class.getResource("/mainnet.json"), UTF_8);
      return JsonUtil.objectNodeFromString(jsonString, false);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Development genesis config file.
   *
   * @return the genesis config file
   */
  public static GenesisConfigFile development() {
    return genesisFileFromResources("/dev.json");
  }

  /**
   * Genesis file from resources genesis config file.
   *
   * @param resourceName the resource name
   * @return the genesis config file
   */
  public static GenesisConfigFile genesisFileFromResources(final String resourceName) {
    try {
      return fromConfig(
          Resources.toString(GenesisConfigFile.class.getResource(resourceName), UTF_8));
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * From config genesis config file.
   *
   * @param jsonString the json string
   * @return the genesis config file
   */
  public static GenesisConfigFile fromConfig(final String jsonString) {
    return fromConfig(JsonUtil.objectNodeFromString(jsonString, false));
  }

  /**
   * From config genesis config file.
   *
   * @param config the config
   * @return the genesis config file
   */
  public static GenesisConfigFile fromConfig(final ObjectNode config) {
    return new GenesisConfigFile(normalizeKeys(config));
  }

  /**
   * Gets config options.
   *
   * @return the config options
   */
  public GenesisConfigOptions getConfigOptions() {
    return getConfigOptions(Collections.emptyMap());
  }

  /**
   * Gets config options.
   *
   * @param overrides the overrides
   * @return the config options
   */
  public GenesisConfigOptions getConfigOptions(final Map<String, String> overrides) {
    final ObjectNode config =
        JsonUtil.getObjectNode(configRoot, "config").orElse(JsonUtil.createEmptyObjectNode());

    Map<String, String> overridesRef = overrides;

    // if baseFeePerGas has been explicitly configured, pass it as an override:
    final var optBaseFee = getBaseFeePerGas();
    if (optBaseFee.isPresent()) {
      // streams and maps cannot handle null values.
      overridesRef = new HashMap<>(overrides);
      overridesRef.put("baseFeePerGas", optBaseFee.get().toShortHexString());
    }

    return JsonGenesisConfigOptions.fromJsonObjectWithOverrides(config, overridesRef);
  }

  /**
   * Stream allocations stream.
   *
   * @return the stream
   */
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

  /**
   * Gets parent hash.
   *
   * @return the parent hash
   */
  public String getParentHash() {
    return JsonUtil.getString(configRoot, "parenthash", "");
  }

  /**
   * Gets difficulty.
   *
   * @return the difficulty
   */
  public String getDifficulty() {
    return getRequiredString("difficulty");
  }

  /**
   * Gets extra data.
   *
   * @return the extra data
   */
  public String getExtraData() {
    return JsonUtil.getString(configRoot, "extradata", "");
  }

  /**
   * Gets gas limit.
   *
   * @return the gas limit
   */
  public long getGasLimit() {
    return parseLong("gasLimit", getFirstRequiredString("gaslimit", "gastarget"));
  }

  /**
   * Gets base fee per gas.
   *
   * @return the base fee per gas
   */
  public Optional<Wei> getBaseFeePerGas() {
    return JsonUtil.getString(configRoot, "basefeepergas")
        .map(baseFeeStr -> Wei.of(parseLong("baseFeePerGas", baseFeeStr)));
  }

  /**
   * Gets genesis base fee per gas.
   *
   * @return the genesis base fee per gas
   */
  public Optional<Wei> getGenesisBaseFeePerGas() {
    if (getBaseFeePerGas().isPresent()) {
      // always use specified basefee if present
      return getBaseFeePerGas();
    } else if (getConfigOptions().getLondonBlockNumber().orElse(-1L) == 0) {
      // if not specified, and we specify london at block zero use a default fee
      // this is needed for testing.
      return Optional.of(BASEFEE_AT_GENESIS_DEFAULT_VALUE);
    } else {
      // no explicit base fee and no london block zero means no basefee at genesis
      return Optional.empty();
    }
  }

  /**
   * Gets mix hash.
   *
   * @return the mix hash
   */
  public String getMixHash() {
    return JsonUtil.getString(configRoot, "mixhash", "");
  }

  /**
   * Gets nonce.
   *
   * @return the nonce
   */
  public String getNonce() {
    return JsonUtil.getValueAsString(configRoot, "nonce", "0x0");
  }

  /**
   * Gets excess data gas.
   *
   * @return the excess data gas
   */
  public String getExcessDataGas() {
    return JsonUtil.getValueAsString(configRoot, "excessblobgas", "0x0");
  }

  /**
   * Gets excess data gas.
   *
   * @return the excess data gas
   */
  public String getParentBeaconBlockRoot() {
    return JsonUtil.getValueAsString(
        configRoot,
        "parentbeaconblockroot",
        "0x0000000000000000000000000000000000000000000000000000000000000000");
  }

  /**
   * Gets data gas used.
   *
   * @return the data gas used
   */
  public String getDataGasUsed() {
    return JsonUtil.getValueAsString(configRoot, "blobgasused", "0x0");
  }

  /**
   * Gets coinbase.
   *
   * @return the coinbase
   */
  public Optional<String> getCoinbase() {
    return JsonUtil.getString(configRoot, "coinbase");
  }

  /**
   * Gets timestamp.
   *
   * @return the timestamp
   */
  public long getTimestamp() {
    return parseLong("timestamp", JsonUtil.getValueAsString(configRoot, "timestamp", "0x0"));
  }

  private String getRequiredString(final String key) {
    return getFirstRequiredString(key);
  }

  private String getFirstRequiredString(final String... keys) {
    List<String> keysList = Arrays.asList(keys);
    return keysList.stream()
        .filter(configRoot::has)
        .findFirst()
        .map(key -> configRoot.get(key).asText())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format(
                        "Invalid genesis block configuration, missing value for one of '%s'",
                        keysList)));
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

  /**
   * Get Fork Block numbers
   *
   * @return list of fork block numbers
   */
  public List<Long> getForkBlockNumbers() {
    return getConfigOptions().getForkBlockNumbers();
  }

  /**
   * Get fork time stamps
   *
   * @return list of fork time stamps
   */
  public List<Long> getForkTimestamps() {
    return getConfigOptions().getForkBlockTimestamps();
  }
}
