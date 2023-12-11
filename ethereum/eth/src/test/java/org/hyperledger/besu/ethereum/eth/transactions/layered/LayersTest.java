/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.eth.transactions.layered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.LayersTest.Sender.S1;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.LayersTest.Sender.S2;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.LayersTest.Sender.S3;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.LayersTest.Sender.S4;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.LayersTest.Sender.SP1;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.LayersTest.Sender.SP2;
import static org.hyperledger.besu.ethereum.eth.transactions.layered.TransactionsLayer.RemovalReason.INVALIDATED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.eth.transactions.BlobCache;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolReplacementHandler;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.account.Account;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class LayersTest extends BaseTransactionPoolTest {
  private static final int MAX_PRIO_TRANSACTIONS = 3;
  private static final int MAX_FUTURE_FOR_SENDER = 10;

  private final TransactionPoolConfiguration poolConfig =
      ImmutableTransactionPoolConfiguration.builder()
          .maxPrioritizedTransactions(MAX_PRIO_TRANSACTIONS)
          .maxFutureBySender(MAX_FUTURE_FOR_SENDER)
          .pendingTransactionsLayerMaxCapacityBytes(
              new PendingTransaction.Remote(createEIP1559Transaction(0, KEYS1, 1)).memorySize() * 3)
          .build();

  private final TransactionPoolMetrics txPoolMetrics = new TransactionPoolMetrics(metricsSystem);

  private final EvictCollectorLayer evictCollector = new EvictCollectorLayer(txPoolMetrics);
  private final SparseTransactions sparseTransactions =
      new SparseTransactions(
          poolConfig,
          evictCollector,
          txPoolMetrics,
          this::transactionReplacementTester,
          new BlobCache());

  private final ReadyTransactions readyTransactions =
      new ReadyTransactions(
          poolConfig,
          sparseTransactions,
          txPoolMetrics,
          this::transactionReplacementTester,
          new BlobCache());

  private final BaseFeePrioritizedTransactions prioritizedTransactions =
      new BaseFeePrioritizedTransactions(
          poolConfig,
          LayersTest::mockBlockHeader,
          readyTransactions,
          txPoolMetrics,
          this::transactionReplacementTester,
          FeeMarket.london(0L),
          new BlobCache());

  private final LayeredPendingTransactions pendingTransactions =
      new LayeredPendingTransactions(poolConfig, prioritizedTransactions);

  @AfterEach
  void reset() {
    pendingTransactions.reset();
  }

  @ParameterizedTest
  @MethodSource("providerAddTransactions")
  void addTransactions(final Scenario scenario) {
    assertScenario(scenario);
  }

  @ParameterizedTest
  @MethodSource("providerAddTransactionsMultipleSenders")
  void addTransactionsMultipleSenders(final Scenario scenario) {
    assertScenario(scenario);
  }

  @ParameterizedTest
  @MethodSource("providerRemoveTransactions")
  void removeTransactions(final Scenario scenario) {
    assertScenario(scenario);
  }

  @ParameterizedTest
  @MethodSource("providerInterleavedAddRemoveTransactions")
  void interleavedAddRemoveTransactions(final Scenario scenario) {
    assertScenario(scenario);
  }

  @ParameterizedTest
  @MethodSource("providerBlockAdded")
  void removeConfirmedTransactions(final Scenario scenario) {
    assertScenario(scenario);
  }

  @ParameterizedTest
  @MethodSource("providerNextNonceForSender")
  void nextNonceForSender(final Scenario scenario) {
    assertScenario(scenario);
  }

  @ParameterizedTest
  @MethodSource("providerSelectTransactions")
  void selectTransactions(final Scenario scenario) {
    assertScenario(scenario);
  }

  @ParameterizedTest
  @MethodSource("providerReorg")
  void reorg(final Scenario scenario) {
    assertScenario(scenario);
  }

  @ParameterizedTest
  @MethodSource("providerAsyncWorldStateUpdates")
  void asyncWorldStateUpdates(final Scenario scenario) {
    assertScenario(scenario);
  }

  @ParameterizedTest
  @MethodSource("providerPrioritySenders")
  void prioritySenders(final Scenario scenario) {
    assertScenario(scenario);
  }

  private void assertScenario(final Scenario scenario) {
    scenario.execute(
        pendingTransactions,
        prioritizedTransactions,
        readyTransactions,
        sparseTransactions,
        evictCollector);
  }

  static Stream<Arguments> providerAddTransactions() {
    return Stream.of(
        Arguments.of(
            new Scenario("add first").addForSender(S1, 0).expectedPrioritizedForSender(S1, 0)),
        Arguments.of(
            new Scenario("add first sparse").addForSender(S1, 1).expectedSparseForSender(S1, 1)),
        Arguments.of(
            new Scenario("fill prioritized")
                .addForSender(S1, 0, 1, 2)
                .expectedPrioritizedForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("fill prioritized reverse")
                .addForSender(S1, 2, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("fill prioritized mixed order 1 step by step")
                .addForSender(S1, 2)
                .expectedSparseForSender(S1, 2)
                .addForSender(S1, 0)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 2)
                .addForSender(S1, 1)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedSparseForSenders()),
        Arguments.of(
            new Scenario("fill prioritized mixed order 2")
                .addForSender(S1, 0, 2, 1)
                .expectedPrioritizedForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("overflow to ready")
                .addForSender(S1, 0, 1, 2, 3)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)),
        Arguments.of(
            new Scenario("overflow to ready reverse")
                .addForSender(S1, 3, 2, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)),
        Arguments.of(
            new Scenario("overflow to ready mixed order 1")
                .addForSender(S1, 3, 0, 2, 1)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)),
        Arguments.of(
            new Scenario("overflow to ready mixed order 2")
                .addForSender(S1, 0, 3, 1, 2)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)),
        Arguments.of(
            new Scenario("overflow to sparse")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse reverse")
                .addForSender(S1, 6, 5, 4, 3, 2, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                // 4,5,6 are evicted since max capacity of sparse layer is 3 txs
                .expectedReadyForSender(S1, 3)
                .expectedDroppedForSender(S1, 4, 5, 6)),
        Arguments.of(
            new Scenario("overflow to sparse mixed order 1")
                .addForSender(S1, 6, 0, 4, 1, 3, 2, 5)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse mixed order 2")
                .addForSender(S1, 0, 4, 6, 1, 5, 2, 3)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse mixed order 3")
                .addForSender(S1, 0, 1, 2, 3, 5, 6, 4)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6)),
        Arguments.of(
            new Scenario("nonce gap to sparse 1")
                .addForSender(S1, 0, 2)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 2)),
        Arguments.of(
            new Scenario("nonce gap to sparse 2")
                .addForSender(S1, 0, 1, 2, 3, 5)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)
                .expectedSparseForSender(S1, 5)),
        Arguments.of(
            new Scenario("fill sparse 1")
                .addForSender(S1, 2, 3, 5)
                .expectedSparseForSender(S1, 2, 3, 5)),
        Arguments.of(
            new Scenario("fill sparse 2")
                .addForSender(S1, 5, 3, 2)
                .expectedSparseForSender(S1, 5, 3, 2)),
        Arguments.of(
            new Scenario("overflow sparse 1")
                .addForSender(S1, 1, 2, 3, 4)
                .expectedSparseForSender(S1, 1, 2, 3)
                .expectedDroppedForSender(S1, 4)),
        Arguments.of(
            new Scenario("overflow sparse 2")
                .addForSender(S1, 4, 2, 3, 1)
                .expectedSparseForSender(S1, 2, 3, 1)
                .expectedDroppedForSender(S1, 4)),
        Arguments.of(
            new Scenario("overflow sparse 3")
                .addForSender(S1, 0, 4, 2, 3, 5)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 4, 2, 3)
                .expectedDroppedForSender(S1, 5)));
  }

  static Stream<Arguments> providerAddTransactionsMultipleSenders() {
    return Stream.of(
        Arguments.of(
            new Scenario("add first")
                .addForSenders(S1, 0, S2, 0)
                .expectedPrioritizedForSenders(S2, 0, S1, 0)),
        Arguments.of(
            new Scenario("add first sparse")
                .addForSenders(S1, 1, S2, 2)
                .expectedSparseForSenders(S1, 1, S2, 2)),
        Arguments.of(
            new Scenario("fill prioritized 1")
                .addForSender(S1, 0, 1, 2)
                .addForSender(S2, 0, 1, 2)
                .expectedPrioritizedForSender(S2, 0, 1, 2)
                .expectedReadyForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("fill prioritized 2")
                .addForSender(S2, 0, 1, 2)
                .addForSender(S1, 0, 1, 2)
                .expectedPrioritizedForSender(S2, 0, 1, 2)
                .expectedReadyForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("fill prioritized 3")
                .addForSenders(S1, 0, S2, 0, S1, 1, S2, 1, S1, 2, S2, 2)
                .expectedPrioritizedForSender(S2, 0, 1, 2)
                .expectedReadyForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("fill prioritized mixed order")
                .addForSenders(S1, 2, S2, 1)
                .expectedPrioritizedForSenders()
                .expectedReadyForSenders()
                .expectedSparseForSenders(S1, 2, S2, 1)
                .addForSenders(S2, 2, S1, 0)
                .expectedPrioritizedForSender(S1, 0)
                .expectedReadyForSenders()
                .expectedSparseForSenders(S1, 2, S2, 1, S2, 2)
                .addForSenders(S1, 1)
                .expectedPrioritizedForSenders(S1, 0, S1, 1, S1, 2)
                .expectedReadyForSenders()
                .expectedSparseForSenders(S2, 1, S2, 2)
                .addForSenders(S2, 0)
                // only S2[0] is prioritized because there is no space to try to fill gaps
                .expectedPrioritizedForSenders(S2, 0, S1, 0, S1, 1)
                .expectedReadyForSender(S1, 2)
                .expectedSparseForSenders(S2, 1, S2, 2)
                // confirm some S1 txs will promote S2 txs
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSenders(S2, 0, S2, 1, S1, 1)
                .expectedReadyForSenders(S2, 2, S1, 2)
                .expectedSparseForSenders()
                .confirmedForSenders(S1, 1)
                .expectedPrioritizedForSenders(S2, 0, S2, 1, S2, 2)
                .expectedReadyForSender(S1, 2)
                .expectedSparseForSenders()
                .confirmedForSenders(S2, 1)
                .expectedPrioritizedForSenders(S2, 2, S1, 2)
                .expectedReadyForSenders()
                .expectedSparseForSenders()),
        Arguments.of(
            new Scenario("overflow to ready 1")
                .addForSenders(S1, 0, S1, 1, S2, 0, S2, 1)
                .expectedPrioritizedForSenders(S2, 0, S2, 1, S1, 0)
                .expectedReadyForSender(S1, 1)),
        Arguments.of(
            new Scenario("overflow to ready 2")
                .addForSenders(S2, 0, S2, 1, S1, 0, S1, 1)
                .expectedPrioritizedForSenders(S2, 0, S2, 1, S1, 0)
                .expectedReadyForSender(S1, 1)),
        Arguments.of(
            new Scenario("overflow to ready 3")
                .addForSenders(S1, 0, S2, 0, S2, 1, S1, 1)
                .expectedPrioritizedForSenders(S2, 0, S2, 1, S1, 0)
                .expectedReadyForSender(S1, 1)),
        Arguments.of(
            new Scenario("overflow to ready reverse 1")
                .addForSenders(S1, 1, S1, 0, S2, 1, S2, 0)
                .expectedPrioritizedForSenders(S2, 0, S2, 1, S1, 0)
                .expectedReadyForSender(S1, 1)),
        Arguments.of(
            new Scenario("overflow to ready reverse 2")
                .addForSenders(S1, 1, S2, 1, S2, 0, S1, 0)
                .expectedPrioritizedForSenders(S2, 0, S2, 1, S1, 0)
                .expectedReadyForSender(S1, 1)),
        Arguments.of(
            new Scenario("overflow to ready mixed")
                .addForSenders(S2, 1, S1, 0, S1, 1, S2, 0)
                .expectedPrioritizedForSenders(S2, 0, S2, 1, S1, 0)
                .expectedReadyForSender(S1, 1)),
        Arguments.of(
            new Scenario("overflow to sparse")
                .addForSenders(S1, 0, S1, 1, S2, 0, S2, 1, S3, 0, S3, 1, S3, 2)
                .expectedPrioritizedForSender(S3, 0, 1, 2)
                .expectedReadyForSenders(S2, 0, S2, 1, S1, 0)
                .expectedSparseForSender(S1, 1)),
        Arguments.of(
            new Scenario("overflow to sparse reverse")
                .addForSenders(S3, 2, S3, 1, S3, 0, S2, 1, S2, 0, S1, 1, S1, 0)
                .expectedPrioritizedForSender(S3, 0, 1, 2)
                .expectedReadyForSenders(S2, 0, S2, 1, S1, 0)
                .expectedSparseForSender(S1, 1)),
        Arguments.of(
            new Scenario("overflow to sparse mixed")
                .addForSenders(S2, 0, S3, 2, S1, 1)
                .expectedPrioritizedForSender(S2, 0)
                .expectedReadyForSenders()
                .expectedSparseForSenders(S3, 2, S1, 1)
                .addForSenders(S2, 1)
                .expectedPrioritizedForSenders(S2, 0, S2, 1)
                .expectedReadyForSenders()
                .expectedSparseForSenders(S3, 2, S1, 1)
                .addForSenders(S3, 0)
                .expectedPrioritizedForSenders(S3, 0, S2, 0, S2, 1)
                .expectedReadyForSenders()
                .expectedSparseForSenders(S3, 2, S1, 1)
                .addForSenders(S1, 0)
                .expectedPrioritizedForSenders(S3, 0, S2, 0, S2, 1)
                .expectedReadyForSenders(S1, 0, S1, 1)
                .expectedSparseForSender(S3, 2)
                .addForSenders(S3, 1)
                // ToDo: only S3[1] is prioritized because there is no space to try to fill gaps
                .expectedPrioritizedForSenders(S3, 0, S3, 1, S2, 0)
                .expectedReadyForSenders(S2, 1, S1, 0, S1, 1)
                .expectedSparseForSender(S3, 2)
                .addForSenders(S4, 0, S4, 1, S3, 3)
                .expectedPrioritizedForSenders(S4, 0, S4, 1, S3, 0)
                .expectedReadyForSenders(S3, 1, S2, 0, S2, 1)
                .expectedSparseForSenders(S3, 2, S1, 1, S1, 0)
                // ToDo: non optimal discard, worth to improve?
                .expectedDroppedForSender(S3, 3)),
        Arguments.of(
            new Scenario("replacement cross layer")
                .addForSenders(S2, 0, S3, 2, S1, 1, S2, 1, S3, 0, S1, 0, S3, 1)
                // ToDo: only S3[1] is prioritized because there is no space to try to fill gaps
                .expectedPrioritizedForSenders(S3, 0, S3, 1, S2, 0)
                .expectedReadyForSenders(S2, 1, S1, 0, S1, 1)
                .expectedSparseForSender(S3, 2)
                .addForSenders(S3, 2) // added in prioritized, but replacement in sparse
                .expectedPrioritizedForSenders(S3, 0, S3, 1, S3, 2)
                .expectedReadyForSenders(S2, 0, S2, 1, S1, 0)
                .expectedSparseForSender(S1, 1)));
  }

  static Stream<Arguments> providerRemoveTransactions() {
    return Stream.of(
        Arguments.of(new Scenario("remove not existing").removeForSender(S1, 0)),
        Arguments.of(new Scenario("add/remove first").addForSender(S1, 0).removeForSender(S1, 0)),
        Arguments.of(
            new Scenario("add/remove first sparse").addForSender(S1, 1).removeForSender(S1, 1)),
        Arguments.of(
            new Scenario("fill/remove prioritized 1")
                .addForSender(S1, 0, 1, 2)
                .removeForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("fill/remove prioritized 2")
                .addForSender(S1, 0, 1, 2)
                .removeForSender(S1, 2, 1, 0)),
        Arguments.of(
            new Scenario("fill/remove prioritized reverse 1")
                .addForSender(S1, 2, 1, 0)
                .removeForSender(S1, 0, 1, 2)),
        Arguments.of(
            new Scenario("fill/remove prioritized reverse 2")
                .addForSender(S1, 2, 1, 0)
                .removeForSender(S1, 2, 1, 0)),
        Arguments.of(
            new Scenario("fill/remove first prioritized")
                .addForSender(S1, 0, 1, 2)
                .removeForSender(S1, 0)
                .expectedSparseForSender(S1, 1, 2)),
        Arguments.of(
            new Scenario("fill/remove last prioritized")
                .addForSender(S1, 0, 1, 2)
                .removeForSender(S1, 2)
                .expectedPrioritizedForSender(S1, 0, 1)),
        Arguments.of(
            new Scenario("fill/remove middle prioritized")
                .addForSender(S1, 0, 1, 2)
                .removeForSender(S1, 1)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 2)),
        Arguments.of(
            new Scenario("overflow to ready then remove 1")
                .addForSender(S1, 0, 1, 2, 3)
                .removeForSender(S1, 2)
                .expectedPrioritizedForSender(S1, 0, 1)
                .expectedSparseForSender(S1, 3)),
        Arguments.of(
            new Scenario("overflow to ready then remove 2")
                .addForSender(S1, 0, 1, 2, 3, 4)
                .removeForSender(S1, 4)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)),
        Arguments.of(
            new Scenario("overflow to ready then remove 3")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .removeForSender(S1, 4)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)
                .expectedSparseForSender(S1, 5)),
        Arguments.of(
            new Scenario("overflow to ready then remove 4")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .removeForSender(S1, 3)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedSparseForSender(S1, 4, 5)),
        Arguments.of(
            new Scenario("overflow to ready then remove 5")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .removeForSender(S1, 0)
                .expectedSparseForSender(S1, 1, 2, 3)
                .expectedDroppedForSender(S1, 4, 5)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 1")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .removeForSender(S1, 6)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 2")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .removeForSender(S1, 5)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4)
                .expectedSparseForSender(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 3")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .removeForSender(S1, 2)
                .expectedPrioritizedForSender(S1, 0, 1)
                .expectedSparseForSender(S1, 3, 4, 5)
                .expectedDroppedForSender(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 4")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 7, 8)
                .removeForSender(S1, 7)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6, 8)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 5")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 7, 8)
                .removeForSender(S1, 6)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 7, 8)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 6")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 7, 8)
                .removeForSender(S1, 6)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 7, 8)),
        Arguments.of(
            new Scenario("overflow to sparse then remove 7")
                .setAccountNonce(S1, 1)
                .addForSender(S1, 1, 2, 3, 4, 5, 8)
                .removeForSender(S1, 2)
                .expectedPrioritizedForSender(S1, 1)
                .expectedSparseForSender(S1, 3, 4, 5)
                .expectedDroppedForSender(S1, 8)));
  }

  static Stream<Arguments> providerInterleavedAddRemoveTransactions() {
    return Stream.of(
        Arguments.of(
            new Scenario("interleaved add/remove 1")
                .addForSender(S1, 0)
                .removeForSender(S1, 0)
                .addForSender(S1, 0)
                .expectedPrioritizedForSender(S1, 0)),
        Arguments.of(
            new Scenario("interleaved add/remove 2")
                .addForSender(S1, 0)
                .removeForSender(S1, 0)
                .addForSender(S1, 1)
                .expectedSparseForSender(S1, 1)),
        Arguments.of(
            new Scenario("interleaved add/remove 3")
                .addForSender(S1, 0)
                .removeForSender(S1, 0)
                .addForSender(S1, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1)));
  }

  static Stream<Arguments> providerBlockAdded() {
    return Stream.of(
        Arguments.of(new Scenario("confirmed not exist").confirmedForSenders(S1, 0)),
        Arguments.of(
            new Scenario("confirmed below existing lower nonce")
                .addForSender(S1, 1)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1)),
        Arguments.of(
            new Scenario("confirmed only one existing")
                .addForSender(S1, 0)
                .confirmedForSenders(S1, 0)),
        Arguments.of(
            new Scenario("confirmed above existing")
                .addForSender(S1, 0)
                .confirmedForSenders(S1, 1)),
        Arguments.of(
            new Scenario("confirmed some existing 1")
                .addForSender(S1, 0, 1)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1)),
        Arguments.of(
            new Scenario("confirmed some existing 2")
                .addForSender(S1, 0, 1, 2)
                .confirmedForSenders(S1, 1)
                .expectedPrioritizedForSender(S1, 2)),
        Arguments.of(
            new Scenario("overflow to ready and confirmed some existing 1")
                .addForSender(S1, 0, 1, 2, 3)
                .confirmedForSenders(S1, 3)),
        Arguments.of(
            new Scenario("overflow to ready and confirmed some existing 2")
                .addForSender(S1, 0, 1, 2, 3)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1, 2, 3)),
        Arguments.of(
            new Scenario("overflow to ready and confirmed some existing 3")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .confirmedForSenders(S1, 1)
                .expectedPrioritizedForSender(S1, 2, 3, 4)
                .expectedReadyForSender(S1, 5)),
        Arguments.of(
            new Scenario("overflow to ready and confirmed all existing")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .confirmedForSenders(S1, 5)),
        Arguments.of(
            new Scenario("overflow to ready and confirmed above highest nonce")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .confirmedForSenders(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed some existing 1")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1, 2, 3)
                .expectedReadyForSender(S1, 4, 5, 6)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed some existing 2")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .confirmedForSenders(S1, 2)
                .expectedPrioritizedForSender(S1, 3, 4, 5)
                .expectedReadyForSender(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed some existing 3")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .confirmedForSenders(S1, 3)
                .expectedPrioritizedForSender(S1, 4, 5, 6)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed some existing 4")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 7, 8)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1, 2, 3)
                .expectedReadyForSender(S1, 4, 5, 6)
                .expectedSparseForSender(S1, 7, 8)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed some existing with gap")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 7, 8)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1, 2, 3)
                .expectedReadyForSender(S1, 4, 5)
                .expectedSparseForSender(S1, 7, 8)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed all w/o gap")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 7, 8)
                .confirmedForSenders(S1, 8)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed all with gap")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 7, 8)
                .confirmedForSenders(S1, 8)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed all before gap 1")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 7, 8)
                .confirmedForSenders(S1, 5)
                .expectedSparseForSender(S1, 7, 8)),
        Arguments.of(
            new Scenario("overflow to sparse and confirmed all before gap 2")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 7, 8)
                .confirmedForSenders(S1, 6)
                .expectedPrioritizedForSender(S1, 7, 8)));
  }

  static Stream<Arguments> providerNextNonceForSender() {
    return Stream.of(
        Arguments.of(new Scenario("not exist").expectedNextNonceForSenders(S1, null)),
        Arguments.of(
            new Scenario("first transaction")
                .addForSender(S1, 0)
                .expectedPrioritizedForSender(S1, 0)
                .expectedNextNonceForSenders(S1, 1)),
        Arguments.of(
            new Scenario("fill prioritized")
                .addForSender(S1, 0, 1, 2)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedNextNonceForSenders(S1, 3)),
        Arguments.of(
            new Scenario("reverse fill prioritized")
                .addForSender(S1, 2, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedNextNonceForSenders(S1, 3)),
        Arguments.of(
            new Scenario("reverse fill prioritized 2")
                .addForSender(S1, 3, 2, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)
                .expectedNextNonceForSenders(S1, 4)),
        Arguments.of(
            new Scenario("overflow to ready")
                .addForSender(S1, 0, 1, 2, 3)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)
                .expectedNextNonceForSenders(S1, 4)),
        Arguments.of(
            new Scenario("fill ready")
                .addForSender(S1, 0, 1, 2, 3, 4, 5)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedNextNonceForSenders(S1, 6)),
        Arguments.of(
            new Scenario("overflow to sparse")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6)
                .expectedNextNonceForSenders(S1, 7)),
        Arguments.of(
            new Scenario("fill sparse")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 7, 8)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6, 7, 8)
                .expectedNextNonceForSenders(S1, 9)),
        Arguments.of(
            new Scenario("drop transaction")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6, 7, 8)
                .expectedDroppedForSender(S1, 9)
                .expectedNextNonceForSenders(S1, 9)),
        Arguments.of(
            new Scenario("first with gap")
                .addForSender(S1, 1)
                .expectedSparseForSender(S1, 1)
                .expectedNextNonceForSenders(S1, null)),
        Arguments.of(
            new Scenario("sequence with gap 1")
                .addForSender(S1, 0, 2)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 2)
                .expectedNextNonceForSenders(S1, 1)),
        Arguments.of(
            new Scenario("sequence with gap 2")
                .addForSender(S1, 0, 1, 2, 4)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedSparseForSender(S1, 4)
                .expectedNextNonceForSenders(S1, 3)),
        Arguments.of(
            new Scenario("sequence with gap 3")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 7)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 7)
                .expectedNextNonceForSenders(S1, 6)),
        Arguments.of(
            new Scenario("sequence with gap 4")
                .addForSender(S1, 0, 1, 2, 3, 4, 5, 6, 8)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6, 8)
                .expectedNextNonceForSenders(S1, 7)),
        Arguments.of(
            new Scenario("out of order sequence 1")
                .addForSender(S1, 2, 0, 1)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedNextNonceForSenders(S1, 3)),
        Arguments.of(
            new Scenario("out of order sequence 2")
                .addForSender(S1, 2, 0, 4, 3, 1)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4)
                .expectedNextNonceForSenders(S1, 5)),
        Arguments.of(
            new Scenario("out of order sequence with gap 1")
                .addForSender(S1, 2, 1)
                .expectedSparseForSender(S1, 2, 1)
                .expectedNextNonceForSenders(S1, null)),
        Arguments.of(
            new Scenario("out of order sequence with gap 2")
                .addForSender(S1, 2, 0)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 2)
                .expectedNextNonceForSenders(S1, 1)),
        Arguments.of(
            new Scenario("out of order sequence with gap 3")
                .addForSender(S1, 2, 0, 1, 4)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedSparseForSender(S1, 4)
                .expectedNextNonceForSenders(S1, 3)),
        Arguments.of(
            new Scenario("out of order sequence with gap 4")
                .addForSender(S1, 2, 0, 4, 1, 6, 3)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4)
                .expectedSparseForSender(S1, 6)
                .expectedNextNonceForSenders(S1, 5)),
        Arguments.of(
            new Scenario("no gap and confirmed 1")
                .addForSender(S1, 0, 1, 2)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1, 2)
                .expectedNextNonceForSenders(S1, 3)),
        Arguments.of(
            new Scenario("all confirmed 1")
                .addForSender(S1, 0, 1, 2)
                .confirmedForSenders(S1, 2)
                .expectedNextNonceForSenders(S1, null)),
        Arguments.of(
            new Scenario("all confirmed 2")
                .addForSender(S1, 3, 0, 4, 1, 5, 2)
                .confirmedForSenders(S1, 8)
                .expectedNextNonceForSenders(S1, null)),
        Arguments.of(
            new Scenario("all confirmed 3")
                .addForSender(S1, 3, 0, 4, 1, 2, 5, 6, 7)
                .confirmedForSenders(S1, 8)
                .expectedNextNonceForSenders(S1, null)),
        Arguments.of(
            new Scenario("all confirmed step by step 1")
                .addForSender(S1, 3)
                .expectedSparseForSender(S1, 3)
                .expectedNextNonceForSenders(S1, null)
                .addForSender(S1, 0)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 3)
                .expectedNextNonceForSenders(S1, 1)
                .addForSender(S1, 4)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 3, 4)
                .expectedNextNonceForSenders(S1, 1)
                .addForSender(S1, 1)
                .expectedPrioritizedForSender(S1, 0, 1)
                .expectedSparseForSender(S1, 3, 4)
                .expectedNextNonceForSenders(S1, 2)
                .addForSender(S1, 2)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4)
                .expectedNextNonceForSenders(S1, 5)
                .addForSender(S1, 5)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedNextNonceForSenders(S1, 6)
                .addForSender(S1, 6)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6)
                .expectedNextNonceForSenders(S1, 7)
                .addForSender(S1, 7)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6, 7)
                .expectedNextNonceForSenders(S1, 8)
                .addForSender(S1, 8)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6, 7, 8)
                .expectedNextNonceForSenders(S1, 9)
                .addForSender(S1, 9)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6, 7, 8)
                .expectedDroppedForSender(S1, 9)
                .expectedNextNonceForSenders(S1, 9)
                .confirmedForSenders(S1, 9)
                .expectedPrioritizedForSenders()
                .expectedReadyForSenders()
                .expectedSparseForSenders()
                .expectedNextNonceForSenders(S1, null)));
  }

  static Stream<Arguments> providerSelectTransactions() {
    return Stream.of(
        Arguments.of(new Scenario("no transactions").expectedSelectedTransactions()),
        Arguments.of(
            new Scenario("first transaction")
                .addForSender(S1, 0)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSelectedTransactions(S1, 0)),
        Arguments.of(
            new Scenario("fill prioritized")
                .addForSender(S1, 0, 1, 2)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)),
        Arguments.of(
            new Scenario("reverse fill prioritized")
                .addForSender(S1, 2, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)),
        Arguments.of(
            new Scenario("reverse fill prioritized 2")
                .addForSender(S1, 3, 2, 1, 0)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)),
        Arguments.of(
            new Scenario("overflow to ready")
                .addForSender(S1, 0, 1, 2, 3)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)),
        Arguments.of(
            new Scenario("first with gap")
                .addForSender(S1, 1)
                .expectedSparseForSender(S1, 1)
                .expectedSelectedTransactions()),
        Arguments.of(
            new Scenario("sequence with gap 1")
                .addForSender(S1, 0, 2)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 2)
                .expectedSelectedTransactions(S1, 0)),
        Arguments.of(
            new Scenario("sequence with gap 2")
                .addForSender(S1, 0, 1, 2, 4)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedSparseForSender(S1, 4)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)),
        Arguments.of(
            new Scenario("out of order sequence 1")
                .addForSender(S1, 2, 0, 1)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)),
        Arguments.of(
            new Scenario("out of order sequence 2")
                .addForSender(S1, 2, 0, 4, 3, 1)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)),
        Arguments.of(
            new Scenario("out of order sequence with gap 1")
                .addForSender(S1, 2, 1)
                .expectedSparseForSender(S1, 2, 1)
                .expectedSelectedTransactions()),
        Arguments.of(
            new Scenario("out of order sequence with gap 2")
                .addForSender(S1, 2, 0)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 2)
                .expectedSelectedTransactions(S1, 0)),
        Arguments.of(
            new Scenario("no gap and confirmed 1")
                .addForSender(S1, 0, 1, 2)
                .confirmedForSenders(S1, 0)
                .expectedPrioritizedForSender(S1, 1, 2)
                .expectedSelectedTransactions(S1, 1, S1, 2)),
        Arguments.of(
            new Scenario("all confirmed 1")
                .addForSender(S1, 0, 1, 2)
                .confirmedForSenders(S1, 2)
                .expectedSelectedTransactions()),
        Arguments.of(
            new Scenario("all confirmed step by step 1")
                .addForSender(S1, 3)
                .expectedSparseForSender(S1, 3)
                .expectedSelectedTransactions()
                .addForSender(S1, 0)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 3)
                .expectedSelectedTransactions(S1, 0)
                .addForSender(S1, 4)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 3, 4)
                .expectedSelectedTransactions(S1, 0)
                .addForSender(S1, 1)
                .expectedPrioritizedForSender(S1, 0, 1)
                .expectedSparseForSender(S1, 3, 4)
                .expectedSelectedTransactions(S1, 0, S1, 1)
                .addForSender(S1, 2)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)
                .addForSender(S1, 5)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)
                .addForSender(S1, 6)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)
                .addForSender(S1, 7)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6, 7)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)
                .addForSender(S1, 8)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6, 7, 8)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)
                .addForSender(S1, 9)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedReadyForSender(S1, 3, 4, 5)
                .expectedSparseForSender(S1, 6, 7, 8)
                .expectedDroppedForSender(S1, 9)
                .expectedSelectedTransactions(S1, 0, S1, 1, S1, 2)
                .confirmedForSenders(S1, 9)
                .expectedPrioritizedForSenders()
                .expectedReadyForSenders()
                .expectedSparseForSenders()
                .expectedSelectedTransactions()));
  }

  static Stream<Arguments> providerReorg() {
    return Stream.of(
        Arguments.of(
            new Scenario("reorg")
                .setAccountNonce(S1, 0)
                .addForSender(S1, 0, 1, 2)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .confirmedForSenders(S1, 1)
                .expectedPrioritizedForSender(S1, 2)
                .expectedNextNonceForSenders(S1, 3)
                .addForSender(S1, 3)
                .expectedPrioritizedForSender(S1, 2, 3)
                .setAccountNonce(S1, 0) // rewind nonce due to reorg
                .addForSender(S1, 0)
                .expectedPrioritizedForSender(S1, 0)
                .expectedSparseForSender(S1, 2, 3)));
  }

  static Stream<Arguments> providerAsyncWorldStateUpdates() {
    return Stream.of(
        Arguments.of(
            new Scenario("nonce forward just before confirmed block is processed")
                .setAccountNonce(S1, 0)
                .addForSender(S1, 0, 1, 2, 8, 9)
                .expectedPrioritizedForSender(S1, 0, 1, 2)
                .expectedSparseForSender(S1, 8, 9)
                .expectedNextNonceForSenders(S1, 3)
                // block has been imported and world state already has the new nonce,
                // but block has not yet been processed by txpool
                .setAccountNonce(S1, 5)
                .addForSender(S1, 7)
                .expectedPrioritizedForSenders()
                // remember that sparse are checked by oldest first
                .expectedSparseForSender(S1, 8, 9, 7)));
  }

  static Stream<Arguments> providerPrioritySenders() {
    return Stream.of(
        Arguments.of(
            new Scenario("priority first same fee")
                .addForSenders(S1, 0, SP1, 0)
                .expectedPrioritizedForSenders(SP1, 0, S1, 0)),
        Arguments.of(
            new Scenario("priority first lower fee")
                .addForSenders(S2, 0, SP1, 0)
                .expectedPrioritizedForSenders(SP1, 0, S2, 0)),
        Arguments.of(
            new Scenario("priority first higher fee")
                .addForSenders(S1, 0, SP2, 0)
                .expectedPrioritizedForSenders(SP2, 0, S1, 0)),
        Arguments.of(
            new Scenario("same priority order by fee")
                .addForSenders(SP1, 0, SP2, 0)
                .expectedPrioritizedForSenders(SP2, 0, SP1, 0)),
        Arguments.of(
            new Scenario("same priority order by fee")
                .addForSenders(SP2, 0, SP1, 0)
                .expectedPrioritizedForSenders(SP2, 0, SP1, 0)),
        Arguments.of(
            new Scenario("priority first overflow to ready")
                .addForSender(S2, 0, 1, 2)
                .expectedPrioritizedForSender(S2, 0, 1, 2)
                .addForSender(SP1, 0)
                .expectedPrioritizedForSenders(SP1, 0, S2, 0, S2, 1)
                .expectedReadyForSender(S2, 2)),
        Arguments.of(
            new Scenario("priority first overflow to ready 2")
                .addForSender(S2, 0, 1, 2)
                .expectedPrioritizedForSender(S2, 0, 1, 2)
                .addForSender(SP1, 0, 1, 2)
                .expectedPrioritizedForSender(SP1, 0, 1, 2)
                .expectedReadyForSender(S2, 0, 1, 2)),
        Arguments.of(
            new Scenario("multiple priority senders first overflow to ready")
                .addForSender(S2, 0, 1, 2)
                .expectedPrioritizedForSender(S2, 0, 1, 2)
                .addForSenders(SP2, 0, SP1, 0)
                .expectedPrioritizedForSenders(SP2, 0, SP1, 0, S2, 0)
                .expectedReadyForSender(S2, 1, 2)),
        Arguments.of(
            new Scenario("priority with initial gap")
                .addForSender(S2, 0)
                .expectedPrioritizedForSender(S2, 0)
                .addForSender(SP1, 1) // initial gap
                .expectedPrioritizedForSender(S2, 0)
                .expectedSparseForSender(SP1, 1)
                .addForSender(SP1, 0) // fill gap
                .expectedPrioritizedForSenders(SP1, 0, SP1, 1, S2, 0)
                .expectedSparseForSenders()),
        Arguments.of(
            new Scenario("priority with initial gap overflow to ready")
                .addForSender(S2, 0, 1)
                .expectedPrioritizedForSender(S2, 0, 1)
                .addForSender(SP1, 1) // initial gap
                .expectedSparseForSender(SP1, 1)
                .addForSender(SP1, 0) // fill gap
                .expectedPrioritizedForSenders(SP1, 0, SP1, 1, S2, 0)
                .expectedReadyForSender(S2, 1)
                .expectedSparseForSenders()),
        Arguments.of(
            new Scenario("priority with initial gap overflow to ready when prioritized is full")
                .addForSender(S2, 0, 1, 2)
                .expectedPrioritizedForSender(S2, 0, 1, 2)
                .addForSender(SP1, 1) // initial gap
                .expectedSparseForSender(SP1, 1)
                .addForSender(SP1, 0) // fill gap, but there is not enough space to promote
                .expectedPrioritizedForSenders(SP1, 0, S2, 0, S2, 1)
                .expectedReadyForSender(S2, 2)
                .expectedSparseForSender(SP1, 1)
                .confirmedForSenders(
                    SP1, 0) // asap there is new space the priority tx is promoted first
                .expectedPrioritizedForSenders(SP1, 1, S2, 0, S2, 1)
                .expectedReadyForSender(S2, 2)
                .expectedSparseForSenders()),
        Arguments.of(
            new Scenario("overflow to ready promote priority first")
                .addForSender(SP2, 0, 1, 2)
                .expectedPrioritizedForSender(SP2, 0, 1, 2)
                .addForSender(S2, 0)
                .expectedReadyForSender(S2, 0)
                .addForSender(SP1, 0)
                .expectedReadyForSenders(SP1, 0, S2, 0)
                .confirmedForSenders(
                    SP2, 0) // asap there is new space the priority tx is promoted first
                .expectedPrioritizedForSenders(SP2, 1, SP2, 2, SP1, 0)
                .expectedReadyForSender(S2, 0)),
        Arguments.of(
            new Scenario("priority first overflow to sparse")
                .addForSender(SP2, 0, 1, 2)
                .addForSender(S3, 0)
                .expectedPrioritizedForSender(SP2, 0, 1, 2)
                .expectedReadyForSender(S3, 0)
                .addForSender(SP1, 0, 1, 2)
                .expectedPrioritizedForSender(SP2, 0, 1, 2)
                .expectedReadyForSender(SP1, 0, 1, 2)
                .expectedSparseForSender(S3, 0)),
        Arguments.of(
            new Scenario("priority first overflow to sparse 2")
                .addForSender(S2, 0, 1, 2)
                .addForSender(S3, 0, 1, 2)
                .expectedPrioritizedForSender(S3, 0, 1, 2)
                .expectedReadyForSender(S2, 0, 1, 2)
                .addForSender(SP1, 0)
                .expectedPrioritizedForSenders(SP1, 0, S3, 0, S3, 1)
                .expectedReadyForSenders(S3, 2, S2, 0, S2, 1)
                .expectedSparseForSender(S2, 2)),
        Arguments.of(
            new Scenario("overflow to sparse promote priority first")
                .addForSender(SP2, 0, 1, 2, 3, 4, 5)
                .expectedPrioritizedForSender(SP2, 0, 1, 2)
                .expectedReadyForSender(SP2, 3, 4, 5)
                .addForSender(S3, 0)
                .expectedSparseForSender(S3, 0)
                .addForSender(SP1, 0)
                .expectedSparseForSenders(S3, 0, SP1, 0)
                .confirmedForSenders(SP2, 0)
                .expectedPrioritizedForSender(SP2, 1, 2, 3)
                .expectedReadyForSenders(SP2, 4, SP2, 5, SP1, 0)
                .expectedSparseForSender(S3, 0)),
        Arguments.of(
            new Scenario("discard priority as last")
                .addForSender(SP2, 0, 1, 2, 3, 4, 5)
                .expectedPrioritizedForSender(SP2, 0, 1, 2)
                .expectedReadyForSender(SP2, 3, 4, 5)
                .addForSender(S3, 0)
                .expectedSparseForSender(S3, 0)
                .addForSender(SP1, 0, 1, 2)
                .expectedSparseForSender(SP1, 0, 1, 2)
                .expectedDroppedForSender(S3, 0)));
  }

  private static BlockHeader mockBlockHeader() {
    final BlockHeader blockHeader = mock(BlockHeader.class);
    when(blockHeader.getBaseFee()).thenReturn(Optional.of(Wei.ONE));
    return blockHeader;
  }

  private boolean transactionReplacementTester(
      final PendingTransaction pt1, final PendingTransaction pt2) {
    return transactionReplacementTester(poolConfig, pt1, pt2);
  }

  private static boolean transactionReplacementTester(
      final TransactionPoolConfiguration poolConfig,
      final PendingTransaction pt1,
      final PendingTransaction pt2) {
    final TransactionPoolReplacementHandler transactionReplacementHandler =
        new TransactionPoolReplacementHandler(poolConfig.getPriceBump());
    return transactionReplacementHandler.shouldReplace(pt1, pt2, mockBlockHeader());
  }

  static class Scenario extends BaseTransactionPoolTest {
    interface TransactionLayersConsumer {
      void accept(
          LayeredPendingTransactions pending,
          AbstractPrioritizedTransactions prioritized,
          ReadyTransactions ready,
          SparseTransactions sparse,
          EvictCollectorLayer dropped);
    }

    final String description;
    final List<TransactionLayersConsumer> actions = new ArrayList<>();
    List<PendingTransaction> lastExpectedPrioritized = new ArrayList<>();
    List<PendingTransaction> lastExpectedReady = new ArrayList<>();
    List<PendingTransaction> lastExpectedSparse = new ArrayList<>();
    List<PendingTransaction> lastExpectedDropped = new ArrayList<>();

    final EnumMap<Sender, Long> nonceBySender = new EnumMap<>(Sender.class);

    {
      Arrays.stream(Sender.values()).forEach(e -> nonceBySender.put(e, 0L));
    }

    final EnumMap<Sender, Map<Long, PendingTransaction>> txsBySender = new EnumMap<>(Sender.class);

    {
      Arrays.stream(Sender.values()).forEach(e -> txsBySender.put(e, new HashMap<>()));
    }

    Scenario(final String description) {
      this.description = description;
    }

    Scenario addForSender(final Sender sender, final long... nonce) {
      Arrays.stream(nonce)
          .forEach(
              n -> {
                final var pendingTx = getOrCreate(sender, n);
                actions.add(
                    (pending, prio, ready, sparse, dropped) -> {
                      final Account mockSender = mock(Account.class);
                      when(mockSender.getNonce()).thenReturn(nonceBySender.get(sender));
                      pending.addTransaction(pendingTx, Optional.of(mockSender));
                    });
              });
      return this;
    }

    Scenario addForSenders(final Object... args) {
      for (int i = 0; i < args.length; i = i + 2) {
        final Sender sender = (Sender) args[i];
        final long nonce = (int) args[i + 1];
        addForSender(sender, nonce);
      }
      return this;
    }

    public Scenario confirmedForSenders(final Object... args) {
      final Map<Address, Long> maxConfirmedNonceBySender = new HashMap<>();
      for (int i = 0; i < args.length; i = i + 2) {
        final Sender sender = (Sender) args[i];
        final long nonce = (int) args[i + 1];
        maxConfirmedNonceBySender.put(sender.address, nonce);
        setAccountNonce(sender, nonce + 1);
      }
      actions.add(
          (pending, prio, ready, sparse, dropped) ->
              prio.blockAdded(FeeMarket.london(0L), mockBlockHeader(), maxConfirmedNonceBySender));
      return this;
    }

    Scenario setAccountNonce(final Sender sender, final long nonce) {
      actions.add((pending, prio, ready, sparse, dropped) -> nonceBySender.put(sender, nonce));
      return this;
    }

    void execute(
        final LayeredPendingTransactions pending,
        final AbstractPrioritizedTransactions prioritized,
        final ReadyTransactions ready,
        final SparseTransactions sparse,
        final EvictCollectorLayer dropped) {
      actions.forEach(action -> action.accept(pending, prioritized, ready, sparse, dropped));
      assertExpectedPrioritized(prioritized, lastExpectedPrioritized);
      assertExpectedReady(ready, lastExpectedReady);
      assertExpectedSparse(sparse, lastExpectedSparse);
      assertExpectedDropped(dropped, lastExpectedDropped);
    }

    private PendingTransaction getOrCreate(final Sender sender, final long nonce) {
      return txsBySender
          .get(sender)
          .computeIfAbsent(nonce, n -> createEIP1559PendingTransactions(sender, n));
    }

    private PendingTransaction get(final Sender sender, final long nonce) {
      return txsBySender.get(sender).get(nonce);
    }

    private PendingTransaction createEIP1559PendingTransactions(
        final Sender sender, final long nonce) {
      return createRemotePendingTransaction(
          createEIP1559Transaction(nonce, sender.key, sender.gasFeeMultiplier), sender.hasPriority);
    }

    public Scenario expectedPrioritizedForSender(final Sender sender, final long... nonce) {
      lastExpectedPrioritized = expectedForSender(sender, nonce);
      final var expectedCopy = List.copyOf(lastExpectedPrioritized);
      actions.add(
          (pending, prio, ready, sparse, dropped) -> assertExpectedPrioritized(prio, expectedCopy));
      return this;
    }

    public Scenario expectedReadyForSender(final Sender sender, final long... nonce) {
      lastExpectedReady = expectedForSender(sender, nonce);
      final var expectedCopy = List.copyOf(lastExpectedReady);
      actions.add(
          (pending, prio, ready, sparse, dropped) -> assertExpectedReady(ready, expectedCopy));
      return this;
    }

    public Scenario expectedSparseForSender(final Sender sender, final long... nonce) {
      lastExpectedSparse = expectedForSender(sender, nonce);
      final var expectedCopy = List.copyOf(lastExpectedSparse);
      actions.add(
          (pending, prio, ready, sparse, dropped) -> assertExpectedSparse(sparse, expectedCopy));
      return this;
    }

    public Scenario expectedDroppedForSender(final Sender sender, final long... nonce) {
      lastExpectedDropped = expectedForSender(sender, nonce);
      final var expectedCopy = List.copyOf(lastExpectedDropped);
      actions.add(
          (pending, prio, ready, sparse, dropped) -> assertExpectedDropped(dropped, expectedCopy));
      return this;
    }

    public Scenario expectedPrioritizedForSenders(
        final Sender sender1, final long nonce1, final Sender sender2, Object... args) {
      lastExpectedPrioritized = expectedForSenders(sender1, nonce1, sender2, args);
      final var expectedCopy = List.copyOf(lastExpectedPrioritized);
      actions.add(
          (pending, prio, ready, sparse, dropped) -> assertExpectedPrioritized(prio, expectedCopy));
      return this;
    }

    public Scenario expectedPrioritizedForSenders() {
      lastExpectedPrioritized = List.of();
      final var expectedCopy = List.copyOf(lastExpectedPrioritized);
      actions.add(
          (pending, prio, ready, sparse, dropped) -> assertExpectedPrioritized(prio, expectedCopy));
      return this;
    }

    public Scenario expectedReadyForSenders(
        final Sender sender1, final long nonce1, final Sender sender2, final Object... args) {
      lastExpectedReady = expectedForSenders(sender1, nonce1, sender2, args);
      final var expectedCopy = List.copyOf(lastExpectedReady);
      actions.add(
          (pending, prio, ready, sparse, dropped) -> assertExpectedReady(ready, expectedCopy));
      return this;
    }

    public Scenario expectedReadyForSenders() {
      lastExpectedReady = List.of();
      final var expectedCopy = List.copyOf(lastExpectedReady);
      actions.add(
          (pending, prio, ready, sparse, dropped) -> assertExpectedReady(ready, expectedCopy));
      return this;
    }

    public Scenario expectedSparseForSenders(
        final Sender sender1, final long nonce1, final Sender sender2, final Object... args) {
      lastExpectedSparse = expectedForSenders(sender1, nonce1, sender2, args);
      final var expectedCopy = List.copyOf(lastExpectedSparse);
      actions.add(
          (pending, prio, ready, sparse, dropped) -> assertExpectedSparse(sparse, expectedCopy));
      return this;
    }

    public Scenario expectedSparseForSenders() {
      lastExpectedSparse = List.of();
      final var expectedCopy = List.copyOf(lastExpectedSparse);
      actions.add(
          (pending, prio, ready, sparse, dropped) -> assertExpectedSparse(sparse, expectedCopy));
      return this;
    }

    public Scenario expectedDroppedForSenders(
        final Sender sender1, final long nonce1, final Sender sender2, final Object... args) {
      lastExpectedDropped = expectedForSenders(sender1, nonce1, sender2, args);
      final var expectedCopy = List.copyOf(lastExpectedDropped);
      actions.add(
          (pending, prio, ready, sparse, dropped) -> assertExpectedDropped(dropped, expectedCopy));
      return this;
    }

    public Scenario expectedDroppedForSenders() {
      lastExpectedDropped = List.of();
      final var expectedCopy = List.copyOf(lastExpectedDropped);
      actions.add(
          (pending, prio, ready, sparse, dropped) -> assertExpectedDropped(dropped, expectedCopy));
      return this;
    }

    private void assertExpectedPrioritized(
        final AbstractPrioritizedTransactions prioLayer, final List<PendingTransaction> expected) {
      assertThat(prioLayer.stream()).describedAs("Prioritized").containsExactlyElementsOf(expected);
    }

    private void assertExpectedReady(
        final ReadyTransactions readyLayer, final List<PendingTransaction> expected) {
      assertThat(readyLayer.stream()).describedAs("Ready").containsExactlyElementsOf(expected);
    }

    private void assertExpectedSparse(
        final SparseTransactions sparseLayer, final List<PendingTransaction> expected) {
      // sparse txs are returned from the most recent to the oldest, so reverse it to make writing
      // scenarios easier
      final var sortedExpected = new ArrayList<>(expected);
      Collections.reverse(sortedExpected);
      assertThat(sparseLayer.stream())
          .describedAs("Sparse")
          .containsExactlyElementsOf(sortedExpected);
    }

    private void assertExpectedDropped(
        final EvictCollectorLayer evictCollector, final List<PendingTransaction> expected) {
      assertThat(evictCollector.getEvictedTransactions())
          .describedAs("Dropped")
          .containsExactlyInAnyOrderElementsOf(expected);
    }

    private List<PendingTransaction> expectedForSenders(
        final Sender sender1, final long nonce1, final Sender sender2, final Object... args) {
      final List<PendingTransaction> expected = new ArrayList<>();
      expected.add(get(sender1, nonce1));
      final List<Object> sendersAndNonce = new ArrayList<>(Arrays.asList(args));
      sendersAndNonce.add(0, sender2);
      for (int i = 0; i < sendersAndNonce.size(); i = i + 2) {
        final Sender sender = (Sender) sendersAndNonce.get(i);
        final long nonce = (int) sendersAndNonce.get(i + 1);
        expected.add(get(sender, nonce));
      }
      return Collections.unmodifiableList(expected);
    }

    private List<PendingTransaction> expectedForSender(final Sender sender, final long... nonce) {
      return Arrays.stream(nonce).mapToObj(n -> get(sender, n)).toList();
    }

    public Scenario expectedNextNonceForSenders(final Object... args) {
      for (int i = 0; i < args.length; i = i + 2) {
        final Sender sender = (Sender) args[i];
        final Integer nullableInt = (Integer) args[i + 1];
        final OptionalLong nonce =
            nullableInt == null ? OptionalLong.empty() : OptionalLong.of(nullableInt);
        actions.add(
            (pending, prio, ready, sparse, dropped) ->
                assertThat(prio.getNextNonceFor(sender.address)).isEqualTo(nonce));
      }
      return this;
    }

    public Scenario removeForSender(final Sender sender, final long... nonce) {
      Arrays.stream(nonce)
          .forEach(
              n -> {
                final var pendingTx = getOrCreate(sender, n);
                actions.add(
                    (pending, prio, ready, sparse, dropped) -> prio.remove(pendingTx, INVALIDATED));
              });
      return this;
    }

    public Scenario expectedSelectedTransactions(final Object... args) {
      List<PendingTransaction> expectedSelected = new ArrayList<>();
      for (int i = 0; i < args.length; i = i + 2) {
        final Sender sender = (Sender) args[i];
        final long nonce = (int) args[i + 1];
        expectedSelected.add(get(sender, nonce));
      }
      actions.add(
          (pending, prio, ready, sparse, dropped) ->
              assertThat(prio.stream()).containsExactlyElementsOf(expectedSelected));
      return this;
    }

    @Override
    public String toString() {
      return description;
    }
  }

  enum Sender {
    S1(false, 1),
    S2(false, 2),
    S3(false, 3),
    S4(false, 4),
    SP1(true, 1),
    SP2(true, 2);

    final KeyPair key;
    final Address address;
    final int gasFeeMultiplier;

    final boolean hasPriority;

    Sender(final boolean hasPriority, final int gasFeeMultiplier) {
      this.key = SIGNATURE_ALGORITHM.get().generateKeyPair();
      this.address = Util.publicKeyToAddress(key.getPublicKey());
      this.gasFeeMultiplier = gasFeeMultiplier;
      this.hasPriority = hasPriority;
    }
  }
}
