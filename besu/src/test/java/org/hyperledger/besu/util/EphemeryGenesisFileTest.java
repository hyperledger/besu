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
package org.hyperledger.besu.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.hyperledger.besu.cli.config.NetworkName.EPHEMERY;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.GenesisConfigOptions;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EphemeryGenesisFileTest {
  private static ObjectMapper objectMapper;
  private static Path tempJsonFile;
  private GenesisConfigFile genesisConfigFile;
  private GenesisConfigOptions genesisConfigOptions;
  private EphemeryGenesisFile ephemeryGenesisFile;
  private static final int GENESIS_CONFIG_TEST_CHAINID = 39438135;
  private static final long GENESIS_TEST_TIMESTAMP = 1720119600;
  private static final long CURRENT_TIMESTAMP = 1712041200;
  private static final long CURRENT_TIMESTAMP_HIGHER = 1922041200;
  private static final long PERIOD_IN_SECONDS = 28 * 24 * 60 * 60;
  private static final long PERIOD_SINCE_GENESIS = 3;

  private static final JsonObject VALID_GENESIS_JSON =
      (new JsonObject())
          .put("config", (new JsonObject()).put("chainId", GENESIS_CONFIG_TEST_CHAINID))
          .put("timestamp", GENESIS_TEST_TIMESTAMP);

  private static final JsonObject INVALID_GENESIS_VALID_JSON = new JsonObject();
  private static final JsonObject INVALID_GENESIS_JSON_WITHOUT_CHAINID =
      (new JsonObject()).put("timestamp", GENESIS_TEST_TIMESTAMP);

  private static final JsonObject INVALID_GENESIS_JSON_WITHOUT_TIMESTAMP =
      new JsonObject()
          .put("config", (new JsonObject()).put("chainId", GENESIS_CONFIG_TEST_CHAINID));

  @BeforeEach
  void setUp() throws IOException {
    objectMapper = new ObjectMapper();
    genesisConfigFile = mock(GenesisConfigFile.class);
    genesisConfigOptions = mock(GenesisConfigOptions.class);
    ephemeryGenesisFile = new EphemeryGenesisFile(genesisConfigFile, genesisConfigOptions);
    tempJsonFile = Files.createTempFile("temp-ephemery", ".json");
  }

  @Test
  public void testEphemeryWhenChainIdIsAbsent() throws IOException {
    Map<String, ObjectNode> node =
        createGenesisFile(tempJsonFile, INVALID_GENESIS_JSON_WITHOUT_CHAINID);
    ObjectNode configNode = node.get("configNode");
    assertThat(configNode.has("chainId")).isFalse();
    assertThat(configNode.get("chainId")).isNull();
    Files.delete(tempJsonFile);
  }

  @Test
  public void testEphemeryWhenTimestampIsAbsent() throws IOException {
    Map<String, ObjectNode> node =
        createGenesisFile(tempJsonFile, INVALID_GENESIS_JSON_WITHOUT_TIMESTAMP);
    ObjectNode rootNode = node.get("rootNode");
    assertThat(rootNode.has("timestamp")).isFalse();
    assertThat(rootNode.get("timestamp")).isNull();
    Files.delete(tempJsonFile);
  }

  @Test
  public void testEphemeryWhenGenesisJsonIsInvalid() throws IOException {
    Map<String, ObjectNode> node = createGenesisFile(tempJsonFile, INVALID_GENESIS_VALID_JSON);
    ObjectNode rootNode = node.get("rootNode");
    ObjectNode configNode = node.get("configNode");
    assertThat(rootNode.has("timestamp")).isFalse();
    assertThat(configNode.has("chainId")).isFalse();
    assertThat(rootNode.get("timestamp")).isNull();
    assertThat(configNode.get("chainId")).isNull();
    Files.delete(tempJsonFile);
  }

  @Test
  public void testEphemeryWhenGenesisJsonIsValid() throws IOException {
    Map<String, ObjectNode> node = createGenesisFile(tempJsonFile, VALID_GENESIS_JSON);
    ObjectNode rootNode = node.get("rootNode");
    ObjectNode configNode = node.get("configNode");
    assertThat(rootNode.has("timestamp")).isTrue();
    assertThat(configNode.has("chainId")).isTrue();
    assertThat(rootNode.get("timestamp")).isNotNull();
    assertThat(configNode.get("chainId")).isNotNull();
    Files.delete(tempJsonFile);
  }

  @Test
  public void testEphemeryNotYetDueForUpdate() throws IOException {
    Map<String, ObjectNode> node = createGenesisFile(tempJsonFile, VALID_GENESIS_JSON);
    ObjectNode rootNode = node.get("rootNode");
    assertThat(CURRENT_TIMESTAMP)
        .isLessThan(rootNode.get("timestamp").asLong() + PERIOD_IN_SECONDS);
    objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempJsonFile.toFile(), rootNode);
    Files.delete(tempJsonFile);
  }

  @Test
  public void testEphemeryWhenSuccessful() throws IOException {
    Map<String, ObjectNode> node = createGenesisFile(tempJsonFile, VALID_GENESIS_JSON);
    ObjectNode rootNode = node.get("rootNode");
    ObjectNode configNode = node.get("configNode");

    BigInteger expectedChainId =
        BigInteger.valueOf(GENESIS_CONFIG_TEST_CHAINID)
            .add(BigInteger.valueOf(PERIOD_SINCE_GENESIS));

    long expectedGenesisTimestamp =
        GENESIS_TEST_TIMESTAMP + (PERIOD_SINCE_GENESIS * PERIOD_IN_SECONDS);
    EPHEMERY.setNetworkId(expectedChainId, EPHEMERY);

    configNode.put("chainId", expectedChainId);
    rootNode.put("timestamp", String.valueOf(expectedGenesisTimestamp));

    assertThat(CURRENT_TIMESTAMP_HIGHER)
        .isGreaterThan(rootNode.get("timestamp").asLong() + PERIOD_IN_SECONDS);
    assertThat(tempJsonFile).exists();
    assertThat(rootNode.get("timestamp").asLong()).isEqualTo(expectedGenesisTimestamp);
    assertThat(configNode.get("chainId").toString()).isEqualTo(expectedChainId.toString());
    Files.delete(tempJsonFile);
  }

  @Test
  public void testEphemeryWhenChainIdAndTimestampIsPresent() {
    when(genesisConfigFile.getTimestamp()).thenReturn(GENESIS_TEST_TIMESTAMP);
    when(genesisConfigOptions.getChainId())
        .thenReturn(Optional.of(BigInteger.valueOf(GENESIS_CONFIG_TEST_CHAINID)));
    ephemeryGenesisFile.updateGenesis();
    verify(genesisConfigFile).getTimestamp();
    verify(genesisConfigOptions).getChainId();
  }

  @Test
  public void testEphemeryThrowIOException() {
    EphemeryGenesisFile spyEphemeryGenesisFile = spy(ephemeryGenesisFile);

    doThrow(new RuntimeException("Unable to update ephemery genesis file."))
        .when(spyEphemeryGenesisFile)
        .updateGenesis();

    assertThatThrownBy(spyEphemeryGenesisFile::updateGenesis)
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Unable to update ephemery genesis file.");
    verify(spyEphemeryGenesisFile).updateGenesis();
  }

  public Map<String, ObjectNode> createGenesisFile(
      final Path tempJsonFile, final JsonObject genesisData) throws IOException {
    Files.write(tempJsonFile, genesisData.encodePrettily().getBytes(UTF_8));

    ObjectMapper objectMapper = new ObjectMapper();
    ObjectNode rootNode = (ObjectNode) objectMapper.readTree(tempJsonFile.toFile());

    ObjectNode configNode =
        rootNode.has("config")
            ? (ObjectNode) rootNode.path("config")
            : objectMapper.createObjectNode();
    Map<String, ObjectNode> nodes = new HashMap<>();
    nodes.put("rootNode", rootNode);
    nodes.put("configNode", configNode);

    return nodes;
  }
}
