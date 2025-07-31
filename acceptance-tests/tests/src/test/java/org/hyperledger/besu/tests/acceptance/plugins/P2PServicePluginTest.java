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
package org.hyperledger.besu.tests.acceptance.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.tests.acceptance.dsl.AcceptanceTestBase;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.genesis.GenesisConfigurationFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class P2PServicePluginTest extends AcceptanceTestBase {

  // Single comprehensive test setup with multiple nodes
  private BesuNode minerNode;
  private BesuNode basicPluginNode;
  private BesuNode comprehensivePluginNode;
  private BesuNode validatorPluginNode;
  private BesuNode protocolPluginNode;
  private BesuNode additionalNode1;
  private BesuNode additionalNode2;

  @BeforeEach
  public void setUp() throws Exception {
    // Create all nodes once for all tests
    minerNode =
        besu.createQbftNode(
            "minerNode",
            b ->
                b.genesisConfigProvider(
                    GenesisConfigurationFactory::createQbftLondonGenesisConfig));

    // Basic plugin node - minimal functionality
    basicPluginNode =
        besu.createQbftPluginsNode(
            "basicPluginNode",
            Collections.singletonList("testPlugins"),
            List.of("--plugin-p2p-test-enabled=true", "--plugin-p2p-test-mode=basic"),
            "DEBUG");

    // Comprehensive plugin node - all functionality
    comprehensivePluginNode =
        besu.createQbftPluginsNode(
            "comprehensivePluginNode",
            Collections.singletonList("testPlugins"),
            List.of("--plugin-p2p-test-enabled=true", "--plugin-p2p-test-mode=comprehensive"),
            "DEBUG");

    // Validator-focused plugin node
    validatorPluginNode =
        besu.createQbftPluginsNode(
            "validatorPluginNode",
            Collections.singletonList("testPlugins"),
            List.of("--plugin-p2p-test-enabled=true", "--plugin-p2p-test-mode=validator"),
            "DEBUG");

    // Protocol-focused plugin node
    protocolPluginNode =
        besu.createQbftPluginsNode(
            "protocolPluginNode",
            Collections.singletonList("testPlugins"),
            List.of("--plugin-p2p-test-enabled=true", "--plugin-p2p-test-mode=protocol"),
            "DEBUG");

    // Additional nodes for multi-node testing
    additionalNode1 =
        besu.createQbftNode(
            "additionalNode1",
            b ->
                b.genesisConfigProvider(
                    GenesisConfigurationFactory::createQbftLondonGenesisConfig));

    additionalNode2 =
        besu.createQbftNode(
            "additionalNode2",
            b ->
                b.genesisConfigProvider(
                    GenesisConfigurationFactory::createQbftLondonGenesisConfig));

    // Start all nodes
    cluster.start(
        minerNode,
        basicPluginNode,
        comprehensivePluginNode,
        validatorPluginNode,
        protocolPluginNode,
        additionalNode1,
        additionalNode2);
  }

  @Test
  public void basicP2PServiceFunctionality() throws IOException, InterruptedException {
    final Path p2pStatusFile = basicPluginNode.homeDirectory().resolve("plugins/p2p_status.txt");
    waitForFile(p2pStatusFile);

    // Wait for basic functionality to initialize
    Thread.sleep(15000);

    final var fileContents = Files.readAllLines(p2pStatusFile);

    // Verify service started
    assertThat(fileContents.stream().anyMatch(line -> line.contains("P2P_SERVICE_STARTED")))
        .isTrue();

    // Check basic P2P functionality
    assertThat(fileContents.stream().anyMatch(line -> line.contains("DISCOVERY_ENABLED"))).isTrue();

    assertThat(fileContents.stream().anyMatch(line -> line.contains("PROTOCOL_REGISTERED")))
        .isTrue();

    assertThat(fileContents.stream().anyMatch(line -> line.contains("MESSAGE_HANDLER_REGISTERED")))
        .isTrue();

    assertThat(
            fileContents.stream().anyMatch(line -> line.contains("VALIDATOR_TRACKER_REGISTERED")))
        .isTrue();

    // Check that test messages are sent in basic mode
    assertThat(
            fileContents.stream()
                .anyMatch(line -> line.contains("MESSAGE_SENT") || line.contains("NO_PEERS")))
        .isTrue();

    // Verify periodic monitoring works
    assertThat(fileContents.stream().anyMatch(line -> line.contains("CAPABILITIES"))).isTrue();

    assertThat(fileContents.stream().anyMatch(line -> line.contains("PEER_COUNT"))).isTrue();
  }

  @Test
  public void comprehensiveP2PTesting() throws IOException, InterruptedException {
    final Path p2pStatusFile =
        comprehensivePluginNode.homeDirectory().resolve("plugins/p2p_status.txt");
    waitForFile(p2pStatusFile);

    // Wait for comprehensive tests to complete (all tests run in parallel)
    Thread.sleep(60000);

    final var fileContents = Files.readAllLines(p2pStatusFile);

    // Verify service started
    assertThat(fileContents.stream().anyMatch(line -> line.contains("P2P_SERVICE_STARTED")))
        .isTrue();

    // Check protocol management features
    assertThat(
            fileContents.stream().anyMatch(line -> line.contains("SECONDARY_PROTOCOL_REGISTERED")))
        .isTrue();

    assertThat(fileContents.stream().anyMatch(line -> line.contains("PROTOCOL_UNREGISTERED")))
        .isTrue();

    assertThat(fileContents.stream().anyMatch(line -> line.contains("PROTOCOL_RE_REGISTERED")))
        .isTrue();

    // Check validator functionality
    assertThat(fileContents.stream().anyMatch(line -> line.contains("VALIDATOR_ADDRESSES")))
        .isTrue();

    assertThat(fileContents.stream().anyMatch(line -> line.contains("VALIDATOR_MESSAGE_SENT")))
        .isTrue();

    // Check connection tracking
    assertThat(fileContents.stream().anyMatch(line -> line.contains("VALIDATOR_CONNECTION_STATE")))
        .isTrue();

    // Verify no excessive errors
    final long errorCount = fileContents.stream().filter(line -> line.contains("ERROR")).count();

    assertThat(errorCount)
        .as("Should not have excessive errors in comprehensive testing")
        .isLessThan(5);
  }

  @Test
  public void validatorNetworkTesting() throws IOException, InterruptedException {
    final Path p2pStatusFile =
        validatorPluginNode.homeDirectory().resolve("plugins/p2p_status.txt");
    waitForFile(p2pStatusFile);

    // Wait for validator tests to complete
    Thread.sleep(35000);

    final var fileContents = Files.readAllLines(p2pStatusFile);

    // Verify service started
    assertThat(fileContents.stream().anyMatch(line -> line.contains("P2P_SERVICE_STARTED")))
        .isTrue();

    // Check validator identification
    assertThat(fileContents.stream().anyMatch(line -> line.contains("VALIDATOR_ADDRESSES")))
        .isTrue();

    assertThat(fileContents.stream().anyMatch(line -> line.contains("VALIDATOR_ADDRESS_DETAIL")))
        .isTrue();

    // Check validator messaging
    assertThat(fileContents.stream().anyMatch(line -> line.contains("VALIDATOR_MESSAGE_SENT")))
        .isTrue();

    // Check connection tracking
    assertThat(fileContents.stream().anyMatch(line -> line.contains("VALIDATOR_CONNECTION_STATE")))
        .isTrue();

    assertThat(fileContents.stream().anyMatch(line -> line.contains("VALIDATOR_PEER_COUNT")))
        .isTrue();
  }

  @Test
  public void protocolManagementTesting() throws IOException, InterruptedException {
    final Path p2pStatusFile = protocolPluginNode.homeDirectory().resolve("plugins/p2p_status.txt");
    waitForFile(p2pStatusFile);

    // Wait for protocol tests to complete
    Thread.sleep(35000);

    final var fileContents = Files.readAllLines(p2pStatusFile);

    // Verify service started
    assertThat(fileContents.stream().anyMatch(line -> line.contains("P2P_SERVICE_STARTED")))
        .isTrue();

    // Check multiple protocol registration
    assertThat(
            fileContents.stream().anyMatch(line -> line.contains("SECONDARY_PROTOCOL_REGISTERED")))
        .isTrue();

    assertThat(
            fileContents.stream().anyMatch(line -> line.contains("SECONDARY_HANDLER_REGISTERED")))
        .isTrue();

    // Check protocol unregistration
    assertThat(fileContents.stream().anyMatch(line -> line.contains("PROTOCOL_UNREGISTERED")))
        .isTrue();

    assertThat(fileContents.stream().anyMatch(line -> line.contains("PROTOCOL_RE_REGISTERED")))
        .isTrue();

    // Check protocol tracking
    assertThat(fileContents.stream().anyMatch(line -> line.contains("MULTIPLE_PROTOCOLS_CHECK")))
        .isTrue();

    assertThat(
            fileContents.stream().anyMatch(line -> line.contains("PROTOCOL_REGISTRATION_STATUS")))
        .isTrue();

    // Verify protocol managers were created
    assertThat(fileContents.stream().anyMatch(line -> line.contains("PROTOCOL_MANAGER_CREATED")))
        .isTrue();
  }

  @Test
  public void multiNodeP2PCommunication() throws IOException, InterruptedException {
    // Test communication between multiple plugin nodes
    final Path basicStatusFile = basicPluginNode.homeDirectory().resolve("plugins/p2p_status.txt");
    final Path comprehensiveStatusFile =
        comprehensivePluginNode.homeDirectory().resolve("plugins/p2p_status.txt");

    waitForFile(basicStatusFile);
    waitForFile(comprehensiveStatusFile);

    // Wait for nodes to discover each other and establish connections
    Thread.sleep(25000);

    final var basicContents = Files.readAllLines(basicStatusFile);
    final var comprehensiveContents = Files.readAllLines(comprehensiveStatusFile);

    // Both nodes should start successfully
    assertThat(basicContents.stream().anyMatch(line -> line.contains("P2P_SERVICE_STARTED")))
        .isTrue();
    assertThat(
            comprehensiveContents.stream().anyMatch(line -> line.contains("P2P_SERVICE_STARTED")))
        .isTrue();

    // At least one node should have peer connections
    final boolean basicHasPeers =
        basicContents.stream()
            .anyMatch(line -> line.contains("PEER_COUNT") && !line.contains("Connected peers: 0"));
    final boolean comprehensiveHasPeers =
        comprehensiveContents.stream()
            .anyMatch(line -> line.contains("PEER_COUNT") && !line.contains("Connected peers: 0"));

    assertThat(basicHasPeers || comprehensiveHasPeers)
        .as("At least one node should have peer connections")
        .isTrue();

    // Check that both nodes have basic P2P functionality working
    assertThat(basicContents.stream().anyMatch(line -> line.contains("DISCOVERY_ENABLED")))
        .isTrue();
    assertThat(comprehensiveContents.stream().anyMatch(line -> line.contains("DISCOVERY_ENABLED")))
        .isTrue();

    // Verify no excessive errors across nodes
    final long basicErrorCount =
        basicContents.stream().filter(line -> line.contains("ERROR")).count();
    final long comprehensiveErrorCount =
        comprehensiveContents.stream().filter(line -> line.contains("ERROR")).count();

    assertThat(basicErrorCount + comprehensiveErrorCount)
        .as("Should not have excessive errors across nodes")
        .isLessThan(10);
  }

  @Test
  public void pluginResilienceAndErrorHandling() throws IOException, InterruptedException {
    // Test that plugins handle errors gracefully and continue functioning
    final Path p2pStatusFile =
        comprehensivePluginNode.homeDirectory().resolve("plugins/p2p_status.txt");
    waitForFile(p2pStatusFile);

    // Wait for comprehensive testing to complete
    Thread.sleep(45000);

    final var fileContents = Files.readAllLines(p2pStatusFile);

    // Service should start and remain functional
    assertThat(fileContents.stream().anyMatch(line -> line.contains("P2P_SERVICE_STARTED")))
        .isTrue();

    // Check that periodic monitoring continues throughout all tests
    assertThat(fileContents.stream().anyMatch(line -> line.contains("CAPABILITIES"))).isTrue();

    assertThat(fileContents.stream().anyMatch(line -> line.contains("PEER_COUNT"))).isTrue();

    // Verify error handling - should not have excessive errors
    final long errorCount = fileContents.stream().filter(line -> line.contains("ERROR")).count();

    assertThat(errorCount).as("Should not have excessive errors").isLessThan(10);

    // Check that all major test categories ran successfully
    final boolean hasProtocolTests =
        fileContents.stream()
            .anyMatch(
                line ->
                    line.contains("SECONDARY_PROTOCOL_REGISTERED")
                        || line.contains("PROTOCOL_UNREGISTERED"));

    final boolean hasValidatorTests =
        fileContents.stream()
            .anyMatch(
                line ->
                    line.contains("VALIDATOR_ADDRESSES")
                        || line.contains("VALIDATOR_MESSAGE_SENT"));

    final boolean hasConnectionTests =
        fileContents.stream()
            .anyMatch(
                line ->
                    line.contains("VALIDATOR_CONNECTION_STATE")
                        || line.contains("VALIDATOR_PEER_COUNT"));

    // At minimum, basic functionality should work
    assertThat(hasProtocolTests || hasValidatorTests || hasConnectionTests)
        .as("At least some test categories should have run successfully")
        .isTrue();
  }
}
