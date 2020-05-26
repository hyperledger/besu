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
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveClientException;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.enclave.EnclaveIOException;
import org.hyperledger.besu.enclave.EnclaveServerException;
import org.hyperledger.besu.ethereum.core.Address;
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
import org.hyperledger.orion.testutil.OrionTestHarness;
import org.hyperledger.orion.testutil.OrionTestHarnessFactory;

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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.awaitility.Awaitility;

public class PrivacyNode implements AutoCloseable {

  private static final Logger LOG = LogManager.getLogger();
  private static final int MAX_OPEN_FILES = 1024;
  private static final long CACHE_CAPACITY = 8388608;
  private static final int MAX_BACKGROUND_COMPACTIONS = 4;
  private static final int BACKGROUND_THREAD_COUNT = 4;

  private final OrionTestHarness orion;
  private final BesuNode besu;
  private final Vertx vertx;
  private final Integer privacyAddress;
  private final boolean isOnchainPrivacyEnabled;

  public PrivacyNode(final PrivacyNodeConfiguration privacyConfiguration, final Vertx vertx)
      throws IOException {
    final Path orionDir = Files.createTempDirectory("acctest-orion");
    this.orion = OrionTestHarnessFactory.create(orionDir, privacyConfiguration.getOrionKeyConfig());
    this.vertx = vertx;

    final BesuNodeConfiguration besuConfig = privacyConfiguration.getBesuConfig();

    privacyAddress = privacyConfiguration.getPrivacyAddress();
    isOnchainPrivacyEnabled = privacyConfiguration.isOnchainPrivacyGroupEnabled();

    this.besu =
        new BesuNode(
            besuConfig.getName(),
            besuConfig.getDataPath(),
            besuConfig.getMiningParameters(),
            besuConfig.getJsonRpcConfiguration(),
            besuConfig.getWebSocketConfiguration(),
            besuConfig.getMetricsConfiguration(),
            besuConfig.getPermissioningConfiguration(),
            besuConfig.getKeyFilePath(),
            besuConfig.isDevMode(),
            besuConfig.getGenesisConfigProvider(),
            besuConfig.isP2pEnabled(),
            besuConfig.getNetworkingConfiguration(),
            besuConfig.isDiscoveryEnabled(),
            besuConfig.isBootnodeEligible(),
            besuConfig.isRevertReasonEnabled(),
            besuConfig.isSecp256k1Native(),
            besuConfig.isAltbn128Native(),
            besuConfig.getPlugins(),
            besuConfig.getExtraCLIOptions(),
            Collections.emptyList(),
            besuConfig.getPrivacyParameters(),
            Optional.empty());
  }

  public void testOrionConnection(final List<PrivacyNode> otherNodes) {
    LOG.info(
        String.format(
            "Testing Enclave connectivity between %s (%s) and %s (%s)",
            besu.getName(),
            orion.nodeUrl(),
            Arrays.toString(otherNodes.stream().map(node -> node.besu.getName()).toArray()),
            Arrays.toString(otherNodes.stream().map(node -> node.orion.nodeUrl()).toArray())));
    final EnclaveFactory factory = new EnclaveFactory(vertx);
    final Enclave enclaveClient = factory.createVertxEnclave(orion.clientUrl());
    final String payload = "SGVsbG8sIFdvcmxkIQ==";
    final List<String> to =
        otherNodes.stream()
            .map(node -> node.orion.getDefaultPublicKey())
            .collect(Collectors.toList());

    Awaitility.await()
        .until(
            () -> {
              try {
                enclaveClient.send(payload, orion.getDefaultPublicKey(), to);
                return true;
              } catch (final EnclaveClientException
                  | EnclaveIOException
                  | EnclaveServerException e) {
                LOG.warn("Waiting for enclave connectivity");
                return false;
              }
            });
  }

  public OrionTestHarness getOrion() {
    return orion;
  }

  public BesuNode getBesu() {
    return besu;
  }

  public void stop() {
    besu.stop();
    orion.stop();
  }

  @Override
  public void close() {
    besu.close();
    orion.close();
  }

  public void start(final BesuNodeRunner runner) {
    orion.start();

    final PrivacyParameters privacyParameters;

    try {
      final Path dataDir = Files.createTempDirectory("acctest-privacy");
      final Path dbDir = dataDir.resolve(DATABASE_PATH);

      privacyParameters =
          new PrivacyParameters.Builder()
              .setEnabled(true)
              .setEnclaveUrl(orion.clientUrl())
              .setEnclavePublicKeyUsingFile(orion.getConfig().publicKeys().get(0).toFile())
              .setStorageProvider(createKeyValueStorageProvider(dataDir, dbDir))
              .setPrivateKeyPath(KeyPairUtil.getDefaultKeyFile(besu.homeDirectory()).toPath())
              .setEnclaveFactory(new EnclaveFactory(vertx))
              .setPrivacyAddress(privacyAddress)
              .setOnchainPrivacyGroupsEnabled(isOnchainPrivacyEnabled)
              .build();
    } catch (IOException e) {
      throw new RuntimeException();
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
    return orion.getDefaultPublicKey();
  }

  public String getTransactionSigningKey() {
    return besu.getPrivacyParameters().getSigningKeyPair().orElseThrow().getPrivateKey().toString();
  }

  public void addOtherEnclaveNode(final URI otherNode) {
    orion.addOtherNode(otherNode);
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
}
