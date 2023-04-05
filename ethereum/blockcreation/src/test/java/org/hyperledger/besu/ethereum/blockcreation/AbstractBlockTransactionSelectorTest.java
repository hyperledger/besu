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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.core.TransactionTestFixture;
import org.hyperledger.besu.ethereum.difficulty.fixed.FixedDifficultyProtocolSchedule;
import org.hyperledger.besu.ethereum.eth.transactions.ImmutableTransactionPoolConfiguration;
import org.hyperledger.besu.ethereum.eth.transactions.PendingTransactions;
import org.hyperledger.besu.ethereum.eth.transactions.sorter.BaseFeePendingTransactionsSorter;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionValidator;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.referencetests.ReferenceTestBlockchain;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.data.TransactionType;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.testutil.TestClock;

import java.math.BigInteger;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public abstract class AbstractBlockTransactionSelectorTest {
  protected static final KeyPair keyPair =
      SignatureAlgorithmFactory.getInstance().generateKeyPair();
  protected final MetricsSystem metricsSystem = new NoOpMetricsSystem();

  protected final Blockchain blockchain = new ReferenceTestBlockchain();
  protected PendingTransactions pendingTransactions;
  protected MutableWorldState worldState;
  @Mock protected MainnetTransactionProcessor transactionProcessor;
  @Mock protected MainnetTransactionValidator transactionValidator;

  @Before
  public void setup() {
    worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();
    pendingTransactions = createPendingTransactions();
    when(transactionProcessor.getTransactionValidator()).thenReturn(transactionValidator);
    when(transactionValidator.getGoQuorumCompatibilityMode()).thenReturn(true);
  }

  protected abstract PendingTransactions createPendingTransactions();

  private Boolean isCancelled() {
    return false;
  }

  protected ProcessableBlockHeader createBlock(final long gasLimit) {
    return createBlock(gasLimit, Wei.ONE);
  }

  protected ProcessableBlockHeader createBlock(final long gasLimit, final Wei baseFee) {
    return BlockHeaderBuilder.create()
        .parentHash(Hash.EMPTY)
        .coinbase(Address.fromHexString(String.format("%020x", 1)))
        .difficulty(Difficulty.ONE)
        .number(1)
        .gasLimit(gasLimit)
        .timestamp(Instant.now().toEpochMilli())
        .baseFee(baseFee)
        .buildProcessableBlockHeader();
  }

  @Test
  public void emptyPendingTransactionsResultsInEmptyVettingResult() {
    final ProtocolSchedule protocolSchedule =
        FixedDifficultyProtocolSchedule.create(
            GenesisConfigFile.development().getConfigOptions(), EvmConfiguration.DEFAULT);
    final MainnetTransactionProcessor mainnetTransactionProcessor =
        protocolSchedule.getByBlockHeader(blockHeader(0)).getTransactionProcessor();

    // The block should fit 5 transactions only
    final ProcessableBlockHeader blockHeader = createBlock(5000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);

    final BlockTransactionSelector selector =
        createBlockSelector(
            mainnetTransactionProcessor, blockHeader, Wei.ZERO, miningBeneficiary, Wei.ZERO);

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions().size()).isEqualTo(0);
    assertThat(results.getReceipts().size()).isEqualTo(0);
    assertThat(results.getCumulativeGasUsed()).isEqualTo(0);
  }

  @Test
  public void failedTransactionsAreIncludedInTheBlock() {
    final Transaction transaction = createTransaction(1);
    pendingTransactions.addRemoteTransaction(transaction, Optional.empty());

    ensureTransactionIsValid(transaction, 0, 5);

    // The block should fit 3 transactions only
    final ProcessableBlockHeader blockHeader = createBlock(5000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);

    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor, blockHeader, Wei.ZERO, miningBeneficiary, Wei.ZERO);

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions().size()).isEqualTo(1);
    Assertions.assertThat(results.getTransactions()).contains(transaction);
    assertThat(results.getReceipts().size()).isEqualTo(1);
    assertThat(results.getCumulativeGasUsed()).isEqualTo(95L);
  }

  @Test
  public void invalidTransactionsTransactionProcessingAreSkippedButBlockStillFills() {
    final List<Transaction> transactionsToInject = Lists.newArrayList();
    for (int i = 0; i < 5; i++) {
      final Transaction tx = createTransaction(i);
      transactionsToInject.add(tx);
      pendingTransactions.addRemoteTransaction(tx, Optional.empty());
      if (i == 1) {
        ensureTransactionIsInvalid(tx, TransactionInvalidReason.NONCE_TOO_LOW);
      } else {
        ensureTransactionIsValid(tx);
      }
    }

    // The block should fit 3 transactions only
    final ProcessableBlockHeader blockHeader = createBlock(5000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);

    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor, blockHeader, Wei.ZERO, miningBeneficiary, Wei.ZERO);

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions().size()).isEqualTo(4);
    assertThat(results.getTransactions().contains(transactionsToInject.get(1))).isFalse();
    assertThat(results.getReceipts().size()).isEqualTo(4);
    assertThat(results.getCumulativeGasUsed()).isEqualTo(400);
  }

  @Test
  public void subsetOfPendingTransactionsIncludedWhenBlockGasLimitHit() {
    final List<Transaction> transactionsToInject = Lists.newArrayList();
    // Transactions are reported in reverse order.
    for (int i = 0; i < 5; i++) {
      final Transaction tx = createTransaction(i);
      transactionsToInject.add(tx);
      pendingTransactions.addRemoteTransaction(tx, Optional.empty());
      ensureTransactionIsValid(tx);
    }

    final ProcessableBlockHeader blockHeader = createBlock(301);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);

    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor, blockHeader, Wei.ZERO, miningBeneficiary, Wei.ZERO);

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions().size()).isEqualTo(3);

    assertThat(results.getTransactions().containsAll(transactionsToInject.subList(0, 3))).isTrue();
    assertThat(results.getReceipts().size()).isEqualTo(3);
    assertThat(results.getCumulativeGasUsed()).isEqualTo(300);

    // Ensure receipts have the correct cumulative gas
    Assertions.assertThat(results.getReceipts().get(0).getCumulativeGasUsed()).isEqualTo(100);
    Assertions.assertThat(results.getReceipts().get(1).getCumulativeGasUsed()).isEqualTo(200);
    Assertions.assertThat(results.getReceipts().get(2).getCumulativeGasUsed()).isEqualTo(300);
  }

  @Test
  public void useSingleGasSpaceForAllTransactions() {
    final ProcessableBlockHeader blockHeader = createBlock(300);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BaseFeePendingTransactionsSorter pendingTransactions1559 =
        new BaseFeePendingTransactionsSorter(
            ImmutableTransactionPoolConfiguration.builder().txPoolMaxSize(5).build(),
            TestClock.system(ZoneId.systemDefault()),
            metricsSystem,
            () -> {
              final BlockHeader mockBlockHeader = mock(BlockHeader.class);
              when(mockBlockHeader.getBaseFee()).thenReturn(Optional.of(Wei.ONE));
              return mockBlockHeader;
            });
    final BlockTransactionSelector selector =
        new BlockTransactionSelector(
            transactionProcessor,
            blockchain,
            worldState,
            pendingTransactions1559,
            blockHeader,
            this::createReceipt,
            Wei.of(6),
            0.8,
            this::isCancelled,
            miningBeneficiary,
            Wei.ZERO,
            FeeMarket.london(0L),
            new LondonGasCalculator(),
            GasLimitCalculator.constant(),
            -1);

    // this should fill up all the block space
    final Transaction fillingLegacyTx =
        Transaction.builder()
            .type(TransactionType.FRONTIER)
            .gasLimit(300)
            .gasPrice(Wei.of(10))
            .nonce(1)
            .payload(Bytes.EMPTY)
            .to(Address.ID)
            .value(Wei.ZERO)
            .sender(Address.ID)
            .chainId(BigInteger.ONE)
            .signAndBuild(keyPair);

    ensureTransactionIsValid(fillingLegacyTx);

    // so we shouldn't include this
    final Transaction extraEIP1559Tx =
        Transaction.builder()
            .type(TransactionType.EIP1559)
            .nonce(0)
            .maxPriorityFeePerGas(Wei.of(10))
            .maxFeePerGas(Wei.of(10))
            .gasLimit(50)
            .to(Address.ID)
            .value(Wei.of(0))
            .payload(Bytes.EMPTY)
            .chainId(BigInteger.ONE)
            .signAndBuild(keyPair);

    ensureTransactionIsValid(extraEIP1559Tx);

    pendingTransactions1559.addRemoteTransaction(fillingLegacyTx, Optional.empty());
    pendingTransactions1559.addRemoteTransaction(extraEIP1559Tx, Optional.empty());
    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions().size()).isEqualTo(1);
  }

  @Test
  public void transactionTooLargeForBlockDoesNotPreventMoreBeingAddedIfBlockOccupancyNotReached() {
    final ProcessableBlockHeader blockHeader = createBlock(300);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor, blockHeader, Wei.ZERO, miningBeneficiary, Wei.ZERO);

    final TransactionTestFixture txTestFixture = new TransactionTestFixture();
    // Add 3 transactions to the Pending Transactions, 79% of block, 100% of block and 10% of block
    // should end up selecting the first and third only.
    // NOTE - PendingTransactions outputs these in nonce order
    final List<Transaction> transactionsToInject = Lists.newArrayList();
    transactionsToInject.add(
        txTestFixture
            .gasLimit((long) (blockHeader.getGasLimit() * 0.79))
            .nonce(1)
            .createTransaction(keyPair));
    transactionsToInject.add(
        txTestFixture.gasLimit(blockHeader.getGasLimit()).nonce(2).createTransaction(keyPair));
    transactionsToInject.add(
        txTestFixture
            .gasLimit((long) (blockHeader.getGasLimit() * 0.1))
            .nonce(3)
            .createTransaction(keyPair));

    for (final Transaction tx : transactionsToInject) {
      pendingTransactions.addRemoteTransaction(tx, Optional.empty());
      ensureTransactionIsValid(tx);
    }

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions().size()).isEqualTo(2);
    Assertions.assertThat(results.getTransactions().get(0)).isEqualTo(transactionsToInject.get(0));
    Assertions.assertThat(results.getTransactions().get(1)).isEqualTo(transactionsToInject.get(2));
  }

  @Test
  public void transactionSelectionStopsWhenSufficientBlockOccupancyIsReached() {
    final ProcessableBlockHeader blockHeader = createBlock(300);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor, blockHeader, Wei.ZERO, miningBeneficiary, Wei.ZERO);

    final TransactionTestFixture txTestFixture = new TransactionTestFixture();
    // Add 4 transactions to the Pending Transactions 15% (ok), 79% (ok), 25% (too large), 10%
    // (not included, it would fit, however previous transaction was too large and block was
    // suitably populated).
    // NOTE - PendingTransactions will output these in nonce order.
    final Transaction[] txs =
        new Transaction[] {
          txTestFixture
              .gasLimit((long) (blockHeader.getGasLimit() * 0.15))
              .nonce(1)
              .createTransaction(keyPair),
          txTestFixture
              .gasLimit((long) (blockHeader.getGasLimit() * 0.79))
              .nonce(2)
              .createTransaction(keyPair),
          txTestFixture
              .gasLimit((long) (blockHeader.getGasLimit() * 0.25))
              .nonce(3)
              .createTransaction(keyPair),
          txTestFixture
              .gasLimit((long) (blockHeader.getGasLimit() * 0.1))
              .nonce(4)
              .createTransaction(keyPair)
        };

    for (Transaction tx : txs) {
      pendingTransactions.addRemoteTransaction(tx, Optional.empty());
      ensureTransactionIsValid(tx);
    }

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    assertThat(results.getTransactions().size()).isEqualTo(2);
    Assertions.assertThat(results.getTransactions().get(0)).isEqualTo(txs[0]);
    Assertions.assertThat(results.getTransactions().get(1)).isEqualTo(txs[1]);
    assertThat(results.getTransactions().contains(txs[3])).isFalse();
    assertThat(results.getTransactions().contains(txs[2])).isFalse();
  }

  @Test
  public void shouldDiscardTransactionsThatFailValidation() {
    final ProcessableBlockHeader blockHeader = createBlock(300);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor, blockHeader, Wei.ZERO, miningBeneficiary, Wei.ZERO);

    final TransactionTestFixture txTestFixture = new TransactionTestFixture();
    final Transaction validTransaction =
        txTestFixture.nonce(1).gasLimit(1).createTransaction(keyPair);
    ensureTransactionIsValid(validTransaction, 2000, 10000);
    final Transaction invalidTransaction =
        txTestFixture.nonce(2).gasLimit(2).createTransaction(keyPair);
    ensureTransactionIsInvalid(
        invalidTransaction, TransactionInvalidReason.EXCEEDS_BLOCK_GAS_LIMIT);

    pendingTransactions.addRemoteTransaction(validTransaction, Optional.empty());
    pendingTransactions.addRemoteTransaction(invalidTransaction, Optional.empty());

    selector.buildTransactionListForBlock();

    Assertions.assertThat(pendingTransactions.getTransactionByHash(validTransaction.getHash()))
        .isPresent();
    Assertions.assertThat(pendingTransactions.getTransactionByHash(invalidTransaction.getHash()))
        .isNotPresent();
  }

  @Test
  public void transactionWithIncorrectNonceRemainsInPoolAndNotSelected() {
    final ProcessableBlockHeader blockHeader = createBlock(5000);

    final TransactionTestFixture txTestFixture = new TransactionTestFixture();
    final Transaction futureTransaction =
        txTestFixture.nonce(4).gasLimit(1).createTransaction(keyPair);

    pendingTransactions.addRemoteTransaction(futureTransaction, Optional.empty());
    ensureTransactionIsInvalid(futureTransaction, TransactionInvalidReason.NONCE_TOO_HIGH);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor, blockHeader, Wei.ZERO, miningBeneficiary, Wei.ZERO);

    final BlockTransactionSelector.TransactionSelectionResults results =
        selector.buildTransactionListForBlock();

    Assertions.assertThat(pendingTransactions.getTransactionByHash(futureTransaction.getHash()))
        .isPresent();
    assertThat(results.getTransactions().size()).isEqualTo(0);
  }

  protected BlockTransactionSelector createBlockSelector(
      final MainnetTransactionProcessor transactionProcessor,
      final ProcessableBlockHeader blockHeader,
      final Wei minGasPrice,
      final Address miningBeneficiary,
      final Wei dataGasPrice) {
    final BlockTransactionSelector selector =
        new BlockTransactionSelector(
            transactionProcessor,
            blockchain,
            worldState,
            pendingTransactions,
            blockHeader,
            this::createReceipt,
            minGasPrice,
            0.8,
            this::isCancelled,
            miningBeneficiary,
            dataGasPrice,
            getFeeMarket(),
            new LondonGasCalculator(),
            GasLimitCalculator.constant(),
            -1);
    return selector;
  }

  protected abstract FeeMarket getFeeMarket();

  private Transaction createTransaction(final int transactionNumber) {
    return Transaction.builder()
        .gasLimit(100)
        .gasPrice(Wei.of(5))
        .nonce(transactionNumber)
        .payload(Bytes.EMPTY)
        .to(Address.ID)
        .value(Wei.of(transactionNumber))
        .sender(Address.ID)
        .chainId(BigInteger.ONE)
        .guessType()
        .signAndBuild(keyPair);
  }

  // This is a duplicate of the MainnetProtocolSpec::frontierTransactionReceiptFactory
  private TransactionReceipt createReceipt(
      final TransactionType __,
      final TransactionProcessingResult result,
      final WorldState worldState,
      final long gasUsed) {
    return new TransactionReceipt(
        worldState.rootHash(), gasUsed, Lists.newArrayList(), Optional.empty());
  }

  protected void ensureTransactionIsValid(final Transaction tx) {
    ensureTransactionIsValid(tx, 0, 0);
  }

  protected void ensureTransactionIsValid(
      final Transaction tx, final long gasUsedByTransaction, final long gasRemaining) {
    when(transactionProcessor.processTransaction(
            any(), any(), any(), eq(tx), any(), any(), anyBoolean(), any(), any()))
        .thenReturn(
            TransactionProcessingResult.successful(
                new ArrayList<>(),
                gasUsedByTransaction,
                gasRemaining,
                Bytes.EMPTY,
                ValidationResult.valid()));
  }

  protected void ensureTransactionIsInvalid(
      final Transaction tx, final TransactionInvalidReason invalidReason) {
    when(transactionProcessor.processTransaction(
            any(), any(), any(), eq(tx), any(), any(), anyBoolean(), any(), any()))
        .thenReturn(TransactionProcessingResult.invalid(ValidationResult.invalid(invalidReason)));
  }

  private BlockHeader blockHeader(final long number) {
    return new BlockHeaderTestFixture().number(number).buildHeader();
  }
}
