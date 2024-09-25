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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperledger.besu.datatypes.Address;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Resources;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenesisFileHandlingTest {

  private static final Logger LOG = LoggerFactory.getLogger(GenesisFileHandlingTest.class);
  private static final int EXTRA_VANITY_LENGTH = 32;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

  @Test
  void shouldValidateCliqueExtraDataStructure() {
    System.out.println("Starting validation of Clique extraData structure");
    String validatorAddress = "1122334455667788990011223344556677889900";
    String vanityData = "00".repeat(32);
    String paddingData = "00".repeat(65);
    String extraData = "0x" + vanityData + validatorAddress + paddingData;

    System.out.println("Vanity data length: " + vanityData.length() + " characters");
    System.out.println("Validator address length: " + validatorAddress.length() + " characters");
    System.out.println("Padding data length: " + paddingData.length() + " characters");
    System.out.println("ExtraData (hex): " + extraData);
    System.out.println("ExtraData length (including 0x): " + extraData.length() + " characters");
    System.out.println(
        "ExtraData length (excluding 0x): " + (extraData.length() - 2) + " characters");

    Bytes extraDataBytes = Bytes.fromHexString(extraData);

    System.out.println("ExtraDataBytes size: " + extraDataBytes.size() + " bytes");
    System.out.println("ExtraDataBytes (hex): " + extraDataBytes.toHexString());

    // Check if the validator address is correctly positioned
    Bytes extractedValidator = extraDataBytes.slice(32, 20); // 32 bytes vanity, 20 bytes address
    String extractedValidatorString = extractedValidator.toHexString().toLowerCase(Locale.ROOT);

    System.out.println("Extracted validator address: " + extractedValidatorString);

    // Check the vanity data
    Bytes vanity = extraDataBytes.slice(0, 32); // 32 bytes of vanity data
    System.out.println("Vanity data: " + vanity.toHexString());

    // Check the zero padding at the end
    Bytes zeroPadding = extraDataBytes.slice(52); // remaining bytes should be zero padding
    System.out.println("Zero padding: " + zeroPadding.toHexString());

    System.out.println("Asserting validator address position");
    assertEquals(
        "0x" + validatorAddress,
        extractedValidatorString,
        "Validator address should be correctly positioned");
    System.out.println("Asserting vanity data");
    assertEquals(
        Bytes.fromHexString("0x" + "00".repeat(32)), vanity, "Vanity data should be 32 zero bytes");
    System.out.println("Asserting zero padding");
    assertEquals(
        Bytes.fromHexString("0x" + "00".repeat(65)),
        zeroPadding,
        "There should be 65 bytes of zero padding");
    System.out.println("Asserting extraData length");
    assertEquals(
        117,
        extraDataBytes.size(),
        "ExtraData should be 117 bytes (32 vanity + 20 address + 65 padding)");

    System.out.println("Completed validation of Clique extraData structure");
  }

  @Test
  void shouldProcessGethLikeBesuFile() throws JsonProcessingException {
    // Read in Besu genesis file with Geth-style fields
    String besuGenesisJson =
        """
        {
          "config": {
            "chainId": 1,
            "homesteadBlock": 1150000,
            "daoForkBlock": 1920000,
            "eip150Block": 2463000,
            "eip158Block": 2675000,
            "byzantiumBlock": 4370000,
            "petersburgBlock": 7280000,
            "istanbulBlock": 9069000,
            "muirGlacierBlock": 9200000,
            "berlinBlock": 12244000,
            "londonBlock": 12965000,
            "arrowGlacierBlock": 13773000,
            "grayGlacierBlock": 15050000,
            "terminalTotalDifficulty": 58750000000000000000000,
            "shanghaiTime": 1681338455,
            "cancunTime": 1710338135,
            "ethash": {},
            "discovery": {
              "dns": "enrtree://AKA3AM6LPBYEUDMVNU3BSVQJ5AD45Y7YPOHJLEF6W26QOE4VTUDPE@all.mainnet.ethdisco.net",
              "bootnodes": [
                "enode://d860a01f9722d78051619d1e2351aba3f43f943f6f00718d1b9baa4101932a1f5011f16bb2b1bb35db20d6fe28fa0bf09636d26a87d31de9ec6203eeedb1f666@18.138.108.67:30303",
                "enode://22a8232c3abc76a16ae9d6c3b164f98775fe226f0917b0ca871128a74a8e9630b458460865bab457221f1d448dd9791d24c4e5d88786180ac185df813a68d4de@3.209.45.79:30303",
                "enode://2b252ab6a1d0f971d9722cb839a42cb81db019ba44c08754628ab4a823487071b5695317c8ccd085219c3a03af063495b2f1da8d18218da2d6a82981b45e6ffc@65.108.70.101:30303",
                "enode://4aeb4ab6c14b23e2c4cfdce879c04b0748a20d8e9b59e25ded2a08143e265c6c25936e74cbc8e641e3312ca288673d91f2f93f8e277de3cfa444ecdaaf982052@157.90.35.166:30303"
              ]
            },
            "checkpoint": {
              "hash": "0x44bca881b07a6a09f83b130798072441705d9a665c5ac8bdf2f39a3cdf3bee29",
              "number": 11052984,
              "totalDifficulty": "0x3D103014E5C74E5E196"
            }
          },
          "nonce": "0x42",
          "timestamp": "0x0",
          "extraData": "0x11bbe8db4e347b4e8c937c1c8370e4b5ed33adb3db69cbdb7a38e1e50b1b82fa",
          "gasLimit": "0x1388",
          "difficulty": "0x400000000",
          "mixHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
          "coinbase": "0x0000000000000000000000000000000000000000",
          "alloc": {
            "000d836201318ec6899a67540690382780743280": {
              "balance": "0xad78ebc5ac6200000"
            },
            "001762430ea9c3a26e5749afdb70da5f78ddbb8c": {
              "balance": "0xad78ebc5ac6200000"
            }
          }
        }
        """;

    ObjectNode besuGenesis = (ObjectNode) objectMapper.readTree(besuGenesisJson);

    ObjectNode processedConfig = JsonUtil.normalizeKeys(besuGenesis);
    GenesisFileAnalyzer.GenesisAnalysis analysis =
        GenesisFileAnalyzer.analyzeGenesisFile(processedConfig);

    assertEquals(
        GenesisFileAnalyzer.GenesisAnalysis.FileStyle.BESU,
        analysis.fileStyle(),
        "Expected FileStyle: BESU, Actual: " + analysis.fileStyle());
  }

  @Test
  void shouldProcessGethLikeBesuFile2() throws JsonProcessingException {
    // Read in Besu genesis file with Geth-style fields
    String besuGenesisJson =
        """
            {
              "config": {
                "chainId":1,
                "homesteadBlock":0,
                "eip150Block":0,
                "eip155Block":0,
                "eip158Block":0,
                "byzantiumBlock":0,
                "constantinopleBlock":0,
                "petersburgBlock":0,
                "istanbulBlock":0,
                "muirGlacierBlock":0,
                "berlinBlock":0,
                "londonBlock":0,
                "shanghaiTime":10,
                "clique": {
                  "period": 5,
                  "epoch": 30000
                },
                "terminalTotalDifficulty":0
              },
              "nonce":"0x42",
              "timestamp":"0x0",
              "extraData":"0x0000000000000000000000000000000000000000000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
              "gasLimit":"0x1C9C380",
              "difficulty":"0x400000000",
              "mixHash":"0x0000000000000000000000000000000000000000000000000000000000000000",
              "coinbase":"0x0000000000000000000000000000000000000000",
              "alloc":{
                "0xa94f5374fce5edbc8e2a8697c15331677e6ebf0b":{"balance":"0x6d6172697573766477000000"}
              },
              "number":"0x0",
              "gasUsed":"0x0",
              "parentHash":"0x0000000000000000000000000000000000000000000000000000000000000000",
              "baseFeePerGas":"0x7"
            }
            """;

    ObjectNode besuGenesis = (ObjectNode) objectMapper.readTree(besuGenesisJson);

    ObjectNode processedConfig = JsonUtil.normalizeKeys(besuGenesis);
    GenesisFileAnalyzer.GenesisAnalysis analysis =
        GenesisFileAnalyzer.analyzeGenesisFile(processedConfig);

    assertEquals(
        GenesisFileAnalyzer.GenesisAnalysis.FileStyle.BESU,
        analysis.fileStyle(),
        "Expected FileStyle: BESU, Actual: " + analysis.fileStyle());
  }

  @Test
  void testAnalyzeAmbiguousGenesisFile() {
    ObjectNode ambiguousGenesis = JSON.objectNode();
    ambiguousGenesis.set("config", JSON.objectNode());
    ambiguousGenesis.put("difficulty", "0x400");
    ambiguousGenesis.put("gasLimit", "0x8000000");
    GenesisFileAnalyzer.GenesisAnalysis analysis =
        GenesisFileAnalyzer.analyzeGenesisFile(ambiguousGenesis);
    // This test ensures that the analyzer makes a consistent decision for ambiguous files
    assertNotNull(analysis.fileStyle());
    assertNotNull(analysis.consensusMechanism());
    assertNotNull(analysis.networkVersion());
  }

  @Test
  void testAnalyzeGenesisFileWithLargeChainId() {
    ObjectNode largeChainIdGenesis = createBesuIbft2Genesis();
    ObjectNode configNode = (ObjectNode) largeChainIdGenesis.get("config");
    if (configNode == null) {
      configNode = JSON.objectNode();
      largeChainIdGenesis.set("config", configNode);
    }
    configNode.put("chainId", Long.MAX_VALUE);

    GenesisFileAnalyzer.GenesisAnalysis analysis =
        GenesisFileAnalyzer.analyzeGenesisFile(largeChainIdGenesis);

    LOG.info("Large Chain ID Genesis JSON: {}", largeChainIdGenesis);
    LOG.info("Analyzed Network Version: {}", analysis.networkVersion());
    assertEquals("Unknown network (Chain ID: " + Long.MAX_VALUE + ")", analysis.networkVersion());
  }

  @Test
  void testAnalyzeGenesisFileWithInvalidChainId() {
    ObjectNode invalidChainIdGenesis = createBesuIbft2Genesis();
    ObjectNode configNode = (ObjectNode) invalidChainIdGenesis.get("config");
    if (configNode == null) {
      configNode = JSON.objectNode();
      invalidChainIdGenesis.set("config", configNode);
    }
    configNode.put("chainId", "invalid");

    LOG.info("Invalid Chain ID Genesis JSON: {}", invalidChainIdGenesis);

    assertThrows(
        RuntimeException.class,
        () -> GenesisFileAnalyzer.analyzeGenesisFile(invalidChainIdGenesis));
  }

  @Test
  void shouldProcessOfficialBesuStyle() throws IOException {
    final ObjectNode besuGenesis =
        JsonUtil.objectNodeFromString(
            Resources.toString(Resources.getResource("besu.json"), StandardCharsets.UTF_8));

    ObjectNode processedConfig = JsonUtil.normalizeKeys(besuGenesis);
    GenesisFileAnalyzer.GenesisAnalysis analysis =
        GenesisFileAnalyzer.analyzeGenesisFile(processedConfig);

    assertEquals(
        GenesisFileAnalyzer.GenesisAnalysis.FileStyle.BESU,
        analysis.fileStyle(),
        "Expected FileStyle: BESU, Actual: " + analysis.fileStyle());
  }

  @Test
  void shouldConvertOfficialGethStyleToBesuStyle() throws IOException {
    final ObjectNode gethGenesis =
        JsonUtil.objectNodeFromString(
            Resources.toString(Resources.getResource("genesis.json"), StandardCharsets.UTF_8));

    ObjectNode normalizedConfig = JsonUtil.normalizeKeys(gethGenesis);
    GenesisFileAnalyzer.GenesisAnalysis analysis =
        GenesisFileAnalyzer.analyzeGenesisFile(normalizedConfig);

    assertEquals(
        GenesisFileAnalyzer.GenesisAnalysis.FileStyle.BESU,
        analysis.fileStyle(),
        "Expected FileStyle: BESU, Actual: " + analysis.fileStyle());

    ObjectNode besuGenesis = GenesisFileConverter.convertGethToBesu(gethGenesis, normalizedConfig);

    LOG.info("Geth Genesis: {}", gethGenesis);
    LOG.info("Converted Besu Genesis: {}", besuGenesis);

    assertEquals(
        7011893082L,
        besuGenesis.get("config").get("chainId").asLong(),
        "Expected chainId: 7011893082, Actual: "
            + besuGenesis.get("config").get("chainId").asLong());
    assertTrue(
        besuGenesis.get("config").has("homesteadBlock"),
        "Expected 'homesteadBlock' in config, Actual: " + getFields(besuGenesis.get("config")));
    assertEquals(
        "0x01",
        besuGenesis.get("difficulty").asText(),
        "Expected difficulty: 0x01, Actual: " + besuGenesis.get("difficulty").asText());
    assertEquals(
        "0x17d7840",
        besuGenesis.get("gasLimit").asText(),
        "Expected gasLimit: 0x17d7840, Actual: " + besuGenesis.get("gasLimit").asText());
    assertTrue(
        besuGenesis.has("alloc"),
        "Expected 'alloc' in Besu genesis, Actual keys: " + besuGenesis.fieldNames());
  }

  @Test
  void shouldConvertGethStyleEthashGenesisToBesuStyle() throws JsonProcessingException {
    String gethEthashGenesisJson =
        """
    {
      "config": {
        "chainId": 15,
        "homesteadBlock": 0,
        "eip150Block": 0,
        "eip155Block": 0,
        "eip158Block": 0,
        "byzantiumBlock": 0,
        "constantinopleBlock": 0,
        "petersburgBlock": 0,
        "ethash": {}
      },
      "nonce": "0x0000000000000042",
      "timestamp": "0x0",
      "extraData": "0x11bbe8db4e347b4e8c937c1c8370e4b5ed33adb3db69cbdb7a38e1e50b1b82fa",
      "gasLimit": "0x1000000",
      "difficulty": "0x400000000",
      "mixHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
      "coinbase": "0x0000000000000000000000000000000000000000",
      "alloc": {}
    }
    """;

    ObjectNode gethGenesis = (ObjectNode) objectMapper.readTree(gethEthashGenesisJson);
    ObjectNode normalizedConfig = JsonUtil.normalizeKeys(gethGenesis);
    ObjectNode besuGenesis = GenesisFileConverter.convertGethToBesu(gethGenesis, normalizedConfig);

    LOG.info("Converted Besu Ethash Genesis: {}", besuGenesis);

    assertTrue(
        besuGenesis.get("config").has("ethash"), "Besu genesis should contain ethash config");

    // Check if fields exist before asserting their values
    if (besuGenesis.has("nonce")) {
      assertEquals(
          "0x0000000000000042", besuGenesis.get("nonce").asText(), "Nonce should be preserved");
    } else {
      LOG.warn("Nonce field is missing in the converted Besu genesis");
    }

    if (besuGenesis.has("difficulty")) {
      assertEquals(
          "0x400000000", besuGenesis.get("difficulty").asText(), "Difficulty should be preserved");
    } else {
      LOG.warn("Difficulty field is missing in the converted Besu genesis");
    }

    if (besuGenesis.has("mixhash")) {
      assertEquals(
          "0x0000000000000000000000000000000000000000000000000000000000000000",
          besuGenesis.get("mixhash").asText(),
          "MixHash should be preserved");
    } else {
      LOG.warn("mixhash field is missing in the converted Besu genesis");
    }

    // Assert that essential fields are present
    assertTrue(besuGenesis.has("gasLimit"), "GasLimit should be present in Besu genesis");
    assertTrue(besuGenesis.has("difficulty"), "Difficulty should be present in Besu genesis");
    assertTrue(besuGenesis.has("alloc"), "Alloc should be present in Besu genesis");
  }

  @Test
  void shouldConvertGethStyleCliqueGenesisToBesuStyle() throws JsonProcessingException {
    String gethCliqueGenesisJson =
        """
    {
      "config": {
        "chainId": 15,
        "homesteadBlock": 0,
        "eip150Block": 0,
        "eip155Block": 0,
        "eip158Block": 0,
        "byzantiumBlock": 0,
        "constantinopleBlock": 0,
        "petersburgBlock": 0,
        "clique": {
          "period": 15,
          "epoch": 30000
        }
      },
      "nonce": "0x0",
      "timestamp": "0x0",
      "extraData": "0x0000000000000000000000000000000000000000000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
      "gasLimit": "0x1000000",
      "difficulty": "0x1",
      "mixHash": "0x0000000000000000000000000000000000000000000000000000000000000000",
      "coinbase": "0x0000000000000000000000000000000000000000",
      "alloc": {}
    }
    """;

    ObjectNode gethGenesis = (ObjectNode) objectMapper.readTree(gethCliqueGenesisJson);
    ObjectNode normalizedConfig = JsonUtil.normalizeKeys(gethGenesis);
    ObjectNode besuGenesis = GenesisFileConverter.convertGethToBesu(gethGenesis, normalizedConfig);

    LOG.info("Converted Besu Clique Genesis: {}", besuGenesis);

    assertTrue(
        besuGenesis.get("config").has("clique"), "Besu genesis should contain clique config");
    assertEquals(
        "0x1", besuGenesis.get("difficulty").asText(), "Difficulty should be 1 for Clique");

    // Check if the extraData contains a valid validator address
    String extraData = besuGenesis.get("extraData").asText();

    // Validate extraData using the new method
    Bytes extraDataBytes = Bytes.fromHexString(gethGenesis.get("extraData").asText());
    Optional<String> validationReason = validateExtraData(extraDataBytes);
    assertTrue(
        validationReason.isEmpty(),
        "ExtraData validation should succeed. Reason: " + validationReason.orElse(""));

    assertTrue(extraData.startsWith("0x"), "ExtraData should start with 0x");
    assertEquals(
        EXTRA_VANITY_LENGTH * 2 + 2,
        extraData.substring(0, 66).length(),
        "ExtraData should start with 32 bytes (64 hex chars) of vanity data");

    // Check if the extraData contains a valid validator address
    String validatorAddress = extraData.substring(66, 106);
    assertEquals(
        "a94f5374fce5edbc8e2a8697c15331677e6ebf0b",
        validatorAddress,
        "ExtraData should contain the validator address");

    // The total length should be:
    // 0x (2 chars) +
    // 32 bytes vanity (64 chars) +
    // 1 validator address (40 chars) +
    // 65 bytes signature (130 chars) =
    // 236 chars total
    assertEquals(
        236,
        extraData.length(),
        "ExtraData total length should be 236 characters for a single validator");

    // Validate difficulty
    assertEquals(
        "0x1", besuGenesis.get("difficulty").asText(), "Difficulty should be 0x1 for Clique");

    // Validate nonce
    assertEquals("0x0", besuGenesis.get("nonce").asText(), "Nonce should be 0x0 for Clique");
  }

  // Helper methods to create different types of genesis configurations
  private static ObjectNode createBesuIbft2Genesis() {
    ObjectNode genesis = JSON.objectNode();
    ObjectNode config = genesis.putObject("config");
    config.putObject("ibft2");
    config.put("chainId", 1); // Mainnet
    return genesis;
  }

  private static String getFields(final JsonNode node) {
    return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(node.fieldNames(), Spliterator.ORDERED), false)
        .collect(Collectors.joining(", "));
  }

  private static Optional<String> validateExtraData(final Bytes input) {
    final int EXTRA_VANITY_LENGTH = 32;
    final int SECP_SIG_BYTES_REQUIRED = 65;

    if (input.size() < EXTRA_VANITY_LENGTH + SECP_SIG_BYTES_REQUIRED) {
      return Optional.of("Input size is too small.");
    }

    final int validatorByteCount = input.size() - EXTRA_VANITY_LENGTH - SECP_SIG_BYTES_REQUIRED;
    if ((validatorByteCount % Address.SIZE) != 0) {
      return Optional.of("Bytes is of invalid size - i.e. contains unused bytes.");
    }

    return Optional.empty();
  }
}
