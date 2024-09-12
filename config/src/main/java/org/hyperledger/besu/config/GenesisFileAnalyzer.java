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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyzes a genesis file and provides insight into its file style, consensus mechanism, and
 * network version.
 */
public class GenesisFileAnalyzer {

  private static final Logger LOG = LoggerFactory.getLogger(GenesisFileAnalyzer.class);

  private GenesisFileAnalyzer() {}

  record GenesisAnalysis(
      FileStyle fileStyle,
      ConsensusMechanism consensusMechanism,
      ObjectNode configSection,
      String networkVersion) {
    public enum FileStyle {
      BESU,
      GETH,
      AMBIGUOUS,
      UNKNOWN
    }

    public enum ConsensusMechanism {
      ETHASH,
      CLIQUE,
      IBFT2,
      QBFT,
      UNKNOWN
    }
  }

  /**
   * Analyzes the given genesis file and returns an analysis of its structure.
   *
   * @param genesisJson The genesis file as a JSON object.
   * @return A {@link GenesisAnalysis} containing the file style, consensus mechanism, and other
   *     details.
   */
  static GenesisAnalysis analyzeGenesisFile(final ObjectNode genesisJson) {
    try {
      LOG.info("Starting genesis file analysis.");

      GenesisAnalysis.FileStyle fileStyle = detectFileStyle(genesisJson);
      LOG.info("Detected file style: {}", fileStyle);

      ObjectNode configSection = extractConfigSection(genesisJson, fileStyle);
      LOG.info("Config section extracted.");

      GenesisAnalysis.ConsensusMechanism consensusMechanism =
          detectConsensusMechanism(configSection, fileStyle);
      LOG.info("Consensus mechanism detected: {}", consensusMechanism);

      String networkVersion = detectNetworkVersion(configSection);
      LOG.info("Network version detected: {}", networkVersion);

      GenesisAnalysis analysis =
          new GenesisAnalysis(fileStyle, consensusMechanism, configSection, networkVersion);
      validateConsistency(analysis);

      return analysis;
    } catch (Exception e) {
      LOG.error("Error during genesis file analysis: ", e);
      throw e;
    }
  }

  /**
   * Detects the file style (Besu or Geth) based on the structure of the genesis file.
   *
   * @param genesisJson The genesis file as a JSON object.
   * @return The detected {@link GenesisAnalysis.FileStyle}.
   */
  private static GenesisAnalysis.FileStyle detectFileStyle(final ObjectNode genesisJson) {
    // Assume BESU style by default
    GenesisAnalysis.FileStyle fileStyle = GenesisAnalysis.FileStyle.BESU;

    if (genesisJson.has("config")) {
      ObjectNode configNode = (ObjectNode) genesisJson.get("config");

      // Check for Besu-specific characteristics
      if (configNode.has("ibft2")
          || configNode.has("qbft")
          || configNode.has("discovery")
          || configNode.has("checkpoint")
          || configNode.has("contractSizeLimit")) {
        return GenesisAnalysis.FileStyle.BESU;
      }

      // The presence of ethash with fixeddifficulty is compatible with both BESU and GETH
      // So we don't need to change the fileStyle based on this
    }

    // Check for Geth-specific extraData format that Besu doesn't support
    if (genesisJson.has("extradata")) {
      String extraData = genesisJson.get("extradata").asText();
      if (extraData.length() == 64 || extraData.length() == 32) {
        fileStyle = GenesisAnalysis.FileStyle.GETH;
      }
    }

    LOG.info("Detected file style: {}", fileStyle);

    return fileStyle;
  }

  /**
   * Extracts the config section from the genesis file based on the detected file style.
   *
   * @param genesisJson The genesis file as a JSON object.
   * @param fileStyle The detected file style.
   * @return The config section as an {@link ObjectNode}.
   */
  private static ObjectNode extractConfigSection(
      final ObjectNode genesisJson, final GenesisAnalysis.FileStyle fileStyle) {
    ObjectNode config = JsonUtil.createEmptyObjectNode();
    if (fileStyle == GenesisAnalysis.FileStyle.BESU && genesisJson.has("config")) {
      return (ObjectNode) genesisJson.get("config");
    } else if (fileStyle == GenesisAnalysis.FileStyle.GETH) {
      String[] relevantFields = {
        "chainId",
        "homesteadBlock",
        "eip150Block",
        "eip155Block",
        "eip158Block",
        "byzantiumBlock",
        "constantinopleBlock",
        "petersburgBlock",
        "istanbulBlock",
        "muirGlacierBlock",
        "berlinBlock",
        "londonBlock",
        "clique",
        "ethash",
        "difficulty",
        "gasLimit",
        "extraData"
      };
      for (String field : relevantFields) {
        if (genesisJson.has(field)) {
          config.set(field, genesisJson.get(field));
        }
      }
      // If the config is at the root level for Geth, move it to the config object
      if (genesisJson.has("config")) {
        config.setAll((ObjectNode) genesisJson.get("config"));
      }
    }
    return config;
  }

  /**
   * Detects the consensus mechanism used in the genesis file.
   *
   * @param config The config section of the genesis file.
   * @param fileStyle The detected file style.
   * @return The detected {@link GenesisAnalysis.ConsensusMechanism}.
   */
  private static GenesisAnalysis.ConsensusMechanism detectConsensusMechanism(
      final ObjectNode config, final GenesisAnalysis.FileStyle fileStyle) {
    if (config.has("clique")) {
      return GenesisAnalysis.ConsensusMechanism.CLIQUE;
    } else if (config.has("ethash")) {
      return GenesisAnalysis.ConsensusMechanism.ETHASH;
    } else if (config.has("ibft2")) {
      return GenesisAnalysis.ConsensusMechanism.IBFT2;
    } else if (config.has("qbft")) {
      return GenesisAnalysis.ConsensusMechanism.QBFT;
    }
    // If no consensus mechanism is found in the config, check the file style
    if (fileStyle == GenesisAnalysis.FileStyle.GETH) {
      // For Geth-style files, default to Ethash if not specified
      return GenesisAnalysis.ConsensusMechanism.ETHASH;
    }
    return GenesisAnalysis.ConsensusMechanism.UNKNOWN;
  }

  /**
   * Detects the network version based on the chain ID in the config section.
   *
   * @param config The config section of the genesis file.
   * @return The detected network version as a string.
   */
  private static String detectNetworkVersion(final ObjectNode config) {
    if (config.has("chainId")) {
      JsonNode chainIdNode = config.get("chainId");
      long chainId;
      try {
        if (chainIdNode.isTextual()) {
          chainId = Long.parseLong(chainIdNode.asText().replaceFirst("^0x", ""), 16);
        } else if (chainIdNode.isNumber()) {
          chainId = chainIdNode.asLong();
        } else {
          throw new IllegalArgumentException("Unexpected chainId format");
        }
      } catch (NumberFormatException e) {
        throw new RuntimeException("Invalid chainId format", e);
      }

      return switch ((int) chainId) {
        case 1 -> "Mainnet";
        case 3 -> "Ropsten";
        case 4 -> "Rinkeby";
        case 5 -> "Goerli";
        case 42 -> "Kovan";
        default -> "Unknown network (Chain ID: " + chainId + ")";
      };
    }
    return "Unknown network";
  }

  /**
   * Validates the consistency of the analyzed genesis file.
   *
   * @param analysis The result of the genesis file analysis.
   */
  private static void validateConsistency(final GenesisAnalysis analysis) {
    if (analysis.fileStyle() == GenesisAnalysis.FileStyle.GETH
        && (analysis.consensusMechanism() == GenesisAnalysis.ConsensusMechanism.IBFT2
            || analysis.consensusMechanism() == GenesisAnalysis.ConsensusMechanism.QBFT)) {
      LOG.warn(
          "Inconsistent genesis file: Geth-style file with IBFT2/QBFT consensus detected. "
              + "This combination is unusual and may indicate a misconfiguration.");
    }
  }
}
