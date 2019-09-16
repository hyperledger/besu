/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.tests.acceptance.dsl.privacy;

import org.hyperledger.besu.controller.KeyPairUtil;
import org.hyperledger.besu.enclave.Enclave;
import org.hyperledger.besu.enclave.EnclaveException;
import org.hyperledger.besu.enclave.types.SendRequest;
import org.hyperledger.besu.enclave.types.SendRequestLegacy;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValuePrivacyStorageFactory;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

  public PrivacyNode(final PrivacyNodeConfiguration privacyConfiguration) throws IOException {
    final Path orionDir = Files.createTempDirectory("acctest-orion");
    this.orion = OrionTestHarnessFactory.create(orionDir, privacyConfiguration.getOrionKeyConfig());

    final BesuNodeConfiguration besuConfig = privacyConfiguration.getBesuConfig();

    this.besu =
        new BesuNode(
            besuConfig.getName(),
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
            besuConfig.getPlugins(),
            besuConfig.getExtraCLIOptions(),
            new ArrayList<>());
  }

  public void testOrionConnection(final List<PrivacyNode> otherNodes) {
    LOG.info(
        String.format(
            "Testing Enclave connectivity between %s (%s) and %s (%s)",
            besu.getName(),
            orion.nodeUrl(),
            Arrays.toString(otherNodes.stream().map(node -> node.besu.getName()).toArray()),
            Arrays.toString(otherNodes.stream().map(node -> node.orion.nodeUrl()).toArray())));
    Enclave enclaveClient = new Enclave(orion.clientUrl());
    SendRequest sendRequest1 =
        new SendRequestLegacy(
            "SGVsbG8sIFdvcmxkIQ==",
            orion.getDefaultPublicKey(),
            otherNodes.stream()
                .map(node -> node.orion.getDefaultPublicKey())
                .collect(Collectors.toList()));

    Awaitility.await()
        .until(
            () -> {
              try {
                enclaveClient.send(sendRequest1);
                return true;
              } catch (final EnclaveException e) {
                LOG.info("Waiting for enclave connectivity");
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

      privacyParameters =
          new PrivacyParameters.Builder()
              .setEnabled(true)
              .setEnclaveUrl(orion.clientUrl())
              .setEnclavePublicKeyUsingFile(orion.getConfig().publicKeys().get(0).toFile())
              .setStorageProvider(createKeyValueStorageProvider(dataDir))
              .setPrivateKeyPath(KeyPairUtil.getDefaultKeyFile(besu.homeDirectory()).toPath())
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
    return besu.keyPair().getPrivateKey().toString();
  }

  public void addOtherEnclaveNode(final URI otherNode) {
    orion.addOtherNode(otherNode);
  }

  public NodeConfiguration getConfiguration() {
    return besu.getConfiguration();
  }

  private StorageProvider createKeyValueStorageProvider(final Path dbLocation) {
    return new KeyValueStorageProviderBuilder()
        .withStorageFactory(
            new RocksDBKeyValuePrivacyStorageFactory(
                () ->
                    new RocksDBFactoryConfiguration(
                        MAX_OPEN_FILES,
                        MAX_BACKGROUND_COMPACTIONS,
                        BACKGROUND_THREAD_COUNT,
                        CACHE_CAPACITY),
                Arrays.asList(KeyValueSegmentIdentifier.values())))
        .withCommonConfiguration(new BesuConfigurationImpl(dbLocation))
        .withMetricsSystem(new NoOpMetricsSystem())
        .build();
  }
}
