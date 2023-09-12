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

import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.controller.BesuController;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.eth.EthProtocolConfiguration;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.linea.LineaParameters;
import org.hyperledger.besu.ethereum.p2p.config.NetworkingConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.nio.file.Path;

import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LineaTest {

  private final Vertx vertx = Vertx.vertx();

  @TempDir private static Path dataDir;

  @AfterEach
  public void cleanUp() {
    vertx.close();
  }

  @Test
  public void smokeTest() {
    setUpControllerWithLineaEnabled();
  }

  private BesuController setUpControllerWithLineaEnabled() {
    return new BesuController.Builder()
        .fromGenesisConfig(GenesisConfigFile.genesisFileFromResources("/linea.json"), SyncMode.FULL)
        .synchronizerConfiguration(SynchronizerConfiguration.builder().build())
        .ethProtocolConfiguration(EthProtocolConfiguration.defaultConfig())
        .storageProvider(new InMemoryKeyValueStorageProvider())
        .networkId(BigInteger.valueOf(23331))
        .miningParameters(new MiningParameters.Builder().miningEnabled(false).build())
        .nodeKey(NodeKeyUtils.generate())
        .privacyParameters(mock(PrivacyParameters.class))
        .metricsSystem(new NoOpMetricsSystem())
        .dataDirectory(dataDir)
        .clock(TestClock.fixed())
        .transactionPoolConfiguration(TransactionPoolConfiguration.DEFAULT)
        .gasLimitCalculator(GasLimitCalculator.constant())
        .evmConfiguration(EvmConfiguration.DEFAULT)
        .networkConfiguration(NetworkingConfiguration.create())
        .lineaParameters(LineaParameters.DEFAULT)
        .build();
  }
}
