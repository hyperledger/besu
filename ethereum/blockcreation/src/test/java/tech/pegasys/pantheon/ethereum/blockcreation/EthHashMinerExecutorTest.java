/*
 * Copyright 2018 ConsenSys AG.
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
package tech.pegasys.pantheon.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import tech.pegasys.pantheon.ethereum.core.MiningParameters;
import tech.pegasys.pantheon.ethereum.core.MiningParametersTestBuilder;
import tech.pegasys.pantheon.ethereum.core.PendingTransactions;
import tech.pegasys.pantheon.metrics.MetricsSystem;
import tech.pegasys.pantheon.metrics.noop.NoOpMetricsSystem;
import tech.pegasys.pantheon.testutil.TestClock;
import tech.pegasys.pantheon.util.Subscribers;

import java.util.concurrent.Executors;

import org.junit.Test;

public class EthHashMinerExecutorTest {
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Test
  public void startingMiningWithoutCoinbaseThrowsException() {
    final MiningParameters miningParameters =
        new MiningParametersTestBuilder().coinbase(null).build();

    final EthHashMinerExecutor executor =
        new EthHashMinerExecutor(
            null,
            Executors.newCachedThreadPool(),
            null,
            new PendingTransactions(1, TestClock.fixed(), metricsSystem),
            miningParameters,
            new DefaultBlockScheduler(1, 10, TestClock.fixed()));

    assertThatExceptionOfType(CoinbaseNotSetException.class)
        .isThrownBy(() -> executor.startAsyncMining(new Subscribers<>(), null))
        .withMessageContaining("Unable to start mining without a coinbase.");
  }

  @Test
  public void settingCoinbaseToNullThrowsException() {
    final MiningParameters miningParameters = new MiningParametersTestBuilder().build();

    final EthHashMinerExecutor executor =
        new EthHashMinerExecutor(
            null,
            Executors.newCachedThreadPool(),
            null,
            new PendingTransactions(1, TestClock.fixed(), metricsSystem),
            miningParameters,
            new DefaultBlockScheduler(1, 10, TestClock.fixed()));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> executor.setCoinbase(null))
        .withMessageContaining("Coinbase cannot be unset.");
  }
}
