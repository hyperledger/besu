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
package org.hyperledger.besu.ethereum.blockcreation;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.Subscribers;

import java.util.Optional;

import org.junit.Test;

public class PoWMinerExecutorTest {
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  @Test
  public void startingMiningWithoutCoinbaseThrowsException() {
    final MiningParameters miningParameters =
        new MiningParameters.Builder().coinbase(null).minTransactionGasPrice(Wei.of(1000)).build();

    final GasPricePendingTransactionsSorter pendingTransactions =
        new GasPricePendingTransactionsSorter(
            TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
            1,
            TestClock.fixed(),
            metricsSystem,
            PoWMinerExecutorTest::mockBlockHeader,
            TransactionPoolConfiguration.DEFAULT_PRICE_BUMP);

    final PoWMinerExecutor executor =
        new PoWMinerExecutor(
            null,
            null,
            pendingTransactions,
            miningParameters,
            new DefaultBlockScheduler(1, 10, TestClock.fixed()),
            new EpochCalculator.DefaultEpochCalculator(),
            1000,
            8);

    assertThatExceptionOfType(CoinbaseNotSetException.class)
        .isThrownBy(() -> executor.startAsyncMining(Subscribers.create(), Subscribers.none(), null))
        .withMessageContaining("Unable to start mining without a coinbase.");
  }

  @Test
  public void settingCoinbaseToNullThrowsException() {
    final MiningParameters miningParameters = new MiningParameters.Builder().build();

    final GasPricePendingTransactionsSorter pendingTransactions =
        new GasPricePendingTransactionsSorter(
            TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
            1,
            TestClock.fixed(),
            metricsSystem,
            PoWMinerExecutorTest::mockBlockHeader,
            TransactionPoolConfiguration.DEFAULT_PRICE_BUMP);

    final PoWMinerExecutor executor =
        new PoWMinerExecutor(
            null,
            null,
            pendingTransactions,
            miningParameters,
            new DefaultBlockScheduler(1, 10, TestClock.fixed()),
            new EpochCalculator.DefaultEpochCalculator(),
            1000,
            8);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> executor.setCoinbase(null))
        .withMessageContaining("Coinbase cannot be unset.");
  }

  private static BlockHeader mockBlockHeader() {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.empty());
    return blockHeader;
  }
}
