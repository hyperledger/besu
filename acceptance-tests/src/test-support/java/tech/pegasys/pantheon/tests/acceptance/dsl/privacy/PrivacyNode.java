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
package tech.pegasys.pantheon.tests.acceptance.dsl.privacy;

import tech.pegasys.orion.testutil.OrionTestHarness;
import tech.pegasys.orion.testutil.OrionTestHarnessFactory;
import tech.pegasys.pantheon.controller.KeyPairUtil;
import tech.pegasys.pantheon.enclave.Enclave;
import tech.pegasys.pantheon.enclave.EnclaveException;
import tech.pegasys.pantheon.enclave.types.SendRequest;
import tech.pegasys.pantheon.enclave.types.SendRequestLegacy;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.storage.StorageProvider;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import tech.pegasys.pantheon.ethereum.storage.keyvalue.KeyValueStorageProviderBuilder;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.plugin.services.storage.rocksdb.RocksDBKeyValuePrivacyStorageFactory;
import tech.pegasys.pantheon.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import tech.pegasys.pantheon.services.PantheonConfigurationImpl;
import tech.pegasys.pantheon.tests.acceptance.dsl.condition.Condition;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNode;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.PantheonNodeRunner;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.NodeConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.PantheonNodeConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.node.configuration.privacy.PrivacyNodeConfiguration;
import tech.pegasys.pantheon.tests.acceptance.dsl.privacy.condition.PrivateCondition;
import tech.pegasys.pantheon.tests.acceptance.dsl.transaction.Transaction;

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
  private final PantheonNode pantheon;

  public PrivacyNode(final PrivacyNodeConfiguration privacyConfiguration) throws IOException {
    final Path orionDir = Files.createTempDirectory("acctest-orion");
    this.orion = OrionTestHarnessFactory.create(orionDir, privacyConfiguration.getOrionKeyConfig());

    final PantheonNodeConfiguration pantheonConfig = privacyConfiguration.getPantheonConfig();

    this.pantheon =
        new PantheonNode(
            pantheonConfig.getName(),
            pantheonConfig.getMiningParameters(),
            pantheonConfig.getJsonRpcConfiguration(),
            pantheonConfig.getWebSocketConfiguration(),
            pantheonConfig.getMetricsConfiguration(),
            pantheonConfig.getPermissioningConfiguration(),
            pantheonConfig.getKeyFilePath(),
            pantheonConfig.isDevMode(),
            pantheonConfig.getGenesisConfigProvider(),
            pantheonConfig.isP2pEnabled(),
            pantheonConfig.getNetworkingConfiguration(),
            pantheonConfig.isDiscoveryEnabled(),
            pantheonConfig.isBootnodeEligible(),
            pantheonConfig.isRevertReasonEnabled(),
            pantheonConfig.getPlugins(),
            pantheonConfig.getExtraCLIOptions(),
            new ArrayList<>());
  }

  public void testOrionConnection(final List<PrivacyNode> otherNodes) {
    LOG.info(
        String.format(
            "Testing Enclave connectivity between %s (%s) and %s (%s)",
            pantheon.getName(),
            orion.nodeUrl(),
            Arrays.toString(otherNodes.stream().map(node -> node.pantheon.getName()).toArray()),
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

  public PantheonNode getPantheon() {
    return pantheon;
  }

  public void stop() {
    pantheon.stop();
    orion.stop();
  }

  @Override
  public void close() {
    pantheon.close();
    orion.close();
  }

  public void start(final PantheonNodeRunner runner) {
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
              .setPrivateKeyPath(KeyPairUtil.getDefaultKeyFile(pantheon.homeDirectory()).toPath())
              .build();
    } catch (IOException e) {
      throw new RuntimeException();
    }
    pantheon.setPrivacyParameters(privacyParameters);
    pantheon.start(runner);
  }

  public void awaitPeerDiscovery(final Condition condition) {
    pantheon.awaitPeerDiscovery(condition);
  }

  public String getName() {
    return pantheon.getName();
  }

  public Address getAddress() {
    return pantheon.getAddress();
  }

  public URI enodeUrl() {
    return pantheon.enodeUrl();
  }

  public String getNodeId() {
    return pantheon.getNodeId();
  }

  public <T> T execute(final Transaction<T> transaction) {
    return pantheon.execute(transaction);
  }

  public void verify(final PrivateCondition expected) {
    expected.verify(this);
  }

  public String getEnclaveKey() {
    return orion.getDefaultPublicKey();
  }

  public String getTransactionSigningKey() {
    return pantheon.keyPair().getPrivateKey().toString();
  }

  public void addOtherEnclaveNode(final URI otherNode) {
    orion.addOtherNode(otherNode);
  }

  public NodeConfiguration getConfiguration() {
    return pantheon.getConfiguration();
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
        .withCommonConfiguration(new PantheonConfigurationImpl(dbLocation))
        .withMetricsSystem(new NoOpMetricsSystem())
        .build();
  }
}
