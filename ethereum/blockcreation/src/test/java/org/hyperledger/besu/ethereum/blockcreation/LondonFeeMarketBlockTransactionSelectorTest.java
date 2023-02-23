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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.time.ZoneId;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class LondonFeeMarketBlockTransactionSelectorTest
    extends AbstractBlockTransactionSelectorTest {

  @Override
  protected PendingTransactions createPendingTransactions() {
    return new BaseFeePendingTransactionsSorter(
        ImmutableTransactionPoolConfiguration.builder()
            .txPoolMaxSize(5)
            .txPoolLimitByAccountPercentage(1)
            .build(),
        TestClock.system(ZoneId.systemDefault()),
        metricsSystem,
        LondonFeeMarketBlockTransactionSelectorTest::mockBlockHeader);
  }

  private static BlockHeader mockBlockHeader() {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(Wei.ONE));
    return blockHeader;
  }

  @Override
  protected FeeMarket getFeeMarket() {
    return FeeMarket.london(0L);
  }

  @Test
  public void eip1559TransactionCurrentGasPriceLessThanMinimumIsSkippedAndKeptInThePool() {
    final ProcessableBlockHeader blockHeader = createBlock(301, Wei.ONE);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor, blockHeader, Wei.of(6), miningBeneficiary, Wei.ZERO);

    // tx is willing to pay max 6 wei for gas, but current network condition (baseFee == 1)
    // result in it paying 2 wei, that is below the minimum accepted by the node, so it is skipped
    final Transaction tx = createEIP1559Transaction(1, Wei.of(6L), Wei.ONE);
    pendingTransactions.addRemoteTransaction(tx, Optional.empty());

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions().size()).isEqualTo(0);
    assertThat(pendingTransactions.size()).isEqualTo(1);
  }

  @Test
  public void eip1559TransactionCurrentGasPriceGreaterThanMinimumIsSelected() {
    final ProcessableBlockHeader blockHeader = createBlock(301, Wei.of(5));

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor, blockHeader, Wei.of(6), miningBeneficiary, Wei.ZERO);

    // tx is willing to pay max 6 wei for gas, and current network condition (baseFee == 5)
    // result in it paying the max, that is >= the minimum accepted by the node, so it is selected
    final Transaction tx = createEIP1559Transaction(1, Wei.of(6), Wei.ONE);
    pendingTransactions.addRemoteTransaction(tx, Optional.empty());

    ensureTransactionIsValid(tx);

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions().size()).isEqualTo(1);
    assertThat(pendingTransactions.size()).isEqualTo(1);
  }

  @Test
  public void eip1559LocalTransactionCurrentGasPriceLessThanMinimumIsSelected() {
    final ProcessableBlockHeader blockHeader = createBlock(301, Wei.ONE);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor, blockHeader, Wei.of(6), miningBeneficiary, Wei.ZERO);

    // tx is willing to pay max 6 wei for gas, but current network condition (baseFee == 1)
    // result in it paying 2 wei, that is below the minimum accepted by the node, but since it is
    // a local sender it is accepted anyway
    final Transaction tx = createEIP1559Transaction(1, Wei.of(6L), Wei.ONE);
    pendingTransactions.addLocalTransaction(tx, Optional.empty());

    ensureTransactionIsValid(tx);

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions().size()).isEqualTo(1);
    assertThat(pendingTransactions.size()).isEqualTo(1);
  }

  private Transaction createEIP1559Transaction(
      final int transactionNumber, final Wei maxFeePerGas, final Wei maxPriorityFeePerGas) {
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .gasLimit(100)
        .maxFeePerGas(maxFeePerGas)
        .maxPriorityFeePerGas(maxPriorityFeePerGas)
        .nonce(transactionNumber)
        .payload(Bytes.EMPTY)
        .to(Address.ID)
        .value(Wei.of(transactionNumber))
        .sender(Address.ID)
        .chainId(BigInteger.ONE)
        .guessType()
        .signAndBuild(keyPair);
  }
}
