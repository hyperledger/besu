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

import java.io.IOException;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.rlp.RLP;
import org.apache.tuweni.rlp.RLPReader;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenesisFileHandlingTest {

  private static final Logger LOG = LoggerFactory.getLogger(GenesisFileHandlingTest.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

  @Test
  void shouldConvertGethStyleGenesisToBesuStyle() throws IOException {
    String gethGenesisJson =
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
            "istanbulBlock": 0
          },
          "difficulty": "0x400",
          "gasLimit": "0x8000000",
          "alloc": {
            "0x0000000000000000000000000000000000000001": {
              "balance": "0x1"
            }
          }
        }
        """;
    ObjectNode gethGenesis = (ObjectNode) objectMapper.readTree(gethGenesisJson);

    ObjectNode besuGenesis = GenesisFileConverter.convertGethToBesu(gethGenesis);

    LOG.info("Geth Genesis: {}", gethGenesis);
    LOG.info("Converted Besu Genesis: {}", besuGenesis);

    assertEquals(
        15,
        besuGenesis.get("config").get("chainId").asInt(),
        "Expected chainId: 15, Actual: " + besuGenesis.get("config").get("chainId").asInt());
    assertTrue(
        besuGenesis.get("config").has("ethash"),
        "Expected 'ethash' in config, Actual: " + besuGenesis.get("config").toString());
    assertEquals(
        "0x400",
        besuGenesis.get("difficulty").asText(),
        "Expected difficulty: 0x400, Actual: " + besuGenesis.get("difficulty").asText());
    assertEquals(
        "0x8000000",
        besuGenesis.get("gasLimit").asText(),
        "Expected gasLimit: 0x8000000, Actual: " + besuGenesis.get("gasLimit").asText());
    assertTrue(
        besuGenesis.has("alloc"),
        "Expected 'alloc' in Besu genesis, Actual keys: " + besuGenesis.fieldNames());
  }

  @Test
  void shouldConvertGethStyleCliqueGenesisToBesuStyle() throws IOException {
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
        "clique": {
          "period": 15,
          "epoch": 30000
        }
      },
      "difficulty": "0x1",
      "gasLimit": "0x8000000",
      "extradata": "0x0000000000000000000000000000000000000000000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
      "alloc": {}
    }
    """;
    ObjectNode gethGenesis = (ObjectNode) objectMapper.readTree(gethCliqueGenesisJson);

    ObjectNode besuGenesis = GenesisFileConverter.convertGethToBesu(gethGenesis);

    LOG.info("Geth Clique Genesis: {}", gethGenesis);
    LOG.info("Converted Besu Clique Genesis: {}", besuGenesis);

    assertTrue(
        besuGenesis.get("config").has("clique"),
        "Expected 'clique' in config, Actual: " + besuGenesis.get("config").toString());
    assertEquals(
        "0x1",
        besuGenesis.get("difficulty").asText(),
        "Expected difficulty: 0x1, Actual: " + besuGenesis.get("difficulty").asText());
    assertTrue(
        besuGenesis.has("extraData"),
        "Expected 'extraData' in Besu genesis, Actual keys: " + besuGenesis.fieldNames());

    String gethExtraData = gethGenesis.get("extradata").asText();
    String besuExtraData = besuGenesis.get("extraData").asText();

    LOG.info("Geth extraData: {}", gethExtraData);
    LOG.info("Geth extraData length: {} bytes", (gethExtraData.length() - 2) / 2);
    LOG.info("Besu extraData: {}", besuExtraData);
    LOG.info("Besu extraData length: {} bytes", (besuExtraData.length() - 2) / 2);

    // Check that the Besu extraData starts with the same 32 bytes of vanity data
    assertEquals(
        besuExtraData.substring(2, 66),
        gethExtraData.substring(2, 66),
        "Besu extraData should start with the same vanity data as Geth");

    // Check that the Besu extraData contains the validator address
    assertTrue(
        besuExtraData.contains(gethExtraData.substring(66, 106)),
        "Besu extraData should contain the validator address");

    // Check that the Besu extraData is exactly 194 bytes (388 hex characters + 2 for "0x")
    assertEquals(
        390,
        besuExtraData.length(),
        "Besu extraData should be exactly 194 bytes (390 hex characters including '0x')");
  }

  @Test
  void shouldHandleIncompleteGethGenesisFile() throws IOException {
    String incompleteGethGenesisJson =
        """
        {
          "config": {
            "chainId": 15
          },
          "difficulty": "0x400"
        }
        """;
    ObjectNode incompleteGethGenesis =
        (ObjectNode) objectMapper.readTree(incompleteGethGenesisJson);

    ObjectNode besuGenesis = GenesisFileConverter.convertGethToBesu(incompleteGethGenesis);

    LOG.info("Incomplete Geth Genesis: {}", incompleteGethGenesis);
    LOG.info("Converted Besu Genesis: {}", besuGenesis);

    assertEquals(
        15,
        besuGenesis.get("config").get("chainId").asInt(),
        "Expected chainId: 15, Actual: " + besuGenesis.get("config").get("chainId").asInt());
    assertEquals(
        "0x400",
        besuGenesis.get("difficulty").asText(),
        "Expected difficulty: 0x400, Actual: " + besuGenesis.get("difficulty").asText());
    assertTrue(
        besuGenesis.get("config").has("ethash"),
        "Expected 'ethash' in config, Actual: " + besuGenesis.get("config").toString());
  }

  @Test
  void shouldHandleEdgeCasesInGethToBesuConversion() throws IOException {
    String edgeCaseGethGenesisJson =
        """
        {
          "config": {
            "chainId": "0xF",
            "homesteadBlock": "0x0",
            "eip150Block": "0x0",
            "eip155Block": "0x0",
            "eip158Block": "0x0"
          },
          "difficulty": "0x20000",
          "gasLimit": "0x2fefd8",
          "alloc": {
            "0000000000000000000000000000000000000001": {"balance": "0x1"},
            "0000000000000000000000000000000000000002": {"balance": "0x1"}
          }
        }
        """;
    ObjectNode edgeCaseGethGenesis = (ObjectNode) objectMapper.readTree(edgeCaseGethGenesisJson);

    ObjectNode besuGenesis = GenesisFileConverter.convertGethToBesu(edgeCaseGethGenesis);

    LOG.info("Edge Case Geth Genesis: {}", edgeCaseGethGenesis);
    LOG.info("Converted Besu Genesis: {}", besuGenesis);

    assertEquals(
        15,
        besuGenesis.get("config").get("chainId").asInt(),
        "Expected chainId: 15, Actual: " + besuGenesis.get("config").get("chainId").asInt());
    assertEquals(
        0,
        besuGenesis.get("config").get("homesteadBlock").asInt(),
        "Expected homesteadBlock: 0, Actual: "
            + besuGenesis.get("config").get("homesteadBlock").asInt());
    assertEquals(
        "0x20000",
        besuGenesis.get("difficulty").asText(),
        "Expected difficulty: 0x20000, Actual: " + besuGenesis.get("difficulty").asText());
    assertEquals(
        "0x2fefd8",
        besuGenesis.get("gasLimit").asText(),
        "Expected gasLimit: 0x2fefd8, Actual: " + besuGenesis.get("gasLimit").asText());
    assertTrue(
        besuGenesis.get("alloc").has("0000000000000000000000000000000000000001"),
        "Expected address 0000000000000000000000000000000000000001 in alloc, Actual: "
            + besuGenesis.get("alloc").toString());
    assertTrue(
        besuGenesis.get("alloc").has("0000000000000000000000000000000000000002"),
        "Expected address 0000000000000000000000000000000000000002 in alloc, Actual: "
            + besuGenesis.get("alloc").toString());
  }

  @Test
  void shouldFixCliqueExtraData() {
    String originalExtraData = "0x00000000000000000000000000000000000000000000000000000000000000";
    LOG.info("Original extraData: {}", originalExtraData);
    LOG.info("Original extraData length: {} bytes", (originalExtraData.length() - 2) / 2);

    Bytes fixedExtraData =
        GenesisFileConverter.fixCliqueExtraData(Bytes.fromHexString(originalExtraData));

    String fixedExtraDataString = "0x" + fixedExtraData.toHexString();
    LOG.info("Fixed extraData: {}", fixedExtraDataString);
    LOG.info("Fixed extraData length: {} bytes", fixedExtraData.size());

    assertEquals(194, fixedExtraData.size(), "ExtraData should be 194 bytes");
    assertTrue(fixedExtraDataString.startsWith("0x"), "ExtraData should start with '0x'");
  }

  @Test
  void shouldFixShortCliqueExtraData() {
    String shortExtraData = "0x00000000000000000000000000000000000000000000000000000000000000";
    LOG.info("Original extraData: {}", shortExtraData);
    LOG.info("Original extraData length: {} bytes", (shortExtraData.length() - 2) / 2);

    Bytes fixedExtraData =
        GenesisFileConverter.fixCliqueExtraData(Bytes.fromHexString(shortExtraData));

    String fixedExtraDataString = "0x" + fixedExtraData.toHexString();
    LOG.info("Fixed extraData: {}", fixedExtraDataString);
    LOG.info("Fixed extraData length: {} bytes", fixedExtraData.size());

    assertEquals(194, fixedExtraData.size(), "ExtraData should be 194 bytes");
    assertTrue(fixedExtraDataString.startsWith("0x"), "ExtraData should start with '0x'");
  }

  @Test
  void shouldFixLongCliqueExtraData() {
    String longExtraData = "0x" + "00".repeat(200); // 200 bytes, which is longer than 194
    LOG.info("Original extraData length: {} bytes", (longExtraData.length() - 2) / 2);

    Bytes fixedExtraData =
        GenesisFileConverter.fixCliqueExtraData(Bytes.fromHexString(longExtraData));

    String fixedExtraDataString = "0x" + fixedExtraData.toHexString();
    LOG.info("Fixed extraData length: {} bytes", fixedExtraData.size());

    assertEquals(194, fixedExtraData.size(), "ExtraData should be 194 bytes");
    assertTrue(fixedExtraDataString.startsWith("0x"), "ExtraData should start with '0x'");
  }

  @Test
  void shouldPreserveValidatorAddressesInCliqueExtraData() {
    LOG.info("Starting shouldPreserveValidatorAddressesInCliqueExtraData test");
    String validatorAddress = "1122334455667788990011223344556677889900";
    String extraData =
        "0x000000000000000000000000000000000000000000000000000000000000" + validatorAddress;

    LOG.debug("Original extraData: {}", extraData);
    LOG.debug("Original extraData length: {} bytes", Bytes.fromHexString(extraData).size());
    LOG.debug("Expected validator address: {}", validatorAddress);

    Bytes fixedExtraData = GenesisFileConverter.fixCliqueExtraData(Bytes.fromHexString(extraData));

    LOG.debug("Fixed extraData: {}", fixedExtraData.toHexString());
    LOG.debug("Fixed extraData length: {} bytes", fixedExtraData.size());

    // Check if the validator address is preserved
    Bytes extractedValidator = RLP.decode(fixedExtraData.slice(30), RLPReader::readValue);
    String extractedValidatorString =
        extractedValidator.toHexString().toLowerCase(Locale.ROOT).replaceAll("^0x", "");

    boolean validatorPreserved = extractedValidatorString.equalsIgnoreCase(validatorAddress);
    LOG.debug("Extracted validator address: {}", extractedValidatorString);
    LOG.debug("Validator address preserved: {}", validatorPreserved);

    // Check if the vanity data is preserved
    Bytes originalVanity = Bytes.fromHexString(extraData).slice(0, 30);
    Bytes fixedVanity = fixedExtraData.slice(0, 30);
    boolean vanityPreserved = fixedVanity.equals(originalVanity);
    LOG.debug("Original vanity data: {}", originalVanity.toHexString());
    LOG.debug("Fixed vanity data: {}", fixedVanity.toHexString());
    LOG.debug("Vanity data preserved: {}", vanityPreserved);

    LOG.info("Asserting validator address preservation");
    assertTrue(validatorPreserved, "Validator address should be preserved");
    LOG.info("Asserting vanity data preservation");
    assertTrue(vanityPreserved, "Vanity data should be preserved");
    LOG.info("Asserting extraData length");
    assertEquals(194, fixedExtraData.size(), "ExtraData should be 194 bytes");
    LOG.info("Completed shouldPreserveValidatorAddressesInCliqueExtraData test");
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

  // Helper methods to create different types of genesis configurations
  private static ObjectNode createBesuIbft2Genesis() {
    ObjectNode genesis = JSON.objectNode();
    ObjectNode config = genesis.putObject("config");
    config.putObject("ibft2");
    config.put("chainId", 1); // Mainnet
    return genesis;
  }
}
