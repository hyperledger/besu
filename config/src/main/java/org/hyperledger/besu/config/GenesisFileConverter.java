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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Converts a Geth-style genesis file to a Besu-style genesis file. */
public class GenesisFileConverter {

  private static final Logger LOG = LoggerFactory.getLogger(GenesisFileConverter.class);
  private static final int EXTRA_VANITY_LENGTH = 32;
  private static final int SECP_SIG_BYTES_REQUIRED = 65;

  private GenesisFileConverter() {}

  /**
   * Converts a Geth-style genesis file to a Besu-style genesis file.
   *
   * @param preNormalizedConfig The pre-normalized config section of the genesis file.
   * @param normalizedConfig The normalized config section of the genesis file.
   * @return A Besu-style genesis file as a JSON object.
   */
  static ObjectNode convertGethToBesu(
      final ObjectNode preNormalizedConfig, final ObjectNode normalizedConfig) {
    LOG.info("Starting Geth to Besu genesis conversion.");

    final ObjectNode besuGenesis = JsonUtil.createEmptyObjectNode();
    final ObjectNode besuConfig = JsonUtil.createEmptyObjectNode();

    try {
      // Convert config section
      convertConfig(preNormalizedConfig, normalizedConfig, besuConfig);

      // Set the converted config in the Besu genesis
      besuGenesis.set("config", besuConfig);

      // Convert allocations
      convertAllocations(preNormalizedConfig, besuGenesis);

      // Convert other root-level fields
      convertRootLevelFields(normalizedConfig, besuGenesis);

      // Handle consensus-specific conversions
      handleConsensusSpecificConversions(preNormalizedConfig, besuGenesis, besuConfig);

      LOG.info("Geth to Besu conversion completed.");
    } catch (Exception e) {
      LOG.error("Error during Geth to Besu conversion: ", e);
      throw new RuntimeException("Conversion failed", e);
    }

    return besuGenesis;
  }

  private static void convertConfig(
      final ObjectNode preNormalizedConfig,
      final ObjectNode normalizedConfig,
      final ObjectNode besuConfig) {
    // Copy all fields from the pre-normalized config
    if (preNormalizedConfig.has("config")) {
      JsonNode gethConfig = preNormalizedConfig.get("config");
      Iterator<String> fieldNames = gethConfig.fieldNames();
      while (fieldNames.hasNext()) {
        String fieldName = fieldNames.next();
        besuConfig.set(fieldName, gethConfig.get(fieldName));
      }
    }

    // Ensure chainId is present and in the correct format
    if (normalizedConfig.has("chainid")) {
      JsonNode chainIdNode = normalizedConfig.get("chainid");
      if (chainIdNode.isTextual()) {
        long chainId = Long.parseLong(chainIdNode.asText().replaceFirst("^0x", ""), 16);
        besuConfig.put("chainId", chainId);
      }
    }

    // Handle fork-specific fields
    String[] forkFields = {
      "homesteadBlock", "eip150Block", "eip155Block", "eip158Block",
      "byzantiumBlock", "constantinopleBlock", "petersburgBlock", "istanbulBlock",
      "muirGlacierBlock", "berlinBlock", "londonBlock"
    };
    for (String field : forkFields) {
      String normalizedField = field.toLowerCase(Locale.ROOT);
      if (normalizedConfig.has("config") && normalizedConfig.get("config").has(normalizedField)) {
        besuConfig.set(field, normalizedConfig.get("config").get(normalizedField));
      }
    }
  }

  private static void convertRootLevelFields(
      final ObjectNode normalizedConfig, final ObjectNode besuGenesis) {
    String[] rootFields = {
      "coinbase",
      "difficulty",
      "extraData",
      "gasLimit",
      "nonce",
      "mixhash",
      "parentHash",
      "timestamp"
    };
    for (String field : rootFields) {
      if (field.equals("extraData")) {
        // Handle extraData separately
        if (normalizedConfig.has("extradata")) {
          besuGenesis.put(field, normalizedConfig.get("extradata").asText());
        }
      } else {
        copyIfPresent(normalizedConfig, besuGenesis, field);
      }
    }
  }

  private static void convertAllocations(
      final ObjectNode gethGenesis, final ObjectNode besuGenesis) {
    if (gethGenesis.has("alloc")) {
      ObjectNode allocations = JsonUtil.createEmptyObjectNode();
      JsonNode gethAlloc = gethGenesis.get("alloc");
      Iterator<String> addresses = gethAlloc.fieldNames();
      while (addresses.hasNext()) {
        String address = addresses.next();
        JsonNode allocation = gethAlloc.get(address);
        ObjectNode besuAllocation = JsonUtil.createEmptyObjectNode();

        if (allocation.has("balance")) {
          besuAllocation.put("balance", convertBalance(allocation.get("balance").asText()));
        }
        if (allocation.has("code")) {
          besuAllocation.put("code", allocation.get("code").asText());
        }
        if (allocation.has("storage")) {
          besuAllocation.set("storage", allocation.get("storage"));
        }
        if (allocation.has("nonce")) {
          besuAllocation.set("nonce", allocation.get("nonce"));
        }

        allocations.set(address, besuAllocation);
      }
      besuGenesis.set("alloc", allocations);
    }
  }

  private static void handleConsensusSpecificConversions(
      final ObjectNode preNormalizedConfig,
      final ObjectNode besuGenesis,
      final ObjectNode besuConfig) {
    if (besuConfig.has("clique")) {
      handleCliqueConversion(preNormalizedConfig, besuGenesis, besuConfig);
    } else if (besuConfig.has("ethash")) {
      handleEthashConversion(besuGenesis);
    }

    // Handle extraData field
    if (preNormalizedConfig.has("extraData")) {
      String extraData = preNormalizedConfig.get("extraData").asText();
      besuGenesis.put("extraData", extraData);
    }
  }

  private static void handleCliqueConversion(
      final ObjectNode preNormalizedConfig,
      final ObjectNode besuGenesis,
      final ObjectNode besuConfig) {
    // Copy extraData as-is
    if (preNormalizedConfig.has("extraData")) {
      String extraData = preNormalizedConfig.get("extraData").asText();
      validateCliqueExtraData(extraData);
      besuGenesis.put("extraData", extraData);
    }

    // Ensure clique.period is set.
    if (!besuConfig.has("clique") || !besuConfig.get("clique").has("period")) {
      ObjectNode cliqueConfig =
          besuConfig.has("clique")
              ? (ObjectNode) besuConfig.get("clique")
              : besuConfig.putObject("clique");
      cliqueConfig.put("period", 15); // Default period
    }
  }

  private static void validateCliqueExtraData(final String extraData) {
    if (!extraData.startsWith("0x")) {
      throw new IllegalArgumentException("extraData must start with 0x");
    }

    Bytes extraDataBytes = Bytes.fromHexString(extraData);

    if (extraDataBytes.size() < EXTRA_VANITY_LENGTH + SECP_SIG_BYTES_REQUIRED) {
      throw new IllegalArgumentException(
          "Invalid Bytes supplied - too short to produce a valid Clique Extra Data object.");
    }

    final int validatorByteCount =
        extraDataBytes.size() - EXTRA_VANITY_LENGTH - SECP_SIG_BYTES_REQUIRED;
    if ((validatorByteCount % Address.SIZE) != 0) {
      throw new IllegalArgumentException("Bytes is of invalid size - i.e. contains unused bytes.");
    }
  }

  private static void handleEthashConversion(final ObjectNode besuGenesis) {
    // Ensure difficulty is set for Ethash
    if (!besuGenesis.has("difficulty")) {
      besuGenesis.put("difficulty", "0x1");
    }

    // Ensure nonce and mixHash are set
    if (!besuGenesis.has("nonce")) {
      besuGenesis.put("nonce", "0x0000000000000042");
    }
    if (!besuGenesis.has("mixHash") && !besuGenesis.has("mixhash")) {
      besuGenesis.put(
          "mixHash", "0x0000000000000000000000000000000000000000000000000000000000000000");
    }
  }

  private static String convertBalance(final String balance) {
    if (balance.startsWith("0x")) {
      return Wei.fromHexString(balance).toString();
    } else {
      return Wei.of(new BigInteger(balance)).toString();
    }
  }

  private static void copyIfPresent(
      final ObjectNode fromNormalized, final ObjectNode to, final String field) {
    String normalizedField = field.toLowerCase(Locale.ROOT);
    if (fromNormalized.has(normalizedField)) {
      to.set(field, fromNormalized.get(normalizedField));
    }
  }
}
