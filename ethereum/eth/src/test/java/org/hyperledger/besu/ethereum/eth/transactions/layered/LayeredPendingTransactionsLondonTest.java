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
package org.hyperledger.besu.ethereum.eth.transactions.layered;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.StubGenesisConfigOptions;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.eth.transactions.AbstractTransactionsLayeredPendingTransactionsTest;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPoolMetrics;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolScheduleBuilder;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpecAdapters;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.evm.internal.EvmConfiguration;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

public class LayeredPendingTransactionsLondonTest
    extends AbstractTransactionsLayeredPendingTransactionsTest {

  private static final Wei BASE_FEE_FLOOR = Wei.of(7L);

  @Override
  protected PendingTransactions createPendingTransactionsSorter(
      final TransactionPoolConfiguration poolConfig,
      final BiFunction<PendingTransaction, PendingTransaction, Boolean>
          transactionReplacementTester) {

    final var txPoolMetrics = new TransactionPoolMetrics(metricsSystem);
    return new LayeredPendingTransactions(
        poolConfig,
        new BaseFeePrioritizedTransactions(
            poolConfig,
            protocolContext.getBlockchain()::getChainHeadHeader,
            new EndLayer(txPoolMetrics),
            txPoolMetrics,
            transactionReplacementTester,
            FeeMarket.london(0L)));
  }

  @Override
  protected Transaction createTransaction(
      final int nonce, final Optional<BigInteger> maybeChainId) {
    return createBaseTransaction(nonce).chainId(maybeChainId).createTransaction(KEY_PAIR1);
  }

  @Override
  protected Transaction createTransaction(final int nonce, final Wei maxPrice) {
    return createBaseTransaction(nonce)
        .maxFeePerGas(Optional.of(maxPrice))
        .maxPriorityFeePerGas(Optional.of(maxPrice.divide(5L)))
        .createTransaction(KEY_PAIR1);
  }

  @Override
  protected TransactionTestFixture createBaseTransaction(final int nonce) {
    return new TransactionTestFixture()
        .nonce(nonce)
        .gasLimit(blockGasLimit)
        .gasPrice(null)
        .maxFeePerGas(Optional.of(Wei.of(5000L)))
        .maxPriorityFeePerGas(Optional.of(Wei.of(1000L)))
        .type(TransactionType.EIP1559);
  }

  @Override
  protected ExecutionContextTestFixture createExecutionContextTestFixture() {
    final ProtocolSchedule protocolSchedule =
        new ProtocolScheduleBuilder(
                new StubGenesisConfigOptions().londonBlock(0L).baseFeePerGas(10L),
                BigInteger.valueOf(1),
                ProtocolSpecAdapters.create(0, Function.identity()),
                new PrivacyParameters(),
                false,
                EvmConfiguration.DEFAULT)
            .createProtocolSchedule();
    final ExecutionContextTestFixture executionContextTestFixture =
        ExecutionContextTestFixture.builder().protocolSchedule(protocolSchedule).build();

    final Block block =
        new Block(
            new BlockHeaderTestFixture()
                .gasLimit(
                    executionContextTestFixture
                        .getBlockchain()
                        .getChainHeadBlock()
                        .getHeader()
                        .getGasLimit())
                .difficulty(Difficulty.ONE)
                .baseFeePerGas(Wei.of(10L))
                .parentHash(executionContextTestFixture.getBlockchain().getChainHeadHash())
                .number(executionContextTestFixture.getBlockchain().getChainHeadBlockNumber() + 1)
                .buildHeader(),
            new BlockBody(List.of(), List.of()));
    executionContextTestFixture.getBlockchain().appendBlock(block, List.of());

    return executionContextTestFixture;
  }

  @Override
  protected FeeMarket getFeeMarket() {
    return FeeMarket.london(0L, Optional.of(BASE_FEE_FLOOR));
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
                .baseFeePerGas(Wei.of(10L))
                .gasLimit(parentBlock.getGasLimit())
                .difficulty(difficulty)
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
  public void shouldAcceptZeroGasPriceFrontierTxsWhenMinGasPriceIsZeroAndLondonWithZeroBaseFee() {
    when(miningParameters.getMinTransactionGasPrice()).thenReturn(Wei.ZERO);
    when(protocolSpec.getFeeMarket()).thenReturn(FeeMarket.london(0, Optional.of(Wei.ZERO)));
    whenBlockBaseFeeIs(Wei.ZERO);

    final Transaction frontierTransaction = createFrontierTransaction(0, Wei.ZERO);

    givenTransactionIsValid(frontierTransaction);
    addAndAssertLocalTransactionValid(frontierTransaction);
  }

  @Test
  public void shouldAcceptZeroGasPrice1559TxsWhenMinGasPriceIsZeroAndLondonWithZeroBaseFee() {
    when(miningParameters.getMinTransactionGasPrice()).thenReturn(Wei.ZERO);
    when(protocolSpec.getFeeMarket()).thenReturn(FeeMarket.london(0, Optional.of(Wei.ZERO)));
    whenBlockBaseFeeIs(Wei.ZERO);

    final Transaction transaction = createTransaction(0, Wei.ZERO);

    givenTransactionIsValid(transaction);
    addAndAssertLocalTransactionValid(transaction);
  }

  @Test
  public void shouldAcceptBaseFeeFloorGasPriceFrontierTransactionsWhenMining() {
    final Transaction frontierTransaction = createFrontierTransaction(0, BASE_FEE_FLOOR);

    givenTransactionIsValid(frontierTransaction);

    addAndAssertLocalTransactionValid(frontierTransaction);
  }

  @Test
  public void shouldRejectRemote1559TxsWhenMaxFeePerGasBelowMinGasPrice() {
    final Wei genesisBaseFee = Wei.of(100L);
    final Wei minGasPrice = Wei.of(200L);
    final Wei lastBlockBaseFee = minGasPrice.add(50L);
    final Wei txMaxFeePerGas = minGasPrice.subtract(1L);

    assertThat(
            add1559TxAndGetPendingTxsCount(
                genesisBaseFee, minGasPrice, lastBlockBaseFee, txMaxFeePerGas, false))
        .isEqualTo(0);
  }

  @Test
  public void shouldAcceptRemote1559TxsWhenMaxFeePerGasIsAtLeastEqualToMinGasPrice() {
    final Wei genesisBaseFee = Wei.of(100L);
    final Wei minGasPrice = Wei.of(200L);
    final Wei lastBlockBaseFee = minGasPrice.add(50L);
    final Wei txMaxFeePerGas = minGasPrice;

    assertThat(
            add1559TxAndGetPendingTxsCount(
                genesisBaseFee, minGasPrice, lastBlockBaseFee, txMaxFeePerGas, false))
        .isEqualTo(1);
  }

  @Test
  public void shouldRejectLocal1559TxsWhenMaxFeePerGasBelowMinGasPrice() {
    final Wei genesisBaseFee = Wei.of(100L);
    final Wei minGasPrice = Wei.of(200L);
    final Wei lastBlockBaseFee = minGasPrice.add(50L);
    final Wei txMaxFeePerGas = minGasPrice.subtract(1L);

    assertThat(
            add1559TxAndGetPendingTxsCount(
                genesisBaseFee, minGasPrice, lastBlockBaseFee, txMaxFeePerGas, true))
        .isEqualTo(0);
  }

  @Test
  public void shouldAcceptLocal1559TxsWhenMaxFeePerGasIsAtLeastEqualToMinMinGasPrice() {
    final Wei genesisBaseFee = Wei.of(100L);
    final Wei minGasPrice = Wei.of(200L);
    final Wei lastBlockBaseFee = minGasPrice.add(50L);
    final Wei txMaxFeePerGas = minGasPrice;

    assertThat(
            add1559TxAndGetPendingTxsCount(
                genesisBaseFee, minGasPrice, lastBlockBaseFee, txMaxFeePerGas, true))
        .isEqualTo(1);
  }

  private int add1559TxAndGetPendingTxsCount(
      final Wei genesisBaseFee,
      final Wei minGasPrice,
      final Wei lastBlockBaseFee,
      final Wei txMaxFeePerGas,
      final boolean isLocal) {
    when(miningParameters.getMinTransactionGasPrice()).thenReturn(minGasPrice);
    when(protocolSpec.getFeeMarket()).thenReturn(FeeMarket.london(0, Optional.of(genesisBaseFee)));
    whenBlockBaseFeeIs(lastBlockBaseFee);

    final Transaction transaction = createTransaction(0, txMaxFeePerGas);

    givenTransactionIsValid(transaction);

    if (isLocal) {
      transactionPool.addTransactionViaApi(transaction);
    } else {
      transactionPool.addRemoteTransactions(List.of(transaction));
    }

    return transactions.size();
  }

  private void whenBlockBaseFeeIs(final Wei baseFee) {
    final BlockHeader header =
        BlockHeaderBuilder.fromHeader(blockchain.getChainHeadHeader())
            .baseFee(baseFee)
            .blockHeaderFunctions(new MainnetBlockHeaderFunctions())
            .parentHash(blockchain.getChainHeadHash())
            .buildBlockHeader();
    blockchain.appendBlock(new Block(header, BlockBody.empty()), emptyList());
  }

  private Transaction createFrontierTransaction(final int transactionNumber, final Wei gasPrice) {
    return new TransactionTestFixture()
        .nonce(transactionNumber)
        .gasPrice(gasPrice)
        .gasLimit(blockGasLimit)
        .type(TransactionType.FRONTIER)
        .createTransaction(KEY_PAIR1);
  }
}
