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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCache;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionBroadcaster;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.EpochCalculator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.DeterministicEthScheduler;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.Subscribers;

import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

public class PoWMinerExecutorTest {
  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final EthScheduler ethScheduler = new DeterministicEthScheduler();

  @Test
  public void startingMiningWithoutCoinbaseThrowsException() {
    final MiningConfiguration miningConfiguration = MiningConfiguration.newDefault();

    final TransactionPool transactionPool = createTransactionPool();

    final PoWMinerExecutor executor =
        new PoWMinerExecutor(
            null,
            null,
            transactionPool,
            miningConfiguration,
            new DefaultBlockScheduler(1L, 10, TestClock.fixed()),
            new EpochCalculator.DefaultEpochCalculator(),
            ethScheduler);

    assertThatExceptionOfType(CoinbaseNotSetException.class)
        .isThrownBy(() -> executor.startAsyncMining(Subscribers.create(), Subscribers.none(), null))
        .withMessageContaining("Unable to start mining without a coinbase.");
  }

  @Test
  public void settingCoinbaseToNullThrowsException() {
    final MiningConfiguration miningConfiguration = MiningConfiguration.newDefault();

    final TransactionPool transactionPool = createTransactionPool();

    final PoWMinerExecutor executor =
        new PoWMinerExecutor(
            null,
            null,
            transactionPool,
            miningConfiguration,
            new DefaultBlockScheduler(1, 10, TestClock.fixed()),
            new EpochCalculator.DefaultEpochCalculator(),
            ethScheduler);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> executor.setCoinbase(null))
        .withMessageContaining("Coinbase cannot be unset.");
  }

  private static BlockHeader mockBlockHeader() {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.empty());
    return blockHeader;
  }

  private TransactionPool createTransactionPool() {
    final TransactionPoolConfiguration poolConf =
        ImmutableTransactionPoolConfiguration.builder().txPoolMaxSize(1).build();
    final GasPricePendingTransactionsSorter pendingTransactions =
        new GasPricePendingTransactionsSorter(
            poolConf,
            TestClock.system(ZoneId.systemDefault()),
            metricsSystem,
            PoWMinerExecutorTest::mockBlockHeader);

    final EthContext ethContext = mock(EthContext.class, RETURNS_DEEP_STUBS);
    when(ethContext.getEthPeers().subscribeConnect(any())).thenReturn(1L);

    final TransactionPool transactionPool =
        new TransactionPool(
            () -> pendingTransactions,
            mock(ProtocolSchedule.class),
            mock(ProtocolContext.class),
            mock(TransactionBroadcaster.class),
            ethContext,
            new TransactionPoolMetrics(new NoOpMetricsSystem()),
            poolConf,
            new BlobCache());
    transactionPool.setEnabled();

    return transactionPool;
  }
}
