/*
 * Copyright Hyperledger Besu Contributors.
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.testutil.TestClock;

import java.time.ZoneId;
import java.util.Optional;

public class LegacyFeeMarketBlockTransactionSelectorTest
    extends AbstractBlockTransactionSelectorTest {

  @Override
  protected PendingTransactions createPendingTransactions() {

    return new GasPricePendingTransactionsSorter(
        ImmutableTransactionPoolConfiguration.builder()
            .txPoolMaxSize(5)
            .txPoolLimitByAccountPercentage(1)
            .build(),
        TestClock.system(ZoneId.systemDefault()),
        metricsSystem,
        LegacyFeeMarketBlockTransactionSelectorTest::mockBlockHeader);
  }

  private static BlockHeader mockBlockHeader() {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.empty());
    return blockHeader;
  }

  @Override
  protected FeeMarket getFeeMarket() {
    return FeeMarket.legacy();
  }
}
