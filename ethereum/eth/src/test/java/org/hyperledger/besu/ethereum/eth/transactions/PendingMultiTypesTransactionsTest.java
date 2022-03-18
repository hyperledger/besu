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
package org.hyperledger.besu.ethereum.eth.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithm;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.AbstractPendingTransactionsSorter.TransactionSelectionResult;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.metrics.StubMetricsSystem;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.testutil.TestClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import org.junit.Test;

public class PendingMultiTypesTransactionsTest {

  private static final int MAX_TRANSACTIONS = 5;
  private static final Supplier<SignatureAlgorithm> SIGNATURE_ALGORITHM =
      Suppliers.memoize(SignatureAlgorithmFactory::getInstance)::get;
  private static final KeyPair KEYS1 = SIGNATURE_ALGORITHM.get().generateKeyPair();
  private static final KeyPair KEYS2 = SIGNATURE_ALGORITHM.get().generateKeyPair();
  private static final KeyPair KEYS3 = SIGNATURE_ALGORITHM.get().generateKeyPair();
  private static final String ADDED_COUNTER = "transactions_added_total";
  private static final String REMOTE = "remote";
  private static final String LOCAL = "local";

  private final BlockHeader blockHeader = mock(BlockHeader.class);

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final BaseFeePendingTransactionsSorter transactions =
      new BaseFeePendingTransactionsSorter(
          TransactionPoolConfiguration.DEFAULT_TX_RETENTION_HOURS,
          MAX_TRANSACTIONS,
          TestClock.fixed(),
          metricsSystem,
          () -> mockBlockHeader(Wei.of(7L)),
          TransactionPoolConfiguration.DEFAULT_PRICE_BUMP);

  @Test
  public void shouldReturnExclusivelyLocal1559TransactionsWhenAppropriate() {
    final Transaction localTransaction0 = create1559Transaction(0, 19, 20, KEYS1);
    transactions.addLocalTransaction(localTransaction0);
    assertThat(transactions.size()).isEqualTo(1);

    List<Transaction> localTransactions = transactions.getLocalTransactions();
    assertThat(localTransactions.size()).isEqualTo(1);

    final Transaction remoteTransaction1 = create1559Transaction(1, 19, 20, KEYS1);
    transactions.addRemoteTransaction(remoteTransaction1);
    assertThat(transactions.size()).isEqualTo(2);

    localTransactions = transactions.getLocalTransactions();
    assertThat(localTransactions.size()).isEqualTo(1);
  }

  @Test
  public void shouldReplaceTransactionWithLowestMaxFeePerGas() {
    final Transaction localTransaction0 = create1559Transaction(0, 200, 20, KEYS1);
    final Transaction localTransaction1 = create1559Transaction(1, 190, 20, KEYS1);
    final Transaction localTransaction2 = create1559Transaction(2, 220, 20, KEYS1);
    final Transaction localTransaction3 = create1559Transaction(3, 240, 20, KEYS1);
    final Transaction localTransaction4 = create1559Transaction(4, 260, 20, KEYS1);
    final Transaction localTransaction5 = create1559Transaction(5, 900, 20, KEYS1);
    transactions.addLocalTransaction(localTransaction0);
    transactions.addLocalTransaction(localTransaction1);
    transactions.addLocalTransaction(localTransaction2);
    transactions.addLocalTransaction(localTransaction3);
    transactions.addLocalTransaction(localTransaction4);

    transactions.updateBaseFee(Wei.of(300L));

    transactions.addLocalTransaction(localTransaction5);
    assertThat(transactions.size()).isEqualTo(5);

    transactions.selectTransactions(
        transaction -> {
          assertThat(transaction.getNonce()).isNotEqualTo(1);
          return TransactionSelectionResult.CONTINUE;
        });
  }

  @Test
  public void shouldEvictTransactionWithLowestMaxFeePerGasAndLowestTip() {
    final Transaction localTransaction0 = create1559Transaction(0, 200, 20, KEYS1);
    final Transaction localTransaction1 = create1559Transaction(1, 200, 19, KEYS1);
    final Transaction localTransaction2 = create1559Transaction(2, 200, 18, KEYS1);
    final Transaction localTransaction3 = create1559Transaction(3, 240, 20, KEYS1);
    final Transaction localTransaction4 = create1559Transaction(4, 260, 20, KEYS1);
    final Transaction localTransaction5 = create1559Transaction(5, 900, 20, KEYS1);
    transactions.addLocalTransaction(localTransaction0);
    transactions.addLocalTransaction(localTransaction1);
    transactions.addLocalTransaction(localTransaction2);
    transactions.addLocalTransaction(localTransaction3);
    transactions.addLocalTransaction(localTransaction4);
    transactions.addLocalTransaction(localTransaction5); // causes eviction

    assertThat(transactions.size()).isEqualTo(5);

    transactions.selectTransactions(
        transaction -> {
          assertThat(transaction.getNonce()).isNotEqualTo(2);
          return TransactionSelectionResult.CONTINUE;
        });
  }

  @Test
  public void shouldEvictLegacyTransactionWithLowestEffectiveMaxPriorityFeePerGas() {
    final Transaction localTransaction0 = create1559Transaction(0, 200, 20, KEYS1);
    final Transaction localTransaction1 = createLegacyTransaction(1, 25, KEYS1);
    final Transaction localTransaction2 = create1559Transaction(2, 200, 18, KEYS1);
    final Transaction localTransaction3 = create1559Transaction(3, 240, 20, KEYS1);
    final Transaction localTransaction4 = create1559Transaction(4, 260, 20, KEYS1);
    final Transaction localTransaction5 = create1559Transaction(5, 900, 20, KEYS1);
    transactions.addLocalTransaction(localTransaction0);
    transactions.addLocalTransaction(localTransaction1);
    transactions.addLocalTransaction(localTransaction2);
    transactions.addLocalTransaction(localTransaction3);
    transactions.addLocalTransaction(localTransaction4);
    transactions.addLocalTransaction(localTransaction5); // causes eviction
    assertThat(transactions.size()).isEqualTo(5);

    transactions.selectTransactions(
        transaction -> {
          assertThat(transaction.getNonce()).isNotEqualTo(1);
          return TransactionSelectionResult.CONTINUE;
        });
  }

  @Test
  public void shouldEvictEIP1559TransactionWithLowestEffectiveMaxPriorityFeePerGas() {
    final Transaction localTransaction0 = create1559Transaction(0, 200, 20, KEYS1);
    final Transaction localTransaction1 = createLegacyTransaction(1, 26, KEYS1);
    final Transaction localTransaction2 = create1559Transaction(2, 200, 18, KEYS1);
    final Transaction localTransaction3 = create1559Transaction(3, 240, 20, KEYS1);
    final Transaction localTransaction4 = create1559Transaction(4, 260, 20, KEYS1);
    final Transaction localTransaction5 = create1559Transaction(5, 900, 20, KEYS1);
    transactions.addLocalTransaction(localTransaction0);
    transactions.addLocalTransaction(localTransaction1);
    transactions.addLocalTransaction(localTransaction2);
    transactions.addLocalTransaction(localTransaction3);
    transactions.addLocalTransaction(localTransaction4);
    transactions.addLocalTransaction(localTransaction5); // causes eviction
    assertThat(transactions.size()).isEqualTo(5);

    transactions.selectTransactions(
        transaction -> {
          assertThat(transaction.getNonce()).isNotEqualTo(2);
          return TransactionSelectionResult.CONTINUE;
        });
  }

  @Test
  public void shouldChangePriorityWhenBaseFeeIncrease() {
    final Transaction localTransaction0 = create1559Transaction(1, 200, 18, KEYS1);
    final Transaction localTransaction1 = create1559Transaction(1, 100, 20, KEYS2);
    final Transaction localTransaction2 = create1559Transaction(2, 100, 19, KEYS2);

    transactions.addLocalTransaction(localTransaction0);
    transactions.addLocalTransaction(localTransaction1);
    transactions.addLocalTransaction(localTransaction2);

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(iterationOrder)
        .containsExactly(localTransaction1, localTransaction2, localTransaction0);

    transactions.updateBaseFee(Wei.of(110L));

    final List<Transaction> iterationOrderAfterBaseIncreased = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrderAfterBaseIncreased.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(iterationOrderAfterBaseIncreased)
        .containsExactly(localTransaction0, localTransaction1, localTransaction2);
  }

  @Test
  public void shouldChangePriorityWhenBaseFeeDecrease() {
    final Transaction localTransaction0 = create1559Transaction(1, 200, 18, KEYS1);
    final Transaction localTransaction1 = create1559Transaction(1, 100, 20, KEYS2);
    final Transaction localTransaction2 = create1559Transaction(2, 100, 19, KEYS2);

    transactions.updateBaseFee(Wei.of(110L));

    transactions.addLocalTransaction(localTransaction0);
    transactions.addLocalTransaction(localTransaction1);
    transactions.addLocalTransaction(localTransaction2);

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(iterationOrder)
        .containsExactly(localTransaction0, localTransaction1, localTransaction2);

    transactions.updateBaseFee(Wei.of(50L));

    final List<Transaction> iterationOrderAfterBaseIncreased = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrderAfterBaseIncreased.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(iterationOrderAfterBaseIncreased)
        .containsExactly(localTransaction1, localTransaction2, localTransaction0);
  }

  @Test
  public void shouldCorrectlyPrioritizeMultipleTransactionTypesBasedOnNonce() {
    final Transaction localTransaction0 = create1559Transaction(1, 200, 18, KEYS1);
    final Transaction localTransaction1 = create1559Transaction(1, 100, 20, KEYS2);
    final Transaction localTransaction2 = create1559Transaction(2, 100, 19, KEYS2);
    final Transaction localTransaction3 = createLegacyTransaction(0, 20, KEYS1);

    transactions.addLocalTransaction(localTransaction0);
    transactions.addLocalTransaction(localTransaction1);
    transactions.addLocalTransaction(localTransaction2);
    transactions.addLocalTransaction(localTransaction3);

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(iterationOrder)
        .containsExactly(
            localTransaction1, localTransaction2, localTransaction3, localTransaction0);
  }

  @Test
  public void shouldCorrectlyPrioritizeMultipleTransactionTypesBasedOnGasPayed() {
    final Transaction localTransaction0 = create1559Transaction(0, 100, 19, KEYS2);
    final Transaction localTransaction1 = createLegacyTransaction(0, 2000, KEYS1);
    final Transaction localTransaction2 = createLegacyTransaction(0, 20, KEYS3);
    final Transaction localTransaction3 = createLegacyTransaction(1, 2000, KEYS3);

    transactions.addLocalTransaction(localTransaction0);
    transactions.addLocalTransaction(localTransaction1);
    transactions.addLocalTransaction(localTransaction2);
    transactions.addLocalTransaction(localTransaction3);

    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(iterationOrder)
        .containsExactly(
            localTransaction1, localTransaction0, localTransaction2, localTransaction3);
  }

  @Test
  public void shouldSelectNoTransactionsIfPoolEmpty() {
    final List<Transaction> iterationOrder = new ArrayList<>();
    transactions.selectTransactions(
        transaction -> {
          iterationOrder.add(transaction);
          return TransactionSelectionResult.CONTINUE;
        });

    assertThat(iterationOrder).isEmpty();
  }

  @Test
  public void shouldAdd1559Transaction() {
    final Transaction remoteTransaction0 = create1559Transaction(0, 19, 20, KEYS1);
    transactions.addRemoteTransaction(remoteTransaction0);
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);

    final Transaction remoteTransaction1 = create1559Transaction(1, 19, 20, KEYS1);
    transactions.addRemoteTransaction(remoteTransaction1);
    assertThat(transactions.size()).isEqualTo(2);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(2);
  }

  @Test
  public void shouldNotIncrementAddedCounterWhenRemote1559TransactionAlreadyPresent() {
    final Transaction localTransaction0 = create1559Transaction(0, 19, 20, KEYS1);
    transactions.addLocalTransaction(localTransaction0);
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(0);

    assertThat(transactions.addRemoteTransaction(localTransaction0)).isFalse();
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, LOCAL)).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(0);
  }

  @Test
  public void shouldAddMixedTransactions() {
    final Transaction remoteTransaction0 = create1559Transaction(0, 19, 20, KEYS1);
    transactions.addRemoteTransaction(remoteTransaction0);
    assertThat(transactions.size()).isEqualTo(1);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(1);

    final Transaction remoteTransaction1 = createLegacyTransaction(1, 5000, KEYS1);
    transactions.addRemoteTransaction(remoteTransaction1);
    assertThat(transactions.size()).isEqualTo(2);
    assertThat(metricsSystem.getCounterValue(ADDED_COUNTER, REMOTE)).isEqualTo(2);
  }

  private Transaction create1559Transaction(
      final long transactionNumber,
      final long maxFeePerGas,
      final long maxPriorityFeePerGas,
      final KeyPair keyPair) {
    return new TransactionTestFixture()
        .type(TransactionType.EIP1559)
        .value(Wei.of(transactionNumber))
        .nonce(transactionNumber)
        .maxFeePerGas(Optional.of(Wei.of(maxFeePerGas)))
        .maxPriorityFeePerGas(Optional.of(Wei.of(maxPriorityFeePerGas)))
        .createTransaction(keyPair);
  }

  private Transaction createLegacyTransaction(
      final long transactionNumber, final long gasPrice, final KeyPair keyPair) {
    return new TransactionTestFixture()
        .value(Wei.of(transactionNumber))
        .gasPrice(Wei.of(gasPrice))
        .nonce(transactionNumber)
        .createTransaction(keyPair);
  }

  private BlockHeader mockBlockHeader(final Wei baseFee) {
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(baseFee));
    return blockHeader;
  }
}
