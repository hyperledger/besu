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
package org.hyperledger.besu.ethereum.eth.transactions.sorter;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.AbstractTransactionPoolTest;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.testutil.TestClock;

import java.time.ZoneId;
import java.util.function.BiFunction;

public class LegacyTransactionPoolGasPriceTest extends AbstractTransactionPoolTest {

  @Override
  protected PendingTransactions createPendingTransactions(
      final TransactionPoolConfiguration poolConfig,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {

    return new GasPricePendingTransactionsSorter(
        poolConfig,
        TestClock.system(ZoneId.systemDefault()),
        metricsSystem,
        protocolContext.getBlockchain()::getChainHeadHeader);
  }

  @Override
  protected Transaction createTransaction(final int transactionNumber, final Wei maxPrice) {
    return createTransactionGasPriceMarket(transactionNumber, maxPrice);
  }

  @Override
  protected TransactionTestFixture createBaseTransaction(final int transactionNumber) {
    return createBaseTransactionGasPriceMarket(transactionNumber);
  }

  @Override
  protected ExecutionContextTestFixture createExecutionContextTestFixture() {
    return ExecutionContextTestFixture.create();
  }

  @Override
  protected FeeMarket getFeeMarket() {
    return FeeMarket.legacy();
  }

  @Override
  protected Block appendBlock(
      final Difficulty difficulty,
      final BlockHeader parentBlock,
      final Transaction... transactionsToAdd) {
    return appendBlockGasPriceMarket(difficulty, parentBlock, transactionsToAdd);
  }
}
