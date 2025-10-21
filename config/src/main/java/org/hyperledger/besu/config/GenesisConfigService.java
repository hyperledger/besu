/*
 * Copyright contributors to Hyperledger Besu.
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

import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Service for loading and transforming genesis configuration files. This service provides automatic
 * detection and transformation of Geth-format genesis files to Besu format, enabling seamless
 * compatibility between the two Ethereum client formats.
 *
 * <p>The service handles five main transformations for Geth genesis files:
 *
 * <ol>
 *   <li>Adding the {@code ethash} field to the config (required for Besu's consensus detection)
 *   <li>Mapping {@code mergeNetsplitBlock} to {@code preMergeForkBlock} (different field names for
 *       the same purpose)
 *   <li>Adding {@code baseFeePerGas} when London fork is activated at genesis (block 0)
 *   <li>Adding {@code withdrawalRequestContractAddress} if missing (EIP-7002)
 *   <li>Adding {@code consolidationRequestContractAddress} if missing (EIP-7251)
 * </ol>
 */
public class GenesisConfigService {

  private final GenesisConfig genesisConfig;
  private final GenesisConfigOptions genesisConfigOptions;

  /**
   * Private constructor. Use factory methods to create instances.
   *
   * @param genesisConfig the genesis configuration
   */
  private GenesisConfigService(final GenesisConfig genesisConfig) {
    this.genesisConfig = genesisConfig;
    this.genesisConfigOptions = genesisConfig.getConfigOptions();
  }

  /**
   * Create a GenesisConfigService from a file, with automatic Geth format detection and
   * transformation.
   *
   * @param genesisFile the genesis file
   * @param overrides configuration overrides to apply
   * @return a new GenesisConfigService instance
   */
  public static GenesisConfigService fromFile(
      final File genesisFile, final Map<String, String> overrides) {
    try {
      final URL url = genesisFile.toURI().toURL();
      final GenesisConfig config = loadAndTransform(url, overrides);
      return new GenesisConfigService(config);
    } catch (final Exception e) {
      // Extract the root cause for better error reporting
      Throwable rootCause = e;
      while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
        rootCause = rootCause.getCause();
      }
      throw new RuntimeException("Unable to load genesis file: " + genesisFile, rootCause);
    }
  }

  /**
   * Create a GenesisConfigService from a resource name (no transformation needed for built-in
   * networks).
   *
   * @param resourceName the resource name
   * @param overrides configuration overrides to apply
   * @return a new GenesisConfigService instance
   */
  public static GenesisConfigService fromResource(
      final String resourceName, final Map<String, String> overrides) {
    final GenesisConfig config = GenesisConfig.fromResource(resourceName).withOverrides(overrides);
    return new GenesisConfigService(config);
  }

  /**
   * Create a GenesisConfigService from an existing GenesisConfig.
   *
   * @param genesisConfig the genesis configuration
   * @return a new GenesisConfigService instance
   */
  public static GenesisConfigService fromGenesisConfig(final GenesisConfig genesisConfig) {
    return new GenesisConfigService(genesisConfig);
  }

  /**
   * Gets the genesis configuration.
   *
   * @return the genesis config
   */
  public GenesisConfig getGenesisConfig() {
    return genesisConfig;
  }

  /**
   * Gets the genesis configuration options.
   *
   * @return the genesis config options
   */
  public GenesisConfigOptions getGenesisConfigOptions() {
    return genesisConfigOptions;
  }

  /**
   * Gets the EC curve from the genesis configuration if specified.
   *
   * @return the EC curve, or empty if not specified
   */
  public Optional<String> getEcCurve() {
    return genesisConfigOptions.getEcCurve();
  }

  /**
   * Gets the block period in seconds based on the consensus mechanism.
   *
   * @return the block period in seconds, or empty if not applicable
   */
  public OptionalInt getBlockPeriodSeconds() {
    if (genesisConfigOptions.isClique()) {
      return OptionalInt.of(genesisConfigOptions.getCliqueConfigOptions().getBlockPeriodSeconds());
    }
    if (genesisConfigOptions.isIbft2()) {
      return OptionalInt.of(genesisConfigOptions.getBftConfigOptions().getBlockPeriodSeconds());
    }
    if (genesisConfigOptions.isQbft()) {
      return OptionalInt.of(genesisConfigOptions.getQbftConfigOptions().getBlockPeriodSeconds());
    }
    return OptionalInt.empty();
  }

  /**
   * Checks if the genesis configuration includes any fork times that require KZG initialization.
   * This includes Cancun and all subsequent forks that use KZG commitments for EIP-4844 blob
   * transactions.
   *
   * @return true if any KZG-requiring fork time is present
   */
  public boolean hasKzgFork() {
    return genesisConfigOptions.getCancunTime().isPresent()
        || genesisConfigOptions.getCancunEOFTime().isPresent()
        || genesisConfigOptions.getPragueTime().isPresent()
        || genesisConfigOptions.getOsakaTime().isPresent()
        || genesisConfigOptions.getBpo1Time().isPresent()
        || genesisConfigOptions.getBpo2Time().isPresent()
        || genesisConfigOptions.getBpo3Time().isPresent()
        || genesisConfigOptions.getBpo4Time().isPresent()
        || genesisConfigOptions.getBpo5Time().isPresent()
        || genesisConfigOptions.getAmsterdamTime().isPresent()
        || genesisConfigOptions.getFutureEipsTime().isPresent();
  }

  /**
   * Checks if the genesis configuration uses a Proof of Authority (PoA) consensus mechanism.
   *
   * @return true if the consensus is PoA (IBFT2, QBFT, or Clique)
   */
  public boolean isPoaConsensus() {
    return genesisConfigOptions.isPoa();
  }

  /**
   * Gets the epoch length for PoA consensus mechanisms.
   *
   * @return epoch length if PoA consensus is configured, empty otherwise
   */
  public OptionalLong getPoaEpochLength() {
    if (genesisConfigOptions.isIbft2()) {
      return OptionalLong.of(genesisConfigOptions.getBftConfigOptions().getEpochLength());
    } else if (genesisConfigOptions.isQbft()) {
      return OptionalLong.of(genesisConfigOptions.getQbftConfigOptions().getEpochLength());
    } else if (genesisConfigOptions.isClique()) {
      return OptionalLong.of(genesisConfigOptions.getCliqueConfigOptions().getEpochLength());
    }
    return OptionalLong.empty();
  }

  /**
   * Gets the name of the consensus mechanism configured in the genesis.
   *
   * @return the consensus mechanism name (e.g., "IBFT2", "QBFT", "Clique", "Ethash")
   */
  public String getConsensusMechanism() {
    if (genesisConfigOptions.isIbft2()) {
      return "IBFT2";
    } else if (genesisConfigOptions.isQbft()) {
      return "QBFT";
    } else if (genesisConfigOptions.isClique()) {
      return "Clique";
    } else if (genesisConfigOptions.isEthHash()) {
      return "Ethash";
    }
    return "Unknown";
  }

  /**
   * Loads a genesis file from URL and applies Geth-to-Besu transformation if needed.
   *
   * @param url the URL to load from
   * @param overrides configuration overrides to apply
   * @return the loaded and potentially transformed GenesisConfig
   */
  private static GenesisConfig loadAndTransform(
      final URL url, final Map<String, String> overrides) {
    // Load the raw JSON
    final ObjectNode genesisRoot = JsonUtil.objectNodeFromURL(url, false);

    // Check if this is a Geth format genesis file
    if (isGethFormat(genesisRoot)) {
      // Transform it to Besu format
      transformGethToBesu(genesisRoot);
    }

    // Create GenesisConfig from the (potentially transformed) JSON
    final GenesisConfig config = GenesisConfig.fromConfig(genesisRoot);
    return config.withOverrides(overrides);
  }

  /**
   * Detects if a genesis file is in Geth format.
   *
   * <p>A genesis file is considered Geth format if:
   *
   * <ul>
   *   <li>It has a "config" section
   *   <li>The config has a "mergeNetsplitBlock" field (Geth-specific)
   *   <li>The config does NOT have an "ethash" field (Besu-specific)
   * </ul>
   *
   * @param genesisRoot the root genesis JSON node
   * @return true if this is a Geth format genesis file
   */
  private static boolean isGethFormat(final ObjectNode genesisRoot) {
    final Optional<ObjectNode> configNode = JsonUtil.getObjectNode(genesisRoot, "config");
    if (!configNode.isPresent()) {
      return false;
    }

    final ObjectNode config = configNode.get();
    final boolean hasMergeNetsplitBlock = config.has("mergeNetsplitBlock");
    final boolean hasEthash = config.has("ethash");

    // It's Geth format if it has mergeNetsplitBlock but not ethash
    return hasMergeNetsplitBlock && !hasEthash;
  }

  /**
   * Transforms a Geth-format genesis file to Besu format by applying five transformations.
   *
   * <p>Transformations applied:
   *
   * <ol>
   *   <li><b>Add ethash field:</b> Besu's {@code isEthHash()} method checks for the presence of
   *       this field in the JSON structure. Since this is a structural check, the overrides
   *       mechanism doesn't work - we must add it to the JSON.
   *   <li><b>Map mergeNetsplitBlock to preMergeForkBlock:</b> These fields serve identical purposes
   *       (marking the merge activation block) but use different names in Geth vs Besu.
   *   <li><b>Add baseFeePerGas:</b> When London fork is activated at genesis (block 0), Besu
   *       expects an explicit base fee. Geth may omit this field, so we add the standard default of
   *       1 gwei (0x3B9ACA00).
   *   <li><b>Add withdrawalRequestContractAddress:</b> EIP-7002 withdrawal request contract address
   *       if missing.
   *   <li><b>Add consolidationRequestContractAddress:</b> EIP-7251 consolidation request contract
   *       address if missing.
   * </ol>
   *
   * @param genesisRoot the root genesis JSON node (will be modified in place)
   */
  private static void transformGethToBesu(final ObjectNode genesisRoot) {
    final Optional<ObjectNode> configNode = JsonUtil.getObjectNode(genesisRoot, "config");
    if (!configNode.isPresent()) {
      return;
    }

    final ObjectNode config = configNode.get();

    // Add ethash field if not present
    if (!config.has("ethash")) {
      config.set("ethash", JsonUtil.createEmptyObjectNode());
    }

    // Map mergeNetsplitBlock to preMergeForkBlock
    if (config.has("mergeNetsplitBlock") && !config.has("preMergeForkBlock")) {
      final long mergeBlock = config.get("mergeNetsplitBlock").asLong();
      config.put("preMergeForkBlock", mergeBlock);
    }

    // Add baseFeePerGas if London is at genesis
    if (!genesisRoot.has("baseFeePerGas") && config.has("londonBlock")) {
      final long londonBlock = config.get("londonBlock").asLong(Long.MAX_VALUE);
      if (londonBlock == 0) {
        // Add default 1 gwei base fee
        genesisRoot.put("baseFeePerGas", "0x3B9ACA00");
      }
    }

    // Add withdrawalRequestContractAddress if missing (EIP-7002)
    if (!config.has("withdrawalRequestContractAddress")) {
      config.put("withdrawalRequestContractAddress", "0x00000961ef480eb55e80d19ad83579a64c007002");
    }

    // Add consolidationRequestContractAddress if missing (EIP-7251)
    if (!config.has("consolidationRequestContractAddress")) {
      config.put(
          "consolidationRequestContractAddress", "0x0000bbddc7ce488642fb579f8b00f3a590007251");
    }
  }
}
