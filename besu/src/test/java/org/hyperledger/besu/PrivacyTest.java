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
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_BACKGROUND_THREAD_COUNT;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_CACHE_CAPACITY;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_IS_HIGH_SPEC;
import static org.hyperledger.besu.plugin.services.storage.rocksdb.configuration.RocksDBCLIOptions.DEFAULT_MAX_OPEN_FILES;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;
import org.hyperledger.besu.ethereum.privacy.storage.keyvalue.PrivacyKeyValueStorageProviderBuilder;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PrivacyTest {

  private final Vertx vertx = Vertx.vertx();

  @TempDir private Path dataDir;

  @AfterEach
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
    final Path dbDir = Files.createTempDirectory(dataDir, "database");
    final var miningParameters = MiningParameters.newDefault();
    final var dataStorageConfiguration = DataStorageConfiguration.DEFAULT_FOREST_CONFIG;
    final PrivacyParameters privacyParameters =
        new PrivacyParameters.Builder()
            .setEnabled(true)
            .setEnclaveUrl(new URI("http://127.0.0.1:8000"))
            .setStorageProvider(
                createKeyValueStorageProvider(
                    dataDir, dbDir, dataStorageConfiguration, miningParameters))
            .setEnclaveFactory(new EnclaveFactory(vertx))
            .setFlexiblePrivacyGroupsEnabled(flexibleEnabled)
            .build();
    return new BesuController.Builder()
        .fromGenesisConfig(GenesisConfigFile.mainnet(), SyncMode.FULL)
        .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
        .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
        .storageProvider(new InMemoryKeyValueStorageProvider())
        .networkId(BigInteger.ONE)
        .miningParameters(miningParameters)
        .dataStorageConfiguration(dataStorageConfiguration)
        .nodeKey(NodeKeyUtils.generate())
        .metricsSystem(new NoOpMetricsSystem())
        .dataDirectory(dataDir)
        .clock(TestClock.fixed())
        .privacyParameters(privacyParameters)
        .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
        .gasLimitCalculator(GasLimitCalculator.constant())
        .evmConfiguration(EvmConfiguration.DEFAULT)
        .networkConfiguration(NetworkingConfiguration.create())
        .build();
  }

  private PrivacyStorageProvider createKeyValueStorageProvider(
      final Path dataDir,
      final Path dbDir,
      final DataStorageConfiguration dataStorageConfiguration,
      final MiningParameters miningParameters) {
    final var besuConfiguration = new BesuConfigurationImpl();
    besuConfiguration.init(dataDir, dbDir, dataStorageConfiguration, miningParameters);
    return new PrivacyKeyValueStorageProviderBuilder()
        .withStorageFactory(
            new RocksDBKeyValuePrivacyStorageFactory(
                new RocksDBKeyValueStorageFactory(
                    () ->
                        new RocksDBFactoryConfiguration(
                            DEFAULT_MAX_OPEN_FILES,
                            DEFAULT_BACKGROUND_THREAD_COUNT,
                            DEFAULT_CACHE_CAPACITY,
                            DEFAULT_IS_HIGH_SPEC),
                    Arrays.asList(KeyValueSegmentIdentifier.values()),
                    RocksDBMetricsFactory.PRIVATE_ROCKS_DB_METRICS)))
        .withCommonConfiguration(besuConfiguration)
        .withMetricsSystem(new NoOpMetricsSystem())
        .build();
  }

  private PrecompiledContract getPrecompile(
      final BesuController besuController, final Address defaultPrivacy) {
    return besuController
        .getProtocolSchedule()
        .getByBlockHeader(blockHeader(0))
        .getPrecompileContractRegistry()
        .get(defaultPrivacy);
  }

  private BlockHeader blockHeader(final long number) {
    return new BlockHeaderTestFixture().number(number).buildHeader();
  }
}
