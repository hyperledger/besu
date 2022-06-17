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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.MergeConfigOptions;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.BlockTestUtil;
import org.hyperledger.besu.testutil.TestClock;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletionException;

import com.google.common.io.Resources;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/** Tests for {@link RlpBlockImporter}. */
@RunWith(MockitoJUnitRunner.class)
public final class RlpBlockImporterTest {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private final RlpBlockImporter rlpBlockImporter = new RlpBlockImporter();

  @Test
  public void blockImport() throws IOException {
    final Path dataDir = folder.newFolder().toPath();
    final Path source = dataDir.resolve("1000.blocks");
    BlockTestUtil.write1000Blocks(source);
    final BesuController targetController =
        new BesuController.Builder()
            .fromGenesisConfig(GenesisConfigFile.mainnet())
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryKeyValueStorageProvider())
            .networkId(BigInteger.ONE)
            .miningParameters(new MiningParameters.Builder().miningEnabled(false).build())
            .nodeKey(NodeKeyUtils.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .privacyParameters(PrivacyParameters.DEFAULT)
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
            .gasLimitCalculator(GasLimitCalculator.constant())
            .evmConfiguration(EvmConfiguration.DEFAULT)
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
    MergeConfigOptions.setMergeEnabled(false);

    final Path dataDir = folder.newFolder().toPath();
    final Path source = dataDir.resolve("badpow.blocks");
    BlockTestUtil.writeBadPowBlocks(source);
    final BesuController targetController =
        new BesuController.Builder()
            .fromGenesisConfig(GenesisConfigFile.mainnet())
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryKeyValueStorageProvider())
            .networkId(BigInteger.ONE)
            .miningParameters(new MiningParameters.Builder().miningEnabled(false).build())
            .nodeKey(NodeKeyUtils.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .privacyParameters(PrivacyParameters.DEFAULT)
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
            .gasLimitCalculator(GasLimitCalculator.constant())
            .evmConfiguration(EvmConfiguration.DEFAULT)
            .build();

    assertThatThrownBy(
        () -> rlpBlockImporter.importBlockchain(source, targetController, false),
        "Invalid header at block number 2.",
        CompletionException.class);
  }

  @Test
  public void blockImportCanSkipPow() throws IOException {
    final Path dataDir = folder.newFolder().toPath();
    final Path source = dataDir.resolve("badpow.blocks");
    BlockTestUtil.writeBadPowBlocks(source);
    final BesuController targetController =
        new BesuController.Builder()
            .fromGenesisConfig(GenesisConfigFile.mainnet())
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryKeyValueStorageProvider())
            .networkId(BigInteger.ONE)
            .miningParameters(new MiningParameters.Builder().miningEnabled(false).build())
            .nodeKey(NodeKeyUtils.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .privacyParameters(PrivacyParameters.DEFAULT)
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
            .gasLimitCalculator(GasLimitCalculator.constant())
            .evmConfiguration(EvmConfiguration.DEFAULT)
            .build();

    final RlpBlockImporter.ImportResult result =
        rlpBlockImporter.importBlockchain(source, targetController, true);
    assertThat(result.count).isEqualTo(1);
    assertThat(result.td).isEqualTo(UInt256.valueOf(34351349760L));
  }

  @Test
  public void ibftImport() throws IOException {
    final Path dataDir = folder.newFolder().toPath();
    final Path source = dataDir.resolve("ibft.blocks");
    final String config =
        Resources.toString(this.getClass().getResource("/ibftlegacy_genesis.json"), UTF_8);

    try {
      Files.write(
          source,
          Resources.toByteArray(this.getClass().getResource("/ibft.blocks")),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING);
    } catch (final IOException ex) {
      throw new IllegalStateException(ex);
    }

    final BesuController controller =
        new BesuController.Builder()
            .fromGenesisConfig(GenesisConfigFile.fromConfig(config))
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryKeyValueStorageProvider())
            .networkId(BigInteger.valueOf(10))
            .miningParameters(new MiningParameters.Builder().miningEnabled(false).build())
            .nodeKey(NodeKeyUtils.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .privacyParameters(PrivacyParameters.DEFAULT)
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
            .gasLimitCalculator(GasLimitCalculator.constant())
            .evmConfiguration(EvmConfiguration.DEFAULT)
            .build();
    final RlpBlockImporter.ImportResult result =
        rlpBlockImporter.importBlockchain(source, controller, false);

    // Don't count the Genesis block
    assertThat(result.count).isEqualTo(958);
  }
}
