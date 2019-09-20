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

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.InMemoryStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParametersTestBuilder;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.BlockTestUtil;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.uint.UInt256;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.google.common.io.Resources;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link RlpBlockImporter}. */
public final class RlpBlockImporterTest {

  @Rule public final TemporaryFolder folder = new TemporaryFolder();

  private final RlpBlockImporter rlpBlockImporter = new RlpBlockImporter();

  @Test
  public void blockImport() throws IOException {
    final Path dataDir = folder.newFolder().toPath();
    final Path source = dataDir.resolve("1000.blocks");
    BlockTestUtil.write1000Blocks(source);
    final BesuController<?> targetController =
        new BesuController.Builder()
            .fromGenesisConfig(GenesisConfigFile.mainnet())
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryStorageProvider())
            .networkId(BigInteger.ONE)
            .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
            .nodeKeys(KeyPair.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .privacyParameters(PrivacyParameters.DEFAULT)
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.builder().build())
            .build();
    final RlpBlockImporter.ImportResult result =
        rlpBlockImporter.importBlockchain(source, targetController);
    // Don't count the Genesis block
    assertThat(result.count).isEqualTo(999);
    assertThat(result.td).isEqualTo(UInt256.of(21991996248790L));
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

    final BesuController<?> controller =
        new BesuController.Builder()
            .fromGenesisConfig(GenesisConfigFile.fromConfig(config))
            .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
            .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
            .storageProvider(new InMemoryStorageProvider())
            .networkId(BigInteger.valueOf(10))
            .miningParameters(new MiningParametersTestBuilder().enabled(false).build())
            .nodeKeys(KeyPair.generate())
            .metricsSystem(new NoOpMetricsSystem())
            .privacyParameters(PrivacyParameters.DEFAULT)
            .dataDirectory(dataDir)
            .clock(TestClock.fixed())
            .transactionPoolConfiguration(TransactionPoolConfiguration.builder().build())
            .build();
    final RlpBlockImporter.ImportResult result =
        rlpBlockImporter.importBlockchain(source, controller);

    // Don't count the Genesis block
    assertThat(result.count).isEqualTo(958);
  }
}
