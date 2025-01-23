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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.config.GenesisReader.ALLOCATION_FIELD;
import static org.hyperledger.besu.config.GenesisReader.CONFIG_FIELD;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GenesisReaderTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void readGenesisFromObjectNode() {
    final var configNode = mapper.createObjectNode();
    configNode.put("londonBlock", 1);
    final var allocNode = mapper.createObjectNode();
    allocNode.put(Address.BLS12_G2MULTIEXP.toUnprefixedHexString(), generateAllocation(Wei.ONE));
    final var rootNode = mapper.createObjectNode();
    rootNode.put("chainId", 12);
    rootNode.put(CONFIG_FIELD, configNode);
    rootNode.put(ALLOCATION_FIELD, allocNode);
    final var genesisReader = new GenesisReader.FromObjectNode(rootNode);

    assertThat(genesisReader.getRoot().get("chainid").asInt()).isEqualTo(12);
    assertThat(genesisReader.getRoot().has(ALLOCATION_FIELD)).isFalse();
    assertThat(genesisReader.getConfig().get("londonblock").asInt()).isEqualTo(1);
    assertThat(genesisReader.streamAllocations())
        .containsExactly(
            new GenesisAccount(Address.BLS12_G2MULTIEXP, 0, Wei.ONE, null, Map.of(), null));
  }

  @Test
  public void readGenesisFromObjectDoesNotModifyObjectNodeArg() {
    final var configNode = mapper.createObjectNode();
    configNode.put("londonBlock", 1);
    final var allocNode = mapper.createObjectNode();
    allocNode.put(Address.BLS12_G2MULTIEXP.toUnprefixedHexString(), generateAllocation(Wei.ONE));
    final var rootNode = mapper.createObjectNode();
    rootNode.put("chainId", 12);
    rootNode.put(CONFIG_FIELD, configNode);
    rootNode.put(ALLOCATION_FIELD, allocNode);
    var rootNodeCopy = rootNode.deepCopy();
    new GenesisReader.FromObjectNode(rootNode);

    assertThat(rootNode).isEqualTo(rootNodeCopy);
  }

  @Test
  public void readGenesisFromURL(@TempDir final Path folder) throws IOException {
    final String jsonStr =
        """
      {
        "chainId":11,
        "config": {
          "londonBlock":1
        },
        "alloc": {
          "000d836201318ec6899a67540690382780743280": {
            "balance": "0xad78ebc5ac6200000"
          }
        },
        "gasLimit": "0x1"
      }
      """;

    final var genesisFile = Files.writeString(folder.resolve("genesis.json"), jsonStr);

    final var genesisReader = new GenesisReader.FromURL(genesisFile.toUri().toURL());

    assertThat(genesisReader.getRoot().get("chainid").asInt()).isEqualTo(11);
    assertThat(genesisReader.getRoot().get("gaslimit").asText()).isEqualTo("0x1");
    assertThat(genesisReader.getRoot().has(ALLOCATION_FIELD)).isFalse();
    assertThat(genesisReader.getConfig().get("londonblock").asInt()).isEqualTo(1);
    assertThat(genesisReader.streamAllocations())
        .containsExactly(
            new GenesisAccount(
                Address.fromHexString("000d836201318ec6899a67540690382780743280"),
                0,
                Wei.fromHexString("0xad78ebc5ac6200000"),
                null,
                Map.of(),
                null));
  }

  private ObjectNode generateAllocation(final Wei balance) {
    final ObjectNode entry = mapper.createObjectNode();
    entry.put("balance", balance.toShortHexString());
    return entry;
  }

  @Test
  void testNonceHandlingAsStringAndInteger() {
    ObjectNode accountNode = JsonNodeFactory.instance.objectNode();

    accountNode.put("nonce", 10);
    String nonceAsStringFromInt = JsonUtil.getValueAsString(accountNode, "nonce").orElse("");
    assertEquals("10", nonceAsStringFromInt, "Nonce should convert integer to string correctly");

    accountNode.put("nonce", "20");
    String nonceAsStringDirect = JsonUtil.getValueAsString(accountNode, "nonce").orElse("");
    assertEquals("20", nonceAsStringDirect, "Nonce should keep string as string correctly");
  }
}
