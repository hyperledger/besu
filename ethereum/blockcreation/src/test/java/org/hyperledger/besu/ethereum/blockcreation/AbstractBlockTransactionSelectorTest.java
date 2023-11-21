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
import static org.awaitility.Awaitility.await;
import static org.hyperledger.besu.ethereum.core.MiningParameters.Unstable.DEFAULT_NON_POA_BLOCK_TXS_SELECTION_MAX_TIME;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.BLOCK_SELECTION_TIMEOUT;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.PRIORITY_FEE_PER_GAS_BELOW_CURRENT_MIN;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.SELECTED;
import static org.hyperledger.besu.plugin.data.TransactionSelectionResult.TX_EVALUATION_TOO_LONG;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
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
import org.hyperledger.besu.ethereum.blockcreation.txselection.selectors.AllAcceptingTransactionSelector;
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
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters.MutableInitValues;
import org.hyperledger.besu.ethereum.core.ImmutableMiningParameters.Unstable;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.core.TransactionReceipt;
import org.hyperledger.besu.ethereum.difficulty.fixed.FixedDifficultyProtocolSchedule;
import org.hyperledger.besu.ethereum.eth.manager.EthContext;
import org.hyperledger.besu.ethereum.eth.manager.EthScheduler;
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
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelector;
import org.hyperledger.besu.plugin.services.txselection.PluginTransactionSelectorFactory;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.util.number.Percentage;

import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class AbstractBlockTransactionSelectorTest {
  protected static final double MIN_OCCUPANCY_80_PERCENT = 0.8;
  protected static final double MIN_OCCUPANCY_100_PERCENT = 1;
  protected static final PluginTransactionSelectorFactory NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY =
      () -> AllAcceptingTransactionSelector.INSTANCE;
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
  protected final MiningParameters defaultTestMiningParameters =
      createMiningParameters(
          Wei.ZERO, MIN_OCCUPANCY_80_PERCENT, DEFAULT_NON_POA_BLOCK_TXS_SELECTION_MAX_TIME);

  @Mock protected EthScheduler ethScheduler;

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
    when(ethScheduler.scheduleBlockCreationTask(any(Runnable.class)))
        .thenAnswer(invocation -> CompletableFuture.runAsync(invocation.getArgument(0)));
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
        createBlockSelectorAndSetupTxPool(
            defaultTestMiningParameters,
            mainnetTransactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions()).isEmpty();
    assertThat(results.getNotSelectedTransactions()).isEmpty();
    assertThat(results.getReceipts()).isEmpty();
    assertThat(results.getCumulativeGasUsed()).isEqualTo(0);
  }

  @Test
  public void validPendingTransactionIsIncludedInTheBlock() {
    final ProcessableBlockHeader blockHeader = createBlock(500_000);
    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            defaultTestMiningParameters,
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

    final Transaction transaction = createTransaction(1, Wei.of(7L), 100_000);
    transactionPool.addRemoteTransactions(List.of(transaction));

    ensureTransactionIsValid(transaction, 0, 5);

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions()).containsExactly(transaction);
    assertThat(results.getNotSelectedTransactions()).isEmpty();
    assertThat(results.getReceipts().size()).isEqualTo(1);
    assertThat(results.getCumulativeGasUsed()).isEqualTo(99995L);
  }

  @Test
  public void invalidTransactionsAreSkippedButBlockStillFills() {
    // The block should fit 4 transactions only
    final ProcessableBlockHeader blockHeader = createBlock(400_000);
    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            defaultTestMiningParameters,
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

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
    final ProcessableBlockHeader blockHeader = createBlock(301_000);
    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            defaultTestMiningParameters,
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

    final List<Transaction> transactionsToInject = Lists.newArrayList();
    for (int i = 0; i < 5; i++) {
      final Transaction tx = createTransaction(i, Wei.of(7), 100_000);
      transactionsToInject.add(tx);
      ensureTransactionIsValid(tx);
    }
    transactionPool.addRemoteTransactions(transactionsToInject);

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
        createBlockSelectorAndSetupTxPool(
            defaultTestMiningParameters,
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

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
        createBlockSelectorAndSetupTxPool(
            defaultTestMiningParameters,
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

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
        createBlockSelectorAndSetupTxPool(
            createMiningParameters(
                Wei.ZERO, MIN_OCCUPANCY_100_PERCENT, DEFAULT_NON_POA_BLOCK_TXS_SELECTION_MAX_TIME),
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

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
        createBlockSelectorAndSetupTxPool(
            createMiningParameters(
                Wei.ZERO, MIN_OCCUPANCY_100_PERCENT, DEFAULT_NON_POA_BLOCK_TXS_SELECTION_MAX_TIME),
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

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
        createBlockSelectorAndSetupTxPool(
            defaultTestMiningParameters,
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

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
    final Address miningBeneficiary = AddressHelpers.ofValue(1);

    final Transaction selected = createTransaction(0, Wei.of(10), 21_000);
    ensureTransactionIsValid(selected, 21_000, 0);

    final Transaction notSelectedTransient = createTransaction(1, Wei.of(10), 21_000);
    ensureTransactionIsValid(notSelectedTransient, 21_000, 0);

    final Transaction notSelectedInvalid = createTransaction(2, Wei.of(10), 21_000);
    ensureTransactionIsValid(notSelectedInvalid, 21_000, 0);

    final PluginTransactionSelectorFactory transactionSelectorFactory =
        () ->
            new PluginTransactionSelector() {
              @Override
              public TransactionSelectionResult evaluateTransactionPreProcessing(
                  final PendingTransaction pendingTransaction) {
                if (pendingTransaction.getTransaction().equals(notSelectedTransient))
                  return TransactionSelectionResult.invalidTransient("transient");
                if (pendingTransaction.getTransaction().equals(notSelectedInvalid))
                  return TransactionSelectionResult.invalid("invalid");
                return SELECTED;
              }

              @Override
              public TransactionSelectionResult evaluateTransactionPostProcessing(
                  final PendingTransaction pendingTransaction,
                  final org.hyperledger.besu.plugin.data.TransactionProcessingResult
                      processingResult) {
                return SELECTED;
              }
            };

    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            defaultTestMiningParameters,
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
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

    final PluginTransactionSelectorFactory transactionSelectorFactory =
        () ->
            new PluginTransactionSelector() {
              @Override
              public TransactionSelectionResult evaluateTransactionPreProcessing(
                  final PendingTransaction pendingTransaction) {
                return SELECTED;
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
                return SELECTED;
              }
            };

    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            createMiningParameters(
                Wei.ZERO, MIN_OCCUPANCY_80_PERCENT, DEFAULT_NON_POA_BLOCK_TXS_SELECTION_MAX_TIME),
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            transactionSelectorFactory);

    transactionPool.addRemoteTransactions(List.of(selected, notSelected, selected3));

    final TransactionSelectionResults transactionSelectionResults =
        selector.buildTransactionListForBlock();

    assertThat(transactionSelectionResults.getSelectedTransactions()).contains(selected, selected3);
    assertThat(transactionSelectionResults.getNotSelectedTransactions())
        .containsOnly(entry(notSelected, TransactionSelectionResult.invalidTransient("Invalid")));
  }

  @Test
  public void transactionSelectionPluginShouldBeNotifiedWhenTransactionSelectionCompletes() {
    final PluginTransactionSelectorFactory transactionSelectorFactory =
        mock(PluginTransactionSelectorFactory.class);
    PluginTransactionSelector transactionSelector = spy(AllAcceptingTransactionSelector.INSTANCE);
    when(transactionSelectorFactory.create()).thenReturn(transactionSelector);

    final Transaction transaction = createTransaction(0, Wei.of(10), 21_000);
    ensureTransactionIsValid(transaction, 21_000, 0);

    final TransactionInvalidReason invalidReason = TransactionInvalidReason.PLUGIN_TX_VALIDATOR;
    final Transaction invalidTransaction = createTransaction(1, Wei.of(10), 21_000);
    ensureTransactionIsInvalid(invalidTransaction, TransactionInvalidReason.PLUGIN_TX_VALIDATOR);

    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            defaultTestMiningParameters,
            transactionProcessor,
            createBlock(300_000),
            AddressHelpers.ofValue(1),
            Wei.ZERO,
            transactionSelectorFactory);

    transactionPool.addRemoteTransactions(List.of(transaction, invalidTransaction));

    selector.buildTransactionListForBlock();

    ArgumentCaptor<PendingTransaction> argumentCaptor =
        ArgumentCaptor.forClass(PendingTransaction.class);

    // selected transaction must be notified to the selector
    verify(transactionSelector)
        .onTransactionSelected(argumentCaptor.capture(), any(TransactionProcessingResult.class));
    PendingTransaction selected = argumentCaptor.getValue();
    assertThat(selected.getTransaction()).isEqualTo(transaction);

    // unselected transaction must be notified to the selector with correct reason
    verify(transactionSelector)
        .onTransactionNotSelected(
            argumentCaptor.capture(),
            eq(TransactionSelectionResult.invalid(invalidReason.toString())));
    PendingTransaction rejectedTransaction = argumentCaptor.getValue();
    assertThat(rejectedTransaction.getTransaction()).isEqualTo(invalidTransaction);
  }

  @Test
  public void transactionWithIncorrectNonceRemainsInPoolAndNotSelected() {
    final ProcessableBlockHeader blockHeader = createBlock(5_000_000);
    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            defaultTestMiningParameters,
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

    final Transaction futureTransaction = createTransaction(4, Wei.of(10), 100_000);

    transactionPool.addRemoteTransactions(List.of(futureTransaction));
    ensureTransactionIsInvalid(futureTransaction, TransactionInvalidReason.NONCE_TOO_HIGH);

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

  @Test
  public void increaseOfMinGasPriceAtRuntimeExcludeTxFromBeingSelected() {
    final Transaction transaction = createTransaction(0, Wei.of(7L), 100_000);
    final ProcessableBlockHeader blockHeader = createBlock(500_000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);

    final MiningParameters miningParameters =
        ImmutableMiningParameters.builder().from(defaultTestMiningParameters).build();

    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            miningParameters,
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

    transactionPool.addRemoteTransactions(List.of(transaction));

    ensureTransactionIsValid(transaction, 0, 5);

    // raise the minGasPrice at runtime from 1 wei to 10 wei
    miningParameters.setMinTransactionGasPrice(Wei.of(10));

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    // now the tx gasPrice is below the new minGasPrice, it is not selected but stays in the pool
    assertThat(results.getSelectedTransactions()).isEmpty();
    assertThat(results.getNotSelectedTransactions())
        .containsOnly(entry(transaction, TransactionSelectionResult.CURRENT_TX_PRICE_BELOW_MIN));
    assertThat(transactionPool.getPendingTransactions())
        .map(PendingTransaction::getTransaction)
        .containsOnly(transaction);
  }

  @Test
  public void decreaseOfMinGasPriceAtRuntimeIncludeTxThatWasPreviouslyNotSelected() {
    final Transaction transaction = createTransaction(0, Wei.of(7L), 100_000);
    final MiningParameters miningParameters =
        ImmutableMiningParameters.builder().from(defaultTestMiningParameters).build();
    final ProcessableBlockHeader blockHeader = createBlock(500_000);

    final Address miningBeneficiary = AddressHelpers.ofValue(1);

    final BlockTransactionSelector selector1 =
        createBlockSelectorAndSetupTxPool(
            miningParameters,
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);
    transactionPool.addRemoteTransactions(List.of(transaction));

    ensureTransactionIsValid(transaction, 0, 5);

    // raise the minGasPrice at runtime from 1 wei to 10 wei
    miningParameters.setMinTransactionGasPrice(Wei.of(10));

    final TransactionSelectionResults results1 = selector1.buildTransactionListForBlock();

    // now the tx gasPrice is below the new minGasPrice, it is not selected but stays in the pool
    assertThat(results1.getSelectedTransactions()).isEmpty();
    assertThat(results1.getNotSelectedTransactions())
        .containsOnly(entry(transaction, TransactionSelectionResult.CURRENT_TX_PRICE_BELOW_MIN));
    assertThat(transactionPool.getPendingTransactions())
        .map(PendingTransaction::getTransaction)
        .containsOnly(transaction);

    // decrease the minGasPrice at runtime from 10 wei to 5 wei
    miningParameters.setMinTransactionGasPrice(Wei.of(5));

    final BlockTransactionSelector selector2 =
        createBlockSelector(
            miningParameters,
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

    final TransactionSelectionResults results2 = selector2.buildTransactionListForBlock();

    // now the tx gasPrice is above the new minGasPrice and it is selected
    assertThat(results2.getSelectedTransactions()).contains(transaction);
    assertThat(results2.getNotSelectedTransactions()).isEmpty();
  }

  @Test
  public void shouldNotSelectTransactionsWithPriorityFeeLessThanConfig() {
    ProcessableBlockHeader blockHeader = createBlock(5_000_000, Wei.ONE);
    final MiningParameters miningParameters =
        ImmutableMiningParameters.builder().from(defaultTestMiningParameters).build();
    miningParameters.setMinPriorityFeePerGas(Wei.of(7));
    final Transaction txSelected = createTransaction(1, Wei.of(8), 100_000);
    ensureTransactionIsValid(txSelected);
    // transaction txNotSelected should not be selected
    final Transaction txNotSelected = createTransaction(2, Wei.of(7), 100_000);
    ensureTransactionIsValid(txNotSelected);

    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            miningParameters,
            transactionProcessor,
            blockHeader,
            AddressHelpers.ofValue(1),
            Wei.ZERO,
            NO_PLUGIN_TRANSACTION_SELECTOR_FACTORY);

    transactionPool.addRemoteTransactions(List.of(txSelected, txNotSelected));

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    assertThat(results.getSelectedTransactions()).containsOnly(txSelected);
    assertThat(results.getNotSelectedTransactions())
        .containsOnly(entry(txNotSelected, PRIORITY_FEE_PER_GAS_BELOW_CURRENT_MIN));
  }

  @ParameterizedTest
  @MethodSource("subsetOfPendingTransactionsIncludedWhenTxSelectionMaxTimeIsOver")
  public void subsetOfPendingTransactionsIncludedWhenTxSelectionMaxTimeIsOver(
      final boolean isPoa,
      final boolean preProcessingTooLate,
      final boolean processingTooLate,
      final boolean postProcessingTooLate) {

    internalBlockSelectionTimeoutSimulation(
        isPoa,
        preProcessingTooLate,
        processingTooLate,
        postProcessingTooLate,
        500,
        BLOCK_SELECTION_TIMEOUT,
        false);
  }

  @ParameterizedTest
  @MethodSource("subsetOfPendingTransactionsIncludedWhenTxSelectionMaxTimeIsOver")
  public void pendingTransactionsThatTakesTooLongToEvaluateIsDroppedFromThePool(
      final boolean isPoa,
      final boolean preProcessingTooLate,
      final boolean processingTooLate,
      final boolean postProcessingTooLate) {

    internalBlockSelectionTimeoutSimulation(
        isPoa,
        preProcessingTooLate,
        processingTooLate,
        postProcessingTooLate,
        900,
        TX_EVALUATION_TOO_LONG,
        true);
  }

  private void internalBlockSelectionTimeoutSimulation(
      final boolean isPoa,
      final boolean preProcessingTooLate,
      final boolean processingTooLate,
      final boolean postProcessingTooLate,
      final long longProcessingTxTime,
      final TransactionSelectionResult longProcessingTxResult,
      final boolean isLongProcessingTxDropped) {

    final long fastProcessingTxTime = 200;

    final Supplier<Answer<TransactionSelectionResult>> inTime = () -> invocation -> SELECTED;

    final BiFunction<Transaction, Long, Answer<TransactionSelectionResult>> tooLate =
        (p, t) ->
            invocation -> {
              final org.hyperledger.besu.ethereum.eth.transactions.PendingTransaction ptx =
                  invocation.getArgument(0);
              if (ptx.getTransaction().equals(p)) {
                Thread.sleep(t);
              } else {
                Thread.sleep(fastProcessingTxTime);
              }
              return SELECTED;
            };

    final ProcessableBlockHeader blockHeader = createBlock(301_000);
    final Address miningBeneficiary = AddressHelpers.ofValue(1);
    final int poaMinBlockTime = 1;
    final long blockTxsSelectionMaxTime = 750;

    final List<Transaction> transactionsToInject = new ArrayList<>(3);
    for (int i = 0; i < 2; i++) {
      final Transaction tx = createTransaction(i, Wei.of(7), 100_000);
      transactionsToInject.add(tx);
      ensureTransactionIsValid(tx, 0, 0, processingTooLate ? fastProcessingTxTime : 0);
    }

    final Transaction lateTx = createTransaction(2, Wei.of(7), 100_000);
    transactionsToInject.add(lateTx);
    ensureTransactionIsValid(lateTx, 0, 0, processingTooLate ? longProcessingTxTime : 0);

    PluginTransactionSelector transactionSelector = mock(PluginTransactionSelector.class);
    when(transactionSelector.evaluateTransactionPreProcessing(any()))
        .thenAnswer(
            preProcessingTooLate ? tooLate.apply(lateTx, longProcessingTxTime) : inTime.get());

    when(transactionSelector.evaluateTransactionPostProcessing(any(), any()))
        .thenAnswer(
            postProcessingTooLate ? tooLate.apply(lateTx, longProcessingTxTime) : inTime.get());

    final PluginTransactionSelectorFactory transactionSelectorFactory =
        mock(PluginTransactionSelectorFactory.class);
    when(transactionSelectorFactory.create()).thenReturn(transactionSelector);

    final BlockTransactionSelector selector =
        createBlockSelectorAndSetupTxPool(
            isPoa
                ? createMiningParameters(
                    Wei.ZERO, MIN_OCCUPANCY_100_PERCENT, poaMinBlockTime, Percentage.fromInt(75))
                : createMiningParameters(
                    Wei.ZERO, MIN_OCCUPANCY_100_PERCENT, blockTxsSelectionMaxTime),
            transactionProcessor,
            blockHeader,
            miningBeneficiary,
            Wei.ZERO,
            transactionSelectorFactory);

    transactionPool.addRemoteTransactions(transactionsToInject);

    final TransactionSelectionResults results = selector.buildTransactionListForBlock();

    // third tx is not selected, even if it could fit in the block,
    // since the selection time was over
    assertThat(results.getSelectedTransactions().size()).isEqualTo(2);

    assertThat(results.getSelectedTransactions().containsAll(transactionsToInject.subList(0, 2)))
        .isTrue();

    assertThat(results.getReceipts().size()).isEqualTo(2);
    assertThat(results.getCumulativeGasUsed()).isEqualTo(200_000);

    // Ensure receipts have the correct cumulative gas
    assertThat(results.getReceipts().get(0).getCumulativeGasUsed()).isEqualTo(100_000);
    assertThat(results.getReceipts().get(1).getCumulativeGasUsed()).isEqualTo(200_000);

    // given enough time we can check the not selected tx
    await().until(() -> !results.getNotSelectedTransactions().isEmpty());
    assertThat(results.getNotSelectedTransactions())
        .containsOnly(entry(lateTx, longProcessingTxResult));
    assertThat(transactionPool.getTransactionByHash(lateTx.getHash()).isEmpty())
        .isEqualTo(isLongProcessingTxDropped ? true : false);
  }

  private static Stream<Arguments>
      subsetOfPendingTransactionsIncludedWhenTxSelectionMaxTimeIsOver() {

    return Stream.of(
        Arguments.of(false, true, false, false),
        Arguments.of(false, false, true, false),
        Arguments.of(false, false, false, true),
        Arguments.of(true, true, false, false),
        Arguments.of(true, false, true, false),
        Arguments.of(true, false, false, true));
  }

  protected BlockTransactionSelector createBlockSelectorAndSetupTxPool(
      final MiningParameters miningParameters,
      final MainnetTransactionProcessor transactionProcessor,
      final ProcessableBlockHeader blockHeader,
      final Address miningBeneficiary,
      final Wei blobGasPrice,
      final PluginTransactionSelectorFactory transactionSelectorFactory) {

    transactionPool = createTransactionPool();

    return createBlockSelector(
        miningParameters,
        transactionProcessor,
        blockHeader,
        miningBeneficiary,
        blobGasPrice,
        transactionSelectorFactory);
  }

  protected BlockTransactionSelector createBlockSelector(
      final MiningParameters miningParameters,
      final MainnetTransactionProcessor transactionProcessor,
      final ProcessableBlockHeader blockHeader,
      final Address miningBeneficiary,
      final Wei blobGasPrice,
      final PluginTransactionSelectorFactory transactionSelectorFactory) {

    final BlockTransactionSelector selector =
        new BlockTransactionSelector(
            miningParameters,
            transactionProcessor,
            blockchain,
            worldState,
            transactionPool,
            blockHeader,
            this::createReceipt,
            this::isCancelled,
            miningBeneficiary,
            blobGasPrice,
            getFeeMarket(),
            new LondonGasCalculator(),
            GasLimitCalculator.constant(),
            transactionSelectorFactory.create(),
            ethScheduler);

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
    ensureTransactionIsValid(tx, gasUsedByTransaction, gasRemaining, 0);
  }

  protected void ensureTransactionIsValid(
      final Transaction tx,
      final long gasUsedByTransaction,
      final long gasRemaining,
      final long processingTime) {
    when(transactionProcessor.processTransaction(
            any(), any(), any(), eq(tx), any(), any(), any(), anyBoolean(), any(), any()))
        .thenAnswer(
            invocation -> {
              if (processingTime > 0) {
                Thread.sleep(processingTime);
              }
              return TransactionProcessingResult.successful(
                  new ArrayList<>(),
                  gasUsedByTransaction,
                  gasRemaining,
                  Bytes.EMPTY,
                  ValidationResult.valid());
            });
  }

  protected void ensureTransactionIsInvalid(
      final Transaction tx, final TransactionInvalidReason invalidReason) {
    when(transactionProcessor.processTransaction(
            any(), any(), any(), eq(tx), any(), any(), any(), anyBoolean(), any(), any()))
        .thenReturn(TransactionProcessingResult.invalid(ValidationResult.invalid(invalidReason)));
  }

  private BlockHeader blockHeader(final long number) {
    return new BlockHeaderTestFixture().number(number).buildHeader();
  }

  protected MiningParameters createMiningParameters(
      final Wei minGasPrice, final double minBlockOccupancyRatio, final long txsSelectionMaxTime) {
    return ImmutableMiningParameters.builder()
        .mutableInitValues(
            MutableInitValues.builder()
                .minTransactionGasPrice(minGasPrice)
                .minBlockOccupancyRatio(minBlockOccupancyRatio)
                .build())
        .unstable(Unstable.builder().nonPoaBlockTxsSelectionMaxTime(txsSelectionMaxTime).build())
        .build();
  }

  protected MiningParameters createMiningParameters(
      final Wei minGasPrice,
      final double minBlockOccupancyRatio,
      final int minBlockTime,
      final Percentage minBlockTimePercentage) {
    return ImmutableMiningParameters.builder()
        .mutableInitValues(
            MutableInitValues.builder()
                .minTransactionGasPrice(minGasPrice)
                .minBlockOccupancyRatio(minBlockOccupancyRatio)
                .build())
        .unstable(
            Unstable.builder()
                .minBlockTime(minBlockTime)
                .poaBlockTxsSelectionMaxTime(minBlockTimePercentage)
                .build())
        .build();
  }
}
