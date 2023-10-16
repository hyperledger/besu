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
import static org.assertj.core.api.Assertions.entry;
import static org.hyperledger.besu.ethereum.core.MiningParameters.Unstable.DEFAULT_TX_SELECTION_MAX_TIME;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.blockcreation.txselection.BlockTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionBroadcaster;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.testutil.TestClock;
import org.hyperledger.besu.util.number.Fraction;

import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

public class LondonFeeMarketBlockTransactionSelectorTest
    extends AbstractBlockTransactionSelectorTest {

  @Override
  protected GenesisConfigFile getGenesisConfigFile() {
    return GenesisConfigFile.genesisFileFromResources(
        "/block-transaction-selector/london-genesis.json");
  }

  @Override
  protected ProtocolSchedule createProtocolSchedule() {
    return new ProtocolScheduleBuilder(
            genesisConfigFile.getConfigOptions(),
            CHAIN_ID,
            ProtocolSpecAdapters.create(0, Function.identity()),
            new PrivacyParameters(),
            false,
            EvmConfiguration.DEFAULT)
        .createProtocolSchedule();
  }

  @Override
  protected TransactionPool createTransactionPool(final MiningParameters miningParameters) {
    final TransactionPoolConfiguration poolConf =
        ImmutableTransactionPoolConfiguration.builder()
            .txPoolMaxSize(5)
            .txPoolLimitByAccountPercentage(Fraction.fromFloat(1f))
            .build();
    final PendingTransactions pendingTransactions =
        new BaseFeePendingTransactionsSorter(
            poolConf,
            TestClock.system(ZoneId.systemDefault()),
            metricsSystem,
            blockchain::getChainHeadHeader);

    final TransactionPool transactionPool =
        new TransactionPool(
            () -> pendingTransactions,
            protocolSchedule,
            protocolContext,
            mock(TransactionBroadcaster.class),
            ethContext,
            miningParameters,
            new TransactionPoolMetrics(metricsSystem),
            poolConf,
            null);
    transactionPool.setEnabled();
    return transactionPool;
  }

  @Test
  public void eip1559TransactionCurrentGasPriceLessThanMinimumIsSkippedAndKeptInThePool() {
    final ProcessableBlockHeader blockHeader = createBlock(301_000, Wei.ONE);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            createMiningParameters(
                Wei.of(6), MIN_OCCUPANCY_80_PERCENT, DEFAULT_TX_SELECTION_MAX_TIME),
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            null);

    // tx is willing to pay max 7 wei for gas, but current network condition (baseFee == 1)
    // result in it paying 2 wei, that is below the minimum accepted by the node, so it is skipped
    final Transaction tx = createEIP1559Transaction(1, Wei.of(7L), Wei.ONE, 100_000);
    final var addResults = transactionPool.addRemoteTransactions(List.of(tx));
    assertThat(addResults).extractingByKey(tx.getHash()).isEqualTo(ValidationResult.valid());

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions()).isEmpty();
    assertThat(results.getNotSelectedTransactions())
        .containsOnly(entry(tx, TransactionSelectionResult.CURRENT_TX_PRICE_BELOW_MIN));
    assertThat(transactionPool.count()).isEqualTo(1);
  }

  @Test
  public void eip1559TransactionCurrentGasPriceGreaterThanMinimumIsSelected() {
    final ProcessableBlockHeader blockHeader = createBlock(301_000, Wei.of(5));

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            createMiningParameters(
                Wei.of(6), MIN_OCCUPANCY_80_PERCENT, DEFAULT_TX_SELECTION_MAX_TIME),
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            null);

    // tx is willing to pay max 7 wei for gas, and current network condition (baseFee == 5)
    // result in it paying the max, that is >= the minimum accepted by the node, so it is selected
    final Transaction tx = createEIP1559Transaction(1, Wei.of(7), Wei.ONE, 100_000);
    transactionPool.addRemoteTransactions(List.of(tx));

    ensureTransactionIsValid(tx);

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions()).containsExactly(tx);
    assertThat(results.getNotSelectedTransactions()).isEmpty();
  }

  @Test
  public void eip1559LocalTransactionCurrentGasPriceLessThanMinimumIsSelected() {
    final ProcessableBlockHeader blockHeader = createBlock(301_000, Wei.ONE);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            createMiningParameters(
                Wei.of(6), MIN_OCCUPANCY_80_PERCENT, DEFAULT_TX_SELECTION_MAX_TIME),
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            null);

    // tx is willing to pay max 7 wei for gas, but current network condition (baseFee == 1)
    // result in it paying 2 wei, that is below the minimum accepted by the node, but since it is
    // a local sender it is accepted anyway
    final Transaction tx = createEIP1559Transaction(1, Wei.of(7L), Wei.ONE, 100_000);
    final var addResult = transactionPool.addTransactionViaApi(tx);
    assertThat(addResult.isValid()).isTrue();

    ensureTransactionIsValid(tx);

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions()).containsExactly(tx);
    assertThat(results.getNotSelectedTransactions()).isEmpty();
  }

  @Test
  public void transactionFromSameSenderWithMixedTypes() {
    final ProcessableBlockHeader blockHeader = createBlock(5_000_000);

    final Transaction txFrontier1 = createTransaction(0, Wei.of(7L), 100_000);
    final Transaction txLondon1 = createEIP1559Transaction(1, Wei.ONE, Wei.ONE, 100_000);
    final Transaction txFrontier2 = createTransaction(2, Wei.of(7L), 100_000);
    final Transaction txLondon2 = createEIP1559Transaction(3, Wei.ONE, Wei.ONE, 100_000);

    ensureTransactionIsValid(txFrontier1);
    ensureTransactionIsValid(txLondon1);
    ensureTransactionIsValid(txFrontier2);
    ensureTransactionIsValid(txLondon2);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            defaultTestMiningParameters,
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            null);

    transactionPool.addRemoteTransactions(List.of(txFrontier1, txLondon1, txFrontier2, txLondon2));

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions())
        .containsExactly(txFrontier1, txLondon1, txFrontier2, txLondon2);
    assertThat(results.getNotSelectedTransactions()).isEmpty();
  }
}
