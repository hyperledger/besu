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
package org.hyperledger.besu.chainimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.cli.config.NetworkName;
import org.hyperledger.besu.components.BesuComponent;
import org.hyperledger.besu.config.MergeConfiguration;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.api.ImmutableApiConfiguration;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.BlockTestUtil;
import org.hyperledger.besu.testutil.TestClock;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;

/** Tests for {@link RlpBlockImporter}. */
@ExtendWith(MockitoExtension.class)
public final class RlpBlockImporterTest {

  @TempDir Path dataDir;

  private final RlpBlockImporter rlpBlockImporter = new RlpBlockImporter();

  @Test
  public void blockImport() throws IOException {
    final Path source = dataDir.resolve("1000.blocks");
    BlockTestUtil.write1000Blocks(source);
    final BesuController targetController =
        new BesuController.Builder()
            .fromEthNetworkConfig(
                EthNetworkConfig.getNetworkConfig(NetworkName.MAINNET), SyncMode.FAST)
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryKeyValueStorageProvider())
            .networkId(BigInteger.ONE)
            .miningParameters(MiningConfiguration.newDefault())
            .nodeKey(NodeKeyUtils.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .privacyParameters(PrivacyParameters.DEFAULT)
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
            .gasLimitCalculator(GasLimitCalculator.constant())
            .evmConfiguration(EvmConfiguration.DEFAULT)
            .networkConfiguration(NetworkingConfiguration.create())
            .besuComponent(mock(BesuComponent.class))
            .apiConfiguration(ImmutableApiConfiguration.builder().build())
            .build();
    final RlpBlockImporter.ImportResult result =
        rlpBlockImporter.importBlockchain(source, targetController, false);
    // Don't count the Genesis block
    assertThat(result.count).isEqualTo(999);
    assertThat(result.td).isEqualTo(UInt256.valueOf(21991996248790L));
  }

  @Test
  public void blockImportRejectsBadPow() throws IOException {
    // set merge flag to false, otherwise this test can fail if a merge test runs first
    MergeConfiguration.setMergeEnabled(false);

    final Path source = dataDir.resolve("badpow.blocks");
    BlockTestUtil.writeBadPowBlocks(source);
    final BesuController targetController =
        new BesuController.Builder()
            .fromEthNetworkConfig(
                EthNetworkConfig.getNetworkConfig(NetworkName.MAINNET), SyncMode.FAST)
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryKeyValueStorageProvider())
            .networkId(BigInteger.ONE)
            .miningParameters(MiningConfiguration.newDefault())
            .nodeKey(NodeKeyUtils.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .privacyParameters(PrivacyParameters.DEFAULT)
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
            .gasLimitCalculator(GasLimitCalculator.constant())
            .evmConfiguration(EvmConfiguration.DEFAULT)
            .networkConfiguration(NetworkingConfiguration.create())
            .besuComponent(mock(BesuComponent.class))
            .apiConfiguration(ImmutableApiConfiguration.builder().build())
            .build();

    assertThatThrownBy(
        () -> rlpBlockImporter.importBlockchain(source, targetController, false),
        "Invalid header at block number 2.",
        CompletionException.class);
  }

  @Test
  public void blockImportCanSkipPow() throws IOException {
    final Path source = dataDir.resolve("badpow.blocks");
    BlockTestUtil.writeBadPowBlocks(source);
    final BesuController targetController =
        new BesuController.Builder()
            .fromEthNetworkConfig(
                EthNetworkConfig.getNetworkConfig(NetworkName.MAINNET), SyncMode.FAST)
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryKeyValueStorageProvider())
            .networkId(BigInteger.ONE)
            .miningParameters(MiningConfiguration.newDefault())
            .nodeKey(NodeKeyUtils.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .privacyParameters(PrivacyParameters.DEFAULT)
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
            .gasLimitCalculator(GasLimitCalculator.constant())
            .evmConfiguration(EvmConfiguration.DEFAULT)
            .networkConfiguration(NetworkingConfiguration.create())
            .besuComponent(mock(BesuComponent.class))
            .apiConfiguration(ImmutableApiConfiguration.builder().build())
            .build();

    final RlpBlockImporter.ImportResult result =
        rlpBlockImporter.importBlockchain(source, targetController, true);
    assertThat(result.count).isEqualTo(1);
    assertThat(result.td).isEqualTo(UInt256.valueOf(34351349760L));
  }
}
