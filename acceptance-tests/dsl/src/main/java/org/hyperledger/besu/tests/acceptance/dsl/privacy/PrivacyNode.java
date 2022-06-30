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
package org.hyperledger.besu.tests.acceptance.dsl.privacy;

import static org.hyperledger.besu.controller.BesuController.DATABASE_PATH;

import org.hyperledger.besu.crypto.KeyPairUtil;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.enclave.EnclaveIOException;
import org.hyperledger.besu.enclave.EnclaveServerException;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValuePrivacyStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.tests.acceptance.dsl.condition.Condition;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNode;
import org.hyperledger.besu.tests.acceptance.dsl.node.BesuNodeRunner;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.BesuNodeConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.NodeConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.node.configuration.privacy.PrivacyNodeConfiguration;
import org.hyperledger.besu.tests.acceptance.dsl.privacy.condition.PrivateCondition;
import org.hyperledger.besu.tests.acceptance.dsl.transaction.Transaction;
import org.hyperledger.enclave.testutil.EnclaveTestHarness;
import org.hyperledger.enclave.testutil.EnclaveType;
import org.hyperledger.enclave.testutil.NoopEnclaveTestHarness;
import org.hyperledger.enclave.testutil.TesseraTestHarnessFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;

public class PrivacyNode implements AutoCloseable {

  private static final Logger LOG = LoggerFactory.getLogger(PrivacyNode.class);
  private static final int MAX_OPEN_FILES = 1024;
  private static final long CACHE_CAPACITY = 8388608;
  private static final int MAX_BACKGROUND_COMPACTIONS = 4;
  private static final int BACKGROUND_THREAD_COUNT = 4;

  private final EnclaveTestHarness enclave;
  private final BesuNode besu;
  private final Vertx vertx;
  private final boolean isFlexiblePrivacyEnabled;
  private final boolean isMultitenancyEnabled;
  private final boolean isPrivacyPluginEnabled;

  public PrivacyNode(
      final PrivacyNodeConfiguration privacyConfiguration,
      final Vertx vertx,
      final EnclaveType enclaveType,
      final Optional<Network> containerNetwork)
      throws IOException {
    final Path enclaveDir = Files.createTempDirectory("acctest-orion");
    final BesuNodeConfiguration config = privacyConfiguration.getBesuConfig();
    this.enclave =
        selectEnclave(enclaveType, enclaveDir, config, privacyConfiguration, containerNetwork);
    this.vertx = vertx;

    final BesuNodeConfiguration besuConfig = config;

    isFlexiblePrivacyEnabled = privacyConfiguration.isFlexiblePrivacyGroupEnabled();
    isMultitenancyEnabled = privacyConfiguration.isMultitenancyEnabled();
    isPrivacyPluginEnabled = privacyConfiguration.isPrivacyPluginEnabled();

    this.besu =
        new BesuNode(
            besuConfig.getName(),
            besuConfig.getDataPath(),
            besuConfig.getMiningParameters(),
            besuConfig.getJsonRpcConfiguration(),
            besuConfig.getEngineRpcConfiguration(),
            besuConfig.getWebSocketConfiguration(),
            besuConfig.getJsonRpcIpcConfiguration(),
            besuConfig.getMetricsConfiguration(),
            besuConfig.getPermissioningConfiguration(),
            besuConfig.getKeyFilePath(),
            besuConfig.isDevMode(),
            besuConfig.getNetwork(),
            besuConfig.getGenesisConfigProvider(),
            besuConfig.isP2pEnabled(),
            besuConfig.getP2pPort(),
            besuConfig.getTLSConfiguration(),
            besuConfig.getNetworkingConfiguration(),
            besuConfig.isDiscoveryEnabled(),
            besuConfig.isBootnodeEligible(),
            besuConfig.isRevertReasonEnabled(),
            besuConfig.isSecp256k1Native(),
            besuConfig.isAltbn128Native(),
            besuConfig.getPlugins(),
            besuConfig.getExtraCLIOptions(),
            Collections.emptyList(),
            besuConfig.isDnsEnabled(),
            besuConfig.getPrivacyParameters(),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            besuConfig.isStrictTxReplayProtectionEnabled());
  }

  public void testEnclaveConnection(final List<PrivacyNode> otherNodes) {
    if (this.isPrivacyPluginEnabled) {
      LOG.info("Skipping test as node has no enclave (isPrivacyPluginEnabled=true)");
      return;
    }

    if (!otherNodes.isEmpty()) {
      LOG.debug(
          String.format(
              "Testing Enclave connectivity between %s (%s) and %s (%s)",
              besu.getName(),
              enclave.nodeUrl(),
              Arrays.toString(otherNodes.stream().map(node -> node.besu.getName()).toArray()),
              Arrays.toString(otherNodes.stream().map(node -> node.enclave.nodeUrl()).toArray())));
      final EnclaveFactory factory = new EnclaveFactory(vertx);
      final Enclave enclaveClient = factory.createVertxEnclave(enclave.clientUrl());
      final String payload = "SGVsbG8sIFdvcmxkIQ==";
      final List<String> to =
          otherNodes.stream()
              .map(node -> node.enclave.getDefaultPublicKey())
              .collect(Collectors.toList());

      Awaitility.await()
          .until(
              () -> {
                try {
                  enclaveClient.send(payload, enclave.getDefaultPublicKey(), to);
                  return true;
                } catch (final EnclaveClientException
                    | EnclaveIOException
                    | EnclaveServerException e) {
                  LOG.warn(
                      "Waiting for enclave connectivity between {} and {}: " + e.getMessage(),
                      enclave.getDefaultPublicKey(),
                      to.get(0));
                  return false;
                }
              });
    }
  }

  public EnclaveTestHarness getEnclave() {
    return enclave;
  }

  public BesuNode getBesu() {
    return besu;
  }

  public void stop() {
    besu.stop();
    enclave.stop();
  }

  @Override
  public void close() {
    besu.close();
    enclave.close();
  }

  public void start(final BesuNodeRunner runner) {
    enclave.start();

    final PrivacyParameters privacyParameters;

    try {
      final Path dataDir = Files.createTempDirectory("acctest-privacy");
      final Path dbDir = dataDir.resolve(DATABASE_PATH);

      var builder =
          new PrivacyParameters.Builder()
              .setEnabled(true)
              .setEnclaveUrl(enclave.clientUrl())
              .setStorageProvider(createKeyValueStorageProvider(dataDir, dbDir))
              .setPrivateKeyPath(KeyPairUtil.getDefaultKeyFile(besu.homeDirectory()).toPath())
              .setEnclaveFactory(new EnclaveFactory(vertx))
              .setFlexiblePrivacyGroupsEnabled(isFlexiblePrivacyEnabled)
              .setMultiTenancyEnabled(isMultitenancyEnabled)
              .setPrivacyPluginEnabled(isPrivacyPluginEnabled);

      if (enclave.getPublicKeyPaths().size() > 0) {
        builder.setPrivacyUserIdUsingFile(enclave.getPublicKeyPaths().get(0).toFile());
      }

      privacyParameters = builder.build();

    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
    besu.setPrivacyParameters(privacyParameters);
    besu.start(runner);
  }

  public void awaitPeerDiscovery(final Condition condition) {
    besu.awaitPeerDiscovery(condition);
  }

  public String getName() {
    return besu.getName();
  }

  public Address getAddress() {
    return besu.getAddress();
  }

  public URI enodeUrl() {
    return besu.enodeUrl();
  }

  public String getNodeId() {
    return besu.getNodeId();
  }

  public <T> T execute(final Transaction<T> transaction) {
    return besu.execute(transaction);
  }

  public void verify(final PrivateCondition expected) {
    expected.verify(this);
  }

  public String getEnclaveKey() {
    return enclave.getDefaultPublicKey();
  }

  public String getTransactionSigningKey() {
    return besu.getPrivacyParameters().getSigningKeyPair().orElseThrow().getPrivateKey().toString();
  }

  public void addOtherEnclaveNode(final URI otherNode) {
    enclave.addOtherNode(otherNode);
  }

  public NodeConfiguration getConfiguration() {
    return besu.getConfiguration();
  }

  private PrivacyStorageProvider createKeyValueStorageProvider(
      final Path dataLocation, final Path dbLocation) {
    return new PrivacyKeyValueStorageProviderBuilder()
        .withStorageFactory(
            new RocksDBKeyValuePrivacyStorageFactory(
                new RocksDBKeyValueStorageFactory(
                    () ->
                        new RocksDBFactoryConfiguration(
                            MAX_OPEN_FILES,
                            MAX_BACKGROUND_COMPACTIONS,
                            BACKGROUND_THREAD_COUNT,
                            CACHE_CAPACITY),
                    Arrays.asList(KeyValueSegmentIdentifier.values()),
                    RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS)))
        .withCommonConfiguration(new BesuConfigurationImpl(dataLocation, dbLocation))
        .withMetricsSystem(new NoOpMetricsSystem())
        .build();
  }

  private EnclaveTestHarness selectEnclave(
      final EnclaveType enclaveType,
      final Path tempDir,
      final BesuNodeConfiguration config,
      final PrivacyNodeConfiguration privacyConfiguration,
      final Optional<Network> containerNetwork) {

    switch (enclaveType) {
      case ORION:
        throw new UnsupportedOperationException("The Orion tests are getting deprecated");
      case TESSERA:
        return TesseraTestHarnessFactory.create(
            config.getName(), tempDir, privacyConfiguration.getKeyConfig(), containerNetwork);
      default:
        return new NoopEnclaveTestHarness(tempDir, privacyConfiguration.getKeyConfig());
    }
  }
}
