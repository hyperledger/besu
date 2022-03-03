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
package org.hyperledger.besu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.DEFAULT_PRIVACY;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValuePrivacyStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBKeyValueStorageFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.RocksDBMetricsFactory;
import org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBFactoryConfiguration;
import org.hyperledger.besu.services.BesuConfigurationImpl;
import org.hyperledger.besu.testutil.TestClock;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;

import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrivacyTest {

  private static final int MAX_OPEN_FILES = 1024;
  private static final long CACHE_CAPACITY = 8388608;
  private static final int MAX_BACKGROUND_COMPACTIONS = 4;
  private static final int BACKGROUND_THREAD_COUNT = 4;
  private final Vertx vertx = Vertx.vertx();

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @After
  public void cleanUp() {
    vertx.close();
  }

  @Test
  public void defaultPrivacy() throws IOException, URISyntaxException {
    final BesuController besuController = setUpControllerWithPrivacyEnabled(false);

    final PrecompiledContract precompiledContract = getPrecompile(besuController, DEFAULT_PRIVACY);

    assertThat(precompiledContract.getName()).isEqualTo("Privacy");
  }

  @Test
  public void flexibleEnabledPrivacy() throws IOException, URISyntaxException {
    final BesuController besuController = setUpControllerWithPrivacyEnabled(true);

    final PrecompiledContract flexiblePrecompiledContract =
        getPrecompile(besuController, FLEXIBLE_PRIVACY);

    assertThat(flexiblePrecompiledContract.getName()).isEqualTo("FlexiblePrivacy");
  }

  private BesuController setUpControllerWithPrivacyEnabled(final boolean flexibleEnabled)
      throws IOException, URISyntaxException {
    final Path dataDir = folder.newFolder().toPath();
    final Path dbDir = dataDir.resolve("database");
    final PrivacyParameters privacyParameters =
        new PrivacyParameters.Builder()
            .setEnabled(true)
            .setEnclaveUrl(new URI("http://127.0.0.1:8000"))
            .setStorageProvider(createKeyValueStorageProvider(dataDir, dbDir))
            .setEnclaveFactory(new EnclaveFactory(vertx))
            .setFlexiblePrivacyGroupsEnabled(flexibleEnabled)
            .build();
    return new BesuController.Builder()
        .fromGenesisConfig(GenesisConfigFile.mainnet())
        .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
        .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
        .storageProvider(new InMemoryKeyValueStorageProvider())
        .networkId(BigInteger.ONE)
        .miningParameters(new MiningParameters.Builder().miningEnabled(false).build())
        .nodeKey(NodeKeyUtils.generate())
        .metricsSystem(new NoOpMetricsSystem())
        .dataDirectory(dataDir)
        .clock(TestClock.fixed())
        .privacyParameters(privacyParameters)
        .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
        .gasLimitCalculator(GasLimitCalculator.constant())
        .evmConfiguration(EvmConfiguration.DEFAULT)
        .build();
  }

  private PrivacyStorageProvider createKeyValueStorageProvider(
      final Path dataDir, final Path dbDir) {
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
        .withCommonConfiguration(new BesuConfigurationImpl(dataDir, dbDir))
        .withMetricsSystem(new NoOpMetricsSystem())
        .build();
  }

  private PrecompiledContract getPrecompile(
      final BesuController besuController, final Address defaultPrivacy) {
    return besuController
        .getProtocolSchedule()
        .getByBlockNumber(1)
        .getPrecompileContractRegistry()
        .get(defaultPrivacy);
  }
}
