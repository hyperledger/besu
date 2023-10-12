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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SignatureAlgorithmFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.PendingTransaction;
import org.hyperledger.besu.datatypes.TransactionType;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.GasLimitCalculator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.blockcreation.txselection.BlockTransactionSelector;
import org.hyperledger.besu.ethereum.blockcreation.txselection.TransactionSelectionResults;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.difficulty.fixed.FixedDifficultyProtocolSchedule;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.transactions.TransactionPool;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.mainnet.feemarket.FeeMarket;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.gascalculator.LondonGasCalculator;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.data.TransactionSelectionResult;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.TransactionSelectorFactory;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class AbstractBlockTransactionSelectorTest {
  protected static final double MIN_OCCUPANCY_80_PERCENT = 0.8;
  protected static final double MIN_OCCUPANCY_100_PERCENT = 1;
  protected static final BigInteger CHAIN_ID = BigInteger.valueOf(42L);
  protected static final KeyPair keyPair =
      SignatureAlgorithmFactory.getInstance().generateKeyPair();
  protected static final Address sender =
      Address.extract(Hash.hash(keyPair.getPublicKey().getEncodedBytes()));

  protected final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  protected GenesisConfigFile genesisConfigFile;
  protected MutableBlockchain blockchain;
  protected TransactionPool transactionPool;
  protected MutableWorldState worldState;
  protected ProtocolSchedule protocolSchedule;
  protected MiningParameters miningParameters;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  protected ProtocolContext protocolContext;

  @Mock protected MainnetTransactionProcessor transactionProcessor;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  protected EthContext ethContext;

  @BeforeEach
  public void setup() {
    genesisConfigFile = getGenesisConfigFile();
    protocolSchedule = createProtocolSchedule();
    final Block genesisBlock =
        GenesisState.fromConfig(genesisConfigFile, protocolSchedule).getBlock();

    blockchain =
        DefaultBlockchain.createMutable(
            genesisBlock,
            new KeyValueStoragePrefixedKeyBlockchainStorage(
                new InMemoryKeyValueStorage(),
                new VariablesKeyValueStorage(new InMemoryKeyValueStorage()),
                new MainnetBlockHeaderFunctions()),
            new NoOpMetricsSystem(),
            0);

    when(protocolContext.getBlockchain()).thenReturn(blockchain);

    worldState = InMemoryKeyValueStorageProvider.createInMemoryWorldState();
    final var worldStateUpdater = worldState.updater();
    worldStateUpdater.createAccount(sender, 0, Wei.of(1_000_000_000L));
    worldStateUpdater.commit();

    when(protocolContext.getWorldStateArchive().getMutable(any(), anyBoolean()))
        .thenReturn(Optional.of(worldState));
    when(ethContext.getEthPeers().subscribeConnect(any())).thenReturn(1L);
    miningParameters =
        ImmutableMiningParameters.builder()
            .build()
            .getDynamic()
            .setMinTransactionGasPrice(Wei.ONE)
            .toParameters();

    transactionPool = createTransactionPool();
  }

  protected abstract GenesisConfigFile getGenesisConfigFile();

  protected abstract ProtocolSchedule createProtocolSchedule();

  protected abstract TransactionPool createTransactionPool();

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
    final ProcessableBlockHeader blockHeader = createBlock(500_000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);

    final BlockTransactionSelector selector =
        createBlockSelector(
            mainnetTransactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            MIN_OCCUPANCY_80_PERCENT);

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions()).isEmpty();
    assertThat(results.getNotSelectedTransactions()).isEmpty();
    assertThat(results.getReceipts()).isEmpty();
    assertThat(results.getCumulativeGasUsed()).isEqualTo(0);
  }

  @Test
  public void validPendingTransactionIsIncludedInTheBlock() {
    final Transaction transaction = createTransaction(1, Wei.of(7L), 100_000);
    transactionPool.addRemoteTransactions(List.of(transaction));

    ensureTransactionIsValid(transaction, 0, 5);

    final ProcessableBlockHeader blockHeader = createBlock(500_000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);

    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            MIN_OCCUPANCY_80_PERCENT);

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions()).containsExactly(transaction);
    assertThat(results.getNotSelectedTransactions()).isEmpty();
    assertThat(results.getReceipts().size()).isEqualTo(1);
    assertThat(results.getCumulativeGasUsed()).isEqualTo(99995L);
  }

  @Test
  public void invalidTransactionsAreSkippedButBlockStillFills() {
    final List<Transaction> transactionsToInject = Lists.newArrayList();
    for (int i = 0; i < 5; i++) {
      final Transaction tx = createTransaction(i, Wei.of(7), 100_000);
      transactionsToInject.add(tx);
      if (i == 1) {
        ensureTransactionIsInvalid(tx, TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE);
      } else {
        ensureTransactionIsValid(tx);
      }
    }
    transactionPool.addRemoteTransactions(transactionsToInject);

    // The block should fit 4 transactions only
    final ProcessableBlockHeader blockHeader = createBlock(400_000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);

    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            MIN_OCCUPANCY_80_PERCENT);

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    final Transaction invalidTx = transactionsToInject.get(1);

    assertThat(results.getNotSelectedTransactions())
        .containsOnly(
            entry(
                invalidTx,
                TransactionSelectionResult.invalid(
                    TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE.name())));
    assertThat(results.getSelectedTransactions().size()).isEqualTo(4);
    assertThat(results.getSelectedTransactions().contains(invalidTx)).isFalse();
    assertThat(results.getReceipts().size()).isEqualTo(4);
    assertThat(results.getCumulativeGasUsed()).isEqualTo(400_000);
  }

  @Test
  public void subsetOfPendingTransactionsIncludedWhenBlockGasLimitHit() {
    final List<Transaction> transactionsToInject = Lists.newArrayList();
    for (int i = 0; i < 5; i++) {
      final Transaction tx = createTransaction(i, Wei.of(7), 100_000);
      transactionsToInject.add(tx);
      ensureTransactionIsValid(tx);
    }
    transactionPool.addRemoteTransactions(transactionsToInject);

    final ProcessableBlockHeader blockHeader = createBlock(301_000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);

    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            MIN_OCCUPANCY_80_PERCENT);

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions().size()).isEqualTo(3);

    assertThat(results.getSelectedTransactions().containsAll(transactionsToInject.subList(0, 3)))
        .isTrue();
    assertThat(results.getNotSelectedTransactions())
        .containsOnly(
            entry(
                transactionsToInject.get(3),
                TransactionSelectionResult.BLOCK_OCCUPANCY_ABOVE_THRESHOLD));
    assertThat(results.getReceipts().size()).isEqualTo(3);
    assertThat(results.getCumulativeGasUsed()).isEqualTo(300_000);

    // Ensure receipts have the correct cumulative gas
    assertThat(results.getReceipts().get(0).getCumulativeGasUsed()).isEqualTo(100_000);
    assertThat(results.getReceipts().get(1).getCumulativeGasUsed()).isEqualTo(200_000);
    assertThat(results.getReceipts().get(2).getCumulativeGasUsed()).isEqualTo(300_000);
  }

  @Test
  public void transactionTooLargeForBlockDoesNotPreventMoreBeingAddedIfBlockOccupancyNotReached() {
    final ProcessableBlockHeader blockHeader = createBlock(300_000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            MIN_OCCUPANCY_80_PERCENT);

    // Add 3 transactions to the Pending Transactions, 79% of block, 100% of block and 10% of block
    // should end up selecting the first and third only.
    // NOTE - PendingTransactions outputs these in nonce order
    final Transaction[] txs =
        new Transaction[] {
          createTransaction(1, Wei.of(10), (long) (blockHeader.getGasLimit() * 0.79)),
          createTransaction(2, Wei.of(10), blockHeader.getGasLimit()),
          createTransaction(3, Wei.of(10), (long) (blockHeader.getGasLimit() * 0.1))
        };

    for (final Transaction tx : txs) {
      ensureTransactionIsValid(tx);
    }
    transactionPool.addRemoteTransactions(Arrays.stream(txs).toList());

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions()).containsExactly(txs[0], txs[2]);
    assertThat(results.getNotSelectedTransactions())
        .containsOnly(entry(txs[1], TransactionSelectionResult.TX_TOO_LARGE_FOR_REMAINING_GAS));
  }

  @Test
  public void transactionSelectionStopsWhenSufficientBlockOccupancyIsReached() {
    final ProcessableBlockHeader blockHeader = createBlock(300_000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            MIN_OCCUPANCY_80_PERCENT);

    // Add 4 transactions to the Pending Transactions 15% (ok), 79% (ok), 25% (too large), 10%
    // (not included, it would fit, however previous transaction was too large and block was
    // suitably populated).
    // NOTE - PendingTransactions will output these in nonce order.
    final Transaction[] txs =
        new Transaction[] {
          createTransaction(0, Wei.of(10), (long) (blockHeader.getGasLimit() * 0.15)),
          createTransaction(1, Wei.of(10), (long) (blockHeader.getGasLimit() * 0.79)),
          createTransaction(2, Wei.of(10), (long) (blockHeader.getGasLimit() * 0.25)),
          createTransaction(3, Wei.of(10), (long) (blockHeader.getGasLimit() * 0.1))
        };

    for (Transaction tx : txs) {
      ensureTransactionIsValid(tx);
    }
    transactionPool.addRemoteTransactions(Arrays.stream(txs).toList());

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions()).containsExactly(txs[0], txs[1]);
    assertThat(results.getNotSelectedTransactions())
        .containsOnly(entry(txs[2], TransactionSelectionResult.BLOCK_OCCUPANCY_ABOVE_THRESHOLD));
  }

  @Test
  public void transactionSelectionStopsWhenBlockIsFull() {
    final ProcessableBlockHeader blockHeader = createBlock(3_000_000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            MIN_OCCUPANCY_100_PERCENT);

    final long minTxGasCost = getGasCalculator().getMinimumTransactionCost();

    // Add 4 transactions to the Pending Transactions
    // 0) 90% of block (selected)
    // 1) 90% of block (skipped since too large)
    // 2) enough gas to only leave space for a transaction with the min gas cost (selected)
    // 3) min gas cost (selected and 100% block gas used)
    // 4) min gas cost (not selected since selection stopped after tx 3)
    // NOTE - PendingTransactions outputs these in nonce order

    final long gasLimit0 = (long) (blockHeader.getGasLimit() * 0.9);
    final long gasLimit1 = (long) (blockHeader.getGasLimit() * 0.9);
    final long gasLimit2 = blockHeader.getGasLimit() - gasLimit0 - minTxGasCost;
    final long gasLimit3 = minTxGasCost;
    final long gasLimit4 = minTxGasCost;

    final List<Transaction> transactionsToInject = Lists.newArrayList();
    transactionsToInject.add(createTransaction(0, Wei.of(7), gasLimit0));
    transactionsToInject.add(createTransaction(1, Wei.of(7), gasLimit1));
    transactionsToInject.add(createTransaction(2, Wei.of(7), gasLimit2));
    transactionsToInject.add(createTransaction(3, Wei.of(7), gasLimit3));
    transactionsToInject.add(createTransaction(4, Wei.of(7), gasLimit4));

    for (final Transaction tx : transactionsToInject) {
      ensureTransactionIsValid(tx);
    }
    transactionPool.addRemoteTransactions(transactionsToInject);

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions())
        .containsExactly(
            transactionsToInject.get(0), transactionsToInject.get(2), transactionsToInject.get(3));
    assertThat(results.getNotSelectedTransactions())
        .containsOnly(
            entry(
                transactionsToInject.get(1),
                TransactionSelectionResult.TX_TOO_LARGE_FOR_REMAINING_GAS),
            entry(
                transactionsToInject.get(4),
                TransactionSelectionResult.BLOCK_OCCUPANCY_ABOVE_THRESHOLD));
    assertThat(results.getCumulativeGasUsed()).isEqualTo(blockHeader.getGasLimit());
  }

  @Test
  public void transactionSelectionStopsWhenRemainingGasIsNotEnoughForAnyMoreTransaction() {
    final ProcessableBlockHeader blockHeader = createBlock(3_000_000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            MIN_OCCUPANCY_100_PERCENT);

    final long minTxGasCost = getGasCalculator().getMinimumTransactionCost();

    // Add 4 transactions to the Pending Transactions
    // 0) 90% of block (selected)
    // 1) 90% of block (skipped since too large)
    // 2) do not fill the block, but leaves less gas than the min for a tx (selected)
    // 3) min gas cost (skipped since not enough gas remaining)
    // NOTE - PendingTransactions outputs these in nonce order

    final long gasLimit0 = (long) (blockHeader.getGasLimit() * 0.9);
    final long gasLimit1 = (long) (blockHeader.getGasLimit() * 0.9);
    final long gasLimit2 = blockHeader.getGasLimit() - gasLimit0 - (minTxGasCost - 1);
    final long gasLimit3 = minTxGasCost;

    final List<Transaction> transactionsToInject = Lists.newArrayList();
    transactionsToInject.add(createTransaction(0, Wei.of(10), gasLimit0));
    transactionsToInject.add(createTransaction(1, Wei.of(10), gasLimit1));
    transactionsToInject.add(createTransaction(2, Wei.of(10), gasLimit2));
    transactionsToInject.add(createTransaction(3, Wei.of(10), gasLimit3));

    for (final Transaction tx : transactionsToInject) {
      ensureTransactionIsValid(tx);
    }
    transactionPool.addRemoteTransactions(transactionsToInject);

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions())
        .containsExactly(transactionsToInject.get(0), transactionsToInject.get(2));
    assertThat(results.getNotSelectedTransactions())
        .containsOnly(
            entry(
                transactionsToInject.get(1),
                TransactionSelectionResult.TX_TOO_LARGE_FOR_REMAINING_GAS),
            entry(transactionsToInject.get(3), TransactionSelectionResult.BLOCK_FULL));
    assertThat(blockHeader.getGasLimit() - results.getCumulativeGasUsed()).isLessThan(minTxGasCost);
  }

  @Test
  public void shouldDiscardTransactionsThatFailValidation() {
    final ProcessableBlockHeader blockHeader = createBlock(300_000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            MIN_OCCUPANCY_80_PERCENT);

    final Transaction validTransaction = createTransaction(0, Wei.of(10), 21_000);

    ensureTransactionIsValid(validTransaction, 21_000, 0);
    final Transaction invalidTransaction = createTransaction(3, Wei.of(10), 21_000);
    ensureTransactionIsInvalid(
        invalidTransaction, TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE);

    transactionPool.addRemoteTransactions(List.of(validTransaction, invalidTransaction));

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(transactionPool.getTransactionByHash(validTransaction.getHash())).isPresent();
    assertThat(transactionPool.getTransactionByHash(invalidTransaction.getHash())).isNotPresent();
    assertThat(results.getSelectedTransactions()).containsExactly(validTransaction);
    assertThat(results.getNotSelectedTransactions())
        .containsOnly(
            entry(
                invalidTransaction,
                TransactionSelectionResult.invalid(
                    TransactionInvalidReason.UPFRONT_COST_EXCEEDS_BALANCE.name())));
  }

  @Test
  public void transactionSelectionPluginShouldWork_PreProcessing() {
    final ProcessableBlockHeader blockHeader = createBlock(300_000);

    final Transaction selected = createTransaction(0, Wei.of(10), 21_000);
    ensureTransactionIsValid(selected, 21_000, 0);

    final Transaction notSelectedTransient = createTransaction(1, Wei.of(10), 21_000);
    ensureTransactionIsValid(notSelectedTransient, 21_000, 0);

    final Transaction notSelectedInvalid = createTransaction(2, Wei.of(10), 21_000);
    ensureTransactionIsValid(notSelectedInvalid, 21_000, 0);

    final TransactionSelectorFactory transactionSelectorFactory =
        () ->
            new TransactionSelector() {
              @Override
              public TransactionSelectionResult evaluateTransactionPreProcessing(
                  final PendingTransaction pendingTransaction) {
                if (pendingTransaction.getTransaction().equals(notSelectedTransient))
                  return TransactionSelectionResult.invalidTransient("transient");
                if (pendingTransaction.getTransaction().equals(notSelectedInvalid))
                  return TransactionSelectionResult.invalid("invalid");
                return TransactionSelectionResult.SELECTED;
              }

              @Override
              public TransactionSelectionResult evaluateTransactionPostProcessing(
                  final PendingTransaction pendingTransaction,
                  final org.hyperledger.besu.plugin.data.TransactionProcessingResult
                      processingResult) {
                return TransactionSelectionResult.SELECTED;
              }
            };

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelectorWithTxSelPlugin(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            MIN_OCCUPANCY_80_PERCENT,
            transactionSelectorFactory);

    transactionPool.addRemoteTransactions(
        List.of(selected, notSelectedTransient, notSelectedInvalid));

    final TransactionSelectionResults transactionSelectionResults =
        selector.buildTransactionListForBlock();

    assertThat(transactionPool.getTransactionByHash(notSelectedTransient.getHash())).isPresent();
    assertThat(transactionPool.getTransactionByHash(notSelectedInvalid.getHash())).isNotPresent();
    assertThat(transactionSelectionResults.getSelectedTransactions()).containsOnly(selected);
    assertThat(transactionSelectionResults.getNotSelectedTransactions())
        .containsOnly(
            entry(notSelectedTransient, TransactionSelectionResult.invalidTransient("transient")),
            entry(notSelectedInvalid, TransactionSelectionResult.invalid("invalid")));
  }

  @Test
  public void transactionSelectionPluginShouldWork_PostProcessing() {
    final ProcessableBlockHeader blockHeader = createBlock(300_000);

    long maxGasUsedByTransaction = 21_000;

    final Transaction selected = createTransaction(0, Wei.of(10), 21_000);
    ensureTransactionIsValid(selected, maxGasUsedByTransaction, 0);

    // Add + 1 to gasUsedByTransaction so it will fail in the post processing selection
    final Transaction notSelected = createTransaction(1, Wei.of(10), 30_000);
    ensureTransactionIsValid(notSelected, maxGasUsedByTransaction + 1, 0);

    final Transaction selected3 = createTransaction(3, Wei.of(10), 21_000);
    ensureTransactionIsValid(selected3, maxGasUsedByTransaction, 0);

    final TransactionSelectorFactory transactionSelectorFactory =
        () ->
            new TransactionSelector() {
              @Override
              public TransactionSelectionResult evaluateTransactionPreProcessing(
                  final PendingTransaction pendingTransaction) {
                return TransactionSelectionResult.SELECTED;
              }

              @Override
              public TransactionSelectionResult evaluateTransactionPostProcessing(
                  final PendingTransaction pendingTransaction,
                  final org.hyperledger.besu.plugin.data.TransactionProcessingResult
                      processingResult) {
                // the transaction with max gas +1 should fail
                if (processingResult.getEstimateGasUsedByTransaction() > maxGasUsedByTransaction) {
                  return TransactionSelectionResult.invalidTransient("Invalid");
                }
                return TransactionSelectionResult.SELECTED;
              }
            };

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelectorWithTxSelPlugin(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            MIN_OCCUPANCY_80_PERCENT,
            transactionSelectorFactory);

    transactionPool.addRemoteTransactions(List.of(selected, notSelected, selected3));

    final TransactionSelectionResults transactionSelectionResults =
        selector.buildTransactionListForBlock();

    assertThat(transactionSelectionResults.getSelectedTransactions()).contains(selected, selected3);
    assertThat(transactionSelectionResults.getNotSelectedTransactions())
        .containsOnly(entry(notSelected, TransactionSelectionResult.invalidTransient("Invalid")));
  }

  @Test
  public void transactionWithIncorrectNonceRemainsInPoolAndNotSelected() {
    final ProcessableBlockHeader blockHeader = createBlock(5_000_000);

    final Transaction futureTransaction = createTransaction(4, Wei.of(10), 100_000);

    transactionPool.addRemoteTransactions(List.of(futureTransaction));
    ensureTransactionIsInvalid(futureTransaction, TransactionInvalidReason.NONCE_TOO_HIGH);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelector(
            transactionProcessor,
            blockHeader,
            Wei.ZERO,
            miningBeneficiary,
            Wei.ZERO,
            MIN_OCCUPANCY_80_PERCENT);

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(transactionPool.getTransactionByHash(futureTransaction.getHash())).isPresent();
    assertThat(results.getSelectedTransactions()).isEmpty();
    assertThat(results.getNotSelectedTransactions())
        .containsOnly(
            entry(
                futureTransaction,
                TransactionSelectionResult.invalidTransient(
                    TransactionInvalidReason.NONCE_TOO_HIGH.name())));
  }

  protected BlockTransactionSelector createBlockSelector(
      final MainnetTransactionProcessor transactionProcessor,
      final ProcessableBlockHeader blockHeader,
      final Wei minGasPrice,
      final Address miningBeneficiary,
      final Wei blobGasPrice,
      final double minBlockOccupancyRatio) {
    final BlockTransactionSelector selector =
        new BlockTransactionSelector(
            miningParameters
                .getDynamic()
                .setMinTransactionGasPrice(minGasPrice)
                .setMinBlockOccupancyRatio(minBlockOccupancyRatio)
                .toParameters(),
            transactionProcessor,
            blockchain,
            worldState,
            transactionPool,
            blockHeader,
            this::createReceipt,
            //            minGasPrice,
            //            minBlockOccupancyRatio,
            this::isCancelled,
            miningBeneficiary,
            blobGasPrice,
            getFeeMarket(),
            new LondonGasCalculator(),
            GasLimitCalculator.constant(),
            Optional.empty());

    return selector;
  }

  protected BlockTransactionSelector createBlockSelectorWithTxSelPlugin(
      final MainnetTransactionProcessor transactionProcessor,
      final ProcessableBlockHeader blockHeader,
      final Wei minGasPrice,
      final Address miningBeneficiary,
      final Wei blobGasPrice,
      final double minBlockOccupancyRatio,
      final TransactionSelectorFactory transactionSelectorFactory) {
    final BlockTransactionSelector selector =
        new BlockTransactionSelector(
            miningParameters,
            transactionProcessor,
            blockchain,
            worldState,
            transactionPool,
            blockHeader,
            this::createReceipt,
            //            minGasPrice,
            //            minBlockOccupancyRatio,
            this::isCancelled,
            miningBeneficiary,
            blobGasPrice,
            getFeeMarket(),
            new LondonGasCalculator(),
            GasLimitCalculator.constant(),
            Optional.of(transactionSelectorFactory));

    return selector;
  }

  protected GasCalculator getGasCalculator() {
    return protocolSchedule.getByBlockHeader(blockchain.getChainHeadHeader()).getGasCalculator();
  }

  protected FeeMarket getFeeMarket() {
    return protocolSchedule.getByBlockHeader(blockchain.getChainHeadHeader()).getFeeMarket();
  }

  protected Transaction createTransaction(
      final int nonce, final Wei gasPrice, final long gasLimit) {
    return Transaction.builder()
        .gasLimit(gasLimit)
        .gasPrice(gasPrice)
        .nonce(nonce)
        .payload(Bytes.EMPTY)
        .to(Address.ID)
        .value(Wei.of(nonce))
        .sender(sender)
        .chainId(CHAIN_ID)
        .guessType()
        .signAndBuild(keyPair);
  }

  protected Transaction createEIP1559Transaction(
      final int nonce,
      final Wei maxFeePerGas,
      final Wei maxPriorityFeePerGas,
      final long gasLimit) {
    return Transaction.builder()
        .type(TransactionType.EIP1559)
        .gasLimit(gasLimit)
        .maxFeePerGas(maxFeePerGas)
        .maxPriorityFeePerGas(maxPriorityFeePerGas)
        .nonce(nonce)
        .payload(Bytes.EMPTY)
        .to(Address.ID)
        .value(Wei.of(nonce))
        .sender(sender)
        .chainId(CHAIN_ID)
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
