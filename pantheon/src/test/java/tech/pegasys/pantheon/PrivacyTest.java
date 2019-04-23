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
package tech.pegasys.pantheon;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.config.GenesisConfigFile;
import tech.pegasys.pantheon.controller.PantheonController;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.InMemoryStorageProvider;
import tech.pegasys.pantheon.ethereum.core.MiningParametersTestBuilder;
import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.eth.EthereumWireProtocolConfiguration;
import tech.pegasys.pantheon.ethereum.eth.sync.SynchronizerConfiguration;
import tech.pegasys.pantheon.ethereum.eth.transactions.PendingTransactions;
import tech.pegasys.pantheon.ethereum.mainnet.PrecompiledContract;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.testutil.TestClock;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PrivacyTest {

  private static final Integer ADDRESS = 9;
  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void privacyPrecompiled() throws IOException {
    final Path dataDir = folder.newFolder().toPath();
    final PrivacyParameters privacyParameters =
        new PrivacyParameters.Builder()
            .setPrivacyAddress(ADDRESS)
            .setEnabled(true)
            .setDataDir(dataDir)
            .build();

    final PantheonController<?> pantheonController =
        new PantheonController.Builder()
            .fromGenesisConfig(GenesisConfigFile.mainnet())
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethereumWireProtocolConfiguration(EthereumWireProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryStorageProvider())
            .networkId(1)
            .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
            .nodeKeys(KeyPair.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .privacyParameters(privacyParameters)
            .maxPendingTransactions(PendingTransactions.MAX_PENDING_TRANSACTIONS)
            .build();

    final Address privacyContractAddress = Address.privacyPrecompiled(ADDRESS);
    final PrecompiledContract precompiledContract =
        pantheonController
            .getProtocolSchedule()
            .getByBlockNumber(1)
            .getPrecompileContractRegistry()
            .get(privacyContractAddress);
    assertThat(precompiledContract.getName()).isEqualTo("Privacy");
  }
}
