package org.hyperledger.besu.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GenesisFileHandlingTest {

    private static final Logger LOG = LoggerFactory.getLogger(GenesisFileHandlingTest.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldConvertGethStyleGenesisToBesuStyle() throws IOException {
        String gethGenesisJson = """
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

        assertEquals(15, besuGenesis.get("config").get("chainId").asInt(),
                "Expected chainId: 15, Actual: " + besuGenesis.get("config").get("chainId").asInt());
        assertTrue(besuGenesis.get("config").has("ethash"),
                "Expected 'ethash' in config, Actual: " + besuGenesis.get("config").toString());
        assertEquals("0x400", besuGenesis.get("difficulty").asText(),
                "Expected difficulty: 0x400, Actual: " + besuGenesis.get("difficulty").asText());
        assertEquals("0x8000000", besuGenesis.get("gasLimit").asText(),
                "Expected gasLimit: 0x8000000, Actual: " + besuGenesis.get("gasLimit").asText());
        assertTrue(besuGenesis.has("alloc"),
                "Expected 'alloc' in Besu genesis, Actual keys: " + besuGenesis.fieldNames());
    }

    @Test
    void shouldConvertGethStyleCliqueGenesisToBesuStyle() throws IOException {
        String gethCliqueGenesisJson = """
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

        assertTrue(besuGenesis.get("config").has("clique"),
                "Expected 'clique' in config, Actual: " + besuGenesis.get("config").toString());
        assertEquals("0x1", besuGenesis.get("difficulty").asText(),
                "Expected difficulty: 0x1, Actual: " + besuGenesis.get("difficulty").asText());
        assertTrue(besuGenesis.has("extraData"),
                "Expected 'extraData' in Besu genesis, Actual keys: " + besuGenesis.fieldNames());

        String gethExtraData = gethGenesis.get("extradata").asText();
        String besuExtraData = besuGenesis.get("extraData").asText();

        LOG.info("Geth extraData: {}", gethExtraData);
        LOG.info("Geth extraData length: {} bytes", (gethExtraData.length() - 2) / 2);
        LOG.info("Besu extraData: {}", besuExtraData);
        LOG.info("Besu extraData length: {} bytes", (besuExtraData.length() - 2) / 2);

        // Check that the Besu extraData starts with the same 32 bytes of vanity data
        assertTrue(besuExtraData.substring(2, 66).equals(gethExtraData.substring(2, 66)),
                "Besu extraData should start with the same vanity data as Geth");

        // Check that the Besu extraData contains the validator address
        assertTrue(besuExtraData.contains(gethExtraData.substring(66, 106)),
                "Besu extraData should contain the validator address");

        // Check that the Besu extraData is exactly 194 bytes (388 hex characters + 2 for "0x")
        assertEquals(390, besuExtraData.length(),
                "Besu extraData should be exactly 194 bytes (390 hex characters including '0x')");
    }

    @Test
    void shouldHandleIncompleteGethGenesisFile() throws IOException {
        String incompleteGethGenesisJson = """
        {
          "config": {
            "chainId": 15
          },
          "difficulty": "0x400"
        }
        """;
        ObjectNode incompleteGethGenesis = (ObjectNode) objectMapper.readTree(incompleteGethGenesisJson);

        ObjectNode besuGenesis = GenesisFileConverter.convertGethToBesu(incompleteGethGenesis);

        LOG.info("Incomplete Geth Genesis: {}", incompleteGethGenesis);
        LOG.info("Converted Besu Genesis: {}", besuGenesis);

        assertEquals(15, besuGenesis.get("config").get("chainId").asInt(),
                "Expected chainId: 15, Actual: " + besuGenesis.get("config").get("chainId").asInt());
        assertEquals("0x400", besuGenesis.get("difficulty").asText(),
                "Expected difficulty: 0x400, Actual: " + besuGenesis.get("difficulty").asText());
        assertTrue(besuGenesis.get("config").has("ethash"),
                "Expected 'ethash' in config, Actual: " + besuGenesis.get("config").toString());
    }

    @Test
    void shouldDetectAndHandleGethFileStyle() throws IOException {
        String gethGenesisJson = """
        {
          "config": {
            "chainId": 15
          },
          "difficulty": "0x400",
          "gasLimit": "0x8000000",
          "alloc": {}
        }
        """;
        ObjectNode gethGenesis = (ObjectNode) objectMapper.readTree(gethGenesisJson);

        GenesisFileAnalyzer.GenesisAnalysis analysis = GenesisFileAnalyzer.analyzeGenesisFile(gethGenesis);

        LOG.info("Geth Genesis for Analysis: {}", gethGenesis);
        LOG.info("Analysis Result: {}", analysis);

        assertEquals(GenesisFileAnalyzer.GenesisAnalysis.FileStyle.GETH, analysis.fileStyle(),
                "Expected FileStyle: GETH, Actual: " + analysis.fileStyle());
        assertEquals(GenesisFileAnalyzer.GenesisAnalysis.ConsensusMechanism.ETHASH, analysis.consensusMechanism(),
                "Expected ConsensusMechanism: ETHASH, Actual: " + analysis.consensusMechanism());
    }

    @Test
    void shouldHandleEdgeCasesInGethToBesuConversion() throws IOException {
        String edgeCaseGethGenesisJson = """
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

        assertEquals(15, besuGenesis.get("config").get("chainId").asInt(),
                "Expected chainId: 15, Actual: " + besuGenesis.get("config").get("chainId").asInt());
        assertEquals(0, besuGenesis.get("config").get("homesteadBlock").asInt(),
                "Expected homesteadBlock: 0, Actual: " + besuGenesis.get("config").get("homesteadBlock").asInt());
        assertEquals("0x20000", besuGenesis.get("difficulty").asText(),
                "Expected difficulty: 0x20000, Actual: " + besuGenesis.get("difficulty").asText());
        assertEquals("0x2fefd8", besuGenesis.get("gasLimit").asText(),
                "Expected gasLimit: 0x2fefd8, Actual: " + besuGenesis.get("gasLimit").asText());
        assertTrue(besuGenesis.get("alloc").has("0000000000000000000000000000000000000001"),
                "Expected address 0000000000000000000000000000000000000001 in alloc, Actual: " + besuGenesis.get("alloc").toString());
        assertTrue(besuGenesis.get("alloc").has("0000000000000000000000000000000000000002"),
                "Expected address 0000000000000000000000000000000000000002 in alloc, Actual: " + besuGenesis.get("alloc").toString());
    }

    @Test
    void shouldFixCliqueExtraData() {
        String originalExtraData = "0x00000000000000000000000000000000000000000000000000000000000000";
        LOG.info("Original extraData: {}", originalExtraData);
        LOG.info("Original extraData length: {} bytes", (originalExtraData.length() - 2) / 2);

        Bytes fixedExtraData = GenesisFileConverter.fixCliqueExtraData(Bytes.fromHexString(originalExtraData));

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

        Bytes fixedExtraData = GenesisFileConverter.fixCliqueExtraData(Bytes.fromHexString(shortExtraData));

        String fixedExtraDataString = "0x" + fixedExtraData.toHexString();
        LOG.info("Fixed extraData: {}", fixedExtraDataString);
        LOG.info("Fixed extraData length: {} bytes", fixedExtraData.size());

        assertEquals(194, fixedExtraData.size(), "ExtraData should be 194 bytes");
        assertTrue(fixedExtraDataString.startsWith("0x"), "ExtraData should start with '0x'");
    }

    @Test
    void shouldFixLongCliqueExtraData() {
        String longExtraData = "0x" + "00".repeat(200);  // 200 bytes, which is longer than 194
        LOG.info("Original extraData length: {} bytes", (longExtraData.length() - 2) / 2);

        Bytes fixedExtraData = GenesisFileConverter.fixCliqueExtraData(Bytes.fromHexString(longExtraData));

        String fixedExtraDataString = "0x" + fixedExtraData.toHexString();
        LOG.info("Fixed extraData length: {} bytes", fixedExtraData.size());

        assertEquals(194, fixedExtraData.size(), "ExtraData should be 194 bytes");
        assertTrue(fixedExtraDataString.startsWith("0x"), "ExtraData should start with '0x'");
    }

    @Test
    void shouldPreserveValidatorAddressesInCliqueExtraData() {
        String validatorAddress = "1122334455667788990011223344556677889900";
        String extraData = "0x" + "00".repeat(32) + validatorAddress;

        LOG.info("Original extraData: {}", extraData);

        Bytes fixedExtraData = GenesisFileConverter.fixCliqueExtraData(Bytes.fromHexString(extraData));

        String fixedExtraDataString = "0x" + fixedExtraData.toHexString();
        LOG.info("Fixed extraData: {}", fixedExtraDataString);

        assertEquals(194, fixedExtraData.size(), "ExtraData should be 194 bytes");
        assertTrue(fixedExtraDataString.startsWith("0x"), "ExtraData should start with '0x'");
        assertTrue(fixedExtraDataString.substring(2, 66).equals("0".repeat(64)), "Vanity data should be preserved");
        assertTrue(fixedExtraDataString.contains(validatorAddress), "ExtraData should preserve validator address");

        // Additional debug information
        LOG.info("Validator address position: {}", fixedExtraDataString.indexOf(validatorAddress));
        LOG.info("Fixed extraData length: {} bytes", fixedExtraData.size());
    }
}