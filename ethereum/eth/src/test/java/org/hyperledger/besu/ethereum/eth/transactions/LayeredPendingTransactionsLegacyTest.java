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
package org.hyperledger.besu.ethereum.eth.transactions;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.INVALID_TRANSACTION_FORMAT;
import static org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason.REPLAY_PROTECTED_SIGNATURE_REQUIRED;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.GasPricePrioritizedTransactions;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@SuppressWarnings("unchecked")
@RunWith(MockitoJUnitRunner.class)
public class LayeredPendingTransactionsLegacyTest extends AbstractLayeredPendingTransactionsTest {

  @Override
  protected PendingTransactions createPendingTransactionsSorter(
      final TransactionPoolConfiguration poolConfig,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {
    return new LayeredPendingTransactions(
        poolConfig,
        new GasPricePrioritizedTransactions(
            poolConfig, TestClock.system(ZoneId.systemDefault()), transactionReplacementTester),
        new TransactionPoolMetrics(metricsSystem),
        transactionReplacementTester);
  }

  @Override
  protected Transaction createTransaction(
      final int nonce, final Optional<BigInteger> maybeChainId) {
    return createBaseTransaction(nonce).chainId(maybeChainId).createTransaction(KEY_PAIR1);
  }

  @Override
  protected Transaction createTransaction(final int nonce, final Wei maxPrice) {
    return createBaseTransaction(nonce).gasPrice(maxPrice).createTransaction(KEY_PAIR1);
  }

  @Override
  protected TransactionTestFixture createBaseTransaction(final int nonce) {
    return new TransactionTestFixture()
        .nonce(nonce)
        .gasLimit(blockGasLimit)
        .type(TransactionType.FRONTIER);
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
    final List<Transaction> transactionList = asList(transactionsToAdd);
    final Block block =
        new Block(
            new BlockHeaderTestFixture()
                .difficulty(difficulty)
                .gasLimit(parentBlock.getGasLimit())
                .parentHash(parentBlock.getHash())
                .number(parentBlock.getNumber() + 1)
                .buildHeader(),
            new BlockBody(transactionList, emptyList()));
    final List<TransactionReceipt> transactionReceipts =
        transactionList.stream()
            .map(transaction -> new TransactionReceipt(1, 1, emptyList(), Optional.empty()))
            .collect(toList());
    blockchain.appendBlock(block, transactionReceipts);
    return block;
  }

  @Test
  public void
      addLocalTransaction_strictReplayProtectionOn_txWithoutChainId_chainIdIsConfigured_protectionNotSupportedAtCurrentBlock() {
    protocolSupportsTxReplayProtection(1337, false);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransactionWithoutChainId(0);
    givenTransactionIsValid(tx);

    addAndAssertLocalTransactionValid(tx);
  }

  @Test
  public void
      addRemoteTransactions_strictReplayProtectionOff_txWithoutChainId_chainIdIsConfigured() {
    protocolSupportsTxReplayProtection(1337, true);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(false));
    final Transaction tx = createTransactionWithoutChainId(0);
    givenTransactionIsValid(tx);

    addAndAssertRemoteTransactionValid(tx);
  }

  @Test
  public void addLocalTransaction_strictReplayProtectionOff_txWithoutChainId_chainIdIsConfigured() {
    protocolSupportsTxReplayProtection(1337, true);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(false));
    final Transaction tx = createTransactionWithoutChainId(0);
    givenTransactionIsValid(tx);

    addAndAssertLocalTransactionValid(tx);
  }

  @Test
  public void addLocalTransaction_strictReplayProtectionOn_txWithoutChainId_chainIdIsConfigured() {
    protocolSupportsTxReplayProtection(1337, true);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransactionWithoutChainId(0);
    givenTransactionIsValid(tx);

    addAndAssertLocalTransactionInvalid(tx, REPLAY_PROTECTED_SIGNATURE_REQUIRED);
  }

  @Test
  public void
      addRemoteTransactions_strictReplayProtectionOn_txWithoutChainId_chainIdIsConfigured() {
    protocolSupportsTxReplayProtection(1337, true);
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransactionWithoutChainId(0);
    givenTransactionIsValid(tx);

    addAndAssertRemoteTransactionValid(tx);
  }

  @Test
  public void
      addLocalTransaction_strictReplayProtectionOn_txWithoutChainId_chainIdIsNotConfigured() {
    protocolDoesNotSupportTxReplayProtection();
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransactionWithoutChainId(0);
    givenTransactionIsValid(tx);

    addAndAssertLocalTransactionValid(tx);
  }

  @Test
  public void
      addRemoteTransactions_strictReplayProtectionOn_txWithoutChainId_chainIdIsNotConfigured() {
    protocolDoesNotSupportTxReplayProtection();
    transactionPool = createTransactionPool(b -> b.strictTransactionReplayProtectionEnabled(true));
    final Transaction tx = createTransactionWithoutChainId(0);
    givenTransactionIsValid(tx);

    addAndAssertRemoteTransactionValid(tx);
  }

  @Test
  public void shouldIgnoreEIP1559TransactionWhenNotAllowed() {
    final Transaction transaction =
        createBaseTransaction(1)
            .type(TransactionType.EIP1559)
            .maxFeePerGas(Optional.of(Wei.of(100L)))
            .maxPriorityFeePerGas(Optional.of(Wei.of(50L)))
            .gasLimit(10)
            .gasPrice(null)
            .createTransaction(KEY_PAIR1);

    givenTransactionIsValid(transaction);

    addAndAssertLocalTransactionInvalid(transaction, INVALID_TRANSACTION_FORMAT);
  }

  @Test
  public void shouldRejectGoQuorumTransactionWithNonZeroValue() {
    when(transactionValidator.getGoQuorumCompatibilityMode()).thenReturn(true);

    final Transaction transaction37 =
        Transaction.builder().v(BigInteger.valueOf(37)).value(Wei.ONE).gasPrice(Wei.ZERO).build();
    final Transaction transaction38 =
        Transaction.builder().v(BigInteger.valueOf(38)).value(Wei.ONE).gasPrice(Wei.ZERO).build();

    final ValidationResult<TransactionInvalidReason> result37 =
        transactionPool.addLocalTransaction(transaction37);
    final ValidationResult<TransactionInvalidReason> result38 =
        transactionPool.addLocalTransaction(transaction38);

    assertThat(result37.getInvalidReason())
        .isEqualTo(TransactionInvalidReason.ETHER_VALUE_NOT_SUPPORTED);
    assertThat(result38.getInvalidReason())
        .isEqualTo(TransactionInvalidReason.ETHER_VALUE_NOT_SUPPORTED);
  }

  @Test
  public void shouldAcceptZeroGasPriceFrontierTransactionsWhenMining() {
    when(miningParameters.isMiningEnabled()).thenReturn(true);

    final Transaction transaction = createTransaction(0, Wei.ZERO);

    givenTransactionIsValid(transaction);

    addAndAssertLocalTransactionValid(transaction);
  }

  @Test
  public void shouldAcceptZeroGasPriceTransactionWhenMinGasPriceIsZero() {
    when(miningParameters.getMinTransactionGasPrice()).thenReturn(Wei.ZERO);

    final Transaction transaction = createTransaction(0, Wei.ZERO);

    givenTransactionIsValid(transaction);

    addAndAssertLocalTransactionValid(transaction);
  }

  private Transaction createTransactionWithoutChainId(final int nonce) {
    return createTransaction(nonce, Optional.empty());
  }

  private void protocolDoesNotSupportTxReplayProtection() {
    when(protocolSchedule.getChainId()).thenReturn(Optional.empty());
  }
}
