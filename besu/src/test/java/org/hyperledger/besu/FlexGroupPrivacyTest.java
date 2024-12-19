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
package org.hyperledger.besu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.core.PrivacyParameters.FLEXIBLE_PRIVACY;

import org.hyperledger.besu.components.BesuComponent;
import org.hyperledger.besu.components.BesuPluginContextModule;
import org.hyperledger.besu.components.MockBesuCommandModule;
import org.hyperledger.besu.components.NoOpMetricsSystemModule;
import org.hyperledger.besu.components.PrivacyTestModule;
import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.enclave.EnclaveFactory;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCacheModule;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.ethereum.privacy.storage.PrivacyStorageProvider;
import org.hyperledger.besu.ethereum.trie.diffbased.bonsai.cache.BonsaiCachedMerkleTrieLoaderModule;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Component;
import dagger.Module;
import dagger.Provides;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FlexGroupPrivacyTest {

  private final Vertx vertx = Vertx.vertx();

  @AfterEach
  public void cleanUp() {
    vertx.close();
  }

  @Test
  void flexibleEnabledPrivacy() {
    final BesuController besuController =
        DaggerFlexGroupPrivacyTest_FlexGroupPrivacyTestComponent.builder()
            .build()
            .getBesuController();

    final PrecompiledContract flexiblePrecompiledContract =
        getPrecompile(besuController, FLEXIBLE_PRIVACY);

    assertThat(flexiblePrecompiledContract.getName()).isEqualTo("FlexiblePrivacy");
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

  @Singleton
  @Component(
      modules = {
        FlexGroupPrivacyParametersModule.class,
        FlexGroupPrivacyTest.PrivacyTestBesuControllerModule.class,
        PrivacyTestModule.class,
        MockBesuCommandModule.class,
        BonsaiCachedMerkleTrieLoaderModule.class,
        NoOpMetricsSystemModule.class,
        BesuPluginContextModule.class,
        BlobCacheModule.class
      })
  interface FlexGroupPrivacyTestComponent extends BesuComponent {
    BesuController getBesuController();
  }

  @Module
  static class FlexGroupPrivacyParametersModule {

    @Provides
    PrivacyParameters providePrivacyParameters(
        final PrivacyStorageProvider storageProvider, final Vertx vertx) {
      try {
        return new PrivacyParameters.Builder()
            .setEnabled(true)
            .setEnclaveUrl(new URI("http://127.0.0.1:8000"))
            .setStorageProvider(storageProvider)
            .setEnclaveFactory(new EnclaveFactory(vertx))
            .setFlexiblePrivacyGroupsEnabled(true)
            .build();
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Module
  static class PrivacyTestBesuControllerModule {

    @Provides
    @Singleton
    @SuppressWarnings("CloseableProvides")
    BesuController provideBesuController(
        final PrivacyParameters privacyParameters,
        final DataStorageConfiguration dataStorageConfiguration,
        final FlexGroupPrivacyTestComponent context,
        @Named("dataDir") final Path dataDir) {

      return new BesuController.Builder()
          .fromGenesisFile(GenesisConfig.mainnet(), SyncMode.FULL)
          .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
          .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
          .storageProvider(new InMemoryKeyValueStorageProvider())
          .networkId(BigInteger.ONE)
          .miningParameters(MiningConfiguration.newDefault())
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
          .besuComponent(context)
          .apiConfiguration(ImmutableApiConfiguration.builder().build())
          .build();
    }
  }
}
