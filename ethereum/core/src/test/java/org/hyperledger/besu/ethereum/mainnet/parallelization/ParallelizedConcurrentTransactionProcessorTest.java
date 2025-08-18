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
package org.hyperledger.besu.ethereum.mainnet.parallelization;

import static org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.WorldStateConfig.createStatefulConfigWithTrie;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.InMemoryKeyValueStorageProvider;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.TransactionValidationParams;
import org.hyperledger.besu.ethereum.mainnet.ValidationResult;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.CodeCache;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.NoOpBonsaiCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.NoopBonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.NoOpTrieLogManager;
import org.hyperledger.besu.ethereum.trie.pathbased.common.worldview.accumulator.PathBasedWorldStateUpdateAccumulator;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;

import java.util.Collections;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParallelizedConcurrentTransactionProcessorTest {

  @Mock private MainnetTransactionProcessor transactionProcessor;
  @Mock private BlockHeader chainHeadBlockHeader;
  @Mock private BlockHeader blockHeader;
  @Mock ProtocolContext protocolContext;
  @Mock private Transaction transaction;
  @Mock private TransactionCollisionDetector transactionCollisionDetector;

  private BonsaiWorldState worldState;

  private ParallelizedConcurrentTransactionProcessor processor;

  @BeforeEach
  void setUp() {
    processor =
        new ParallelizedConcurrentTransactionProcessor(
            transactionProcessor, transactionCollisionDetector);
    final BonsaiWorldStateKeyValueStorage bonsaiWorldStateKeyValueStorage =
        new BonsaiWorldStateKeyValueStorage(
            new InMemoryKeyValueStorageProvider(),
            new NoOpMetricsSystem(),
            DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);
    worldState =
        new BonsaiWorldState(
            bonsaiWorldStateKeyValueStorage,
            new NoopBonsaiCachedMerkleTrieLoader(),
            new NoOpBonsaiCachedWorldStorageManager(
                bonsaiWorldStateKeyValueStorage, EvmConfiguration.DEFAULT, new CodeCache()),
            new NoOpTrieLogManager(),
            EvmConfiguration.DEFAULT,
            createStatefulConfigWithTrie(),
            new CodeCache());

    when(chainHeadBlockHeader.getHash()).thenReturn(Hash.ZERO);
    when(chainHeadBlockHeader.getStateRoot()).thenReturn(Hash.EMPTY_TRIE_HASH);
    when(blockHeader.getParentHash()).thenReturn(Hash.ZERO);

    when(transaction.detachedCopy()).thenReturn(transaction);

    final MutableBlockchain blockchain = mock(MutableBlockchain.class);
    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHeadHeader()).thenReturn(chainHeadBlockHeader);
    final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
    when(protocolContext.getWorldStateArchive()).thenReturn(worldStateArchive);
    when(worldStateArchive.getWorldState(any())).thenReturn(Optional.of(worldState));
    when(transactionCollisionDetector.hasCollision(any(), any(), any(), any())).thenReturn(false);
  }

  @Test
  void testRunTransaction() {
    Address miningBeneficiary = Address.fromHexString("0x1");
    Wei blobGasPrice = Wei.ZERO;

    Mockito.when(
            transactionProcessor.processTransaction(
                any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            TransactionProcessingResult.successful(
                Collections.emptyList(), 0, 0, Bytes.EMPTY, ValidationResult.valid()));

    processor.runTransaction(
        protocolContext,
        blockHeader,
        0,
        transaction,
        miningBeneficiary,
        (__, ___) -> Hash.EMPTY,
        blobGasPrice);

    verify(transactionProcessor, times(1))
        .processTransaction(
            any(PathBasedWorldStateUpdateAccumulator.class),
            eq(blockHeader),
            eq(transaction),
            eq(miningBeneficiary),
            any(OperationTracer.class),
            any(BlockHashLookup.class),
            eq(TransactionValidationParams.processingBlock()),
            eq(blobGasPrice));

    assertTrue(
        processor
            .applyParallelizedTransactionResult(
                worldState, miningBeneficiary, transaction, 0, Optional.empty(), Optional.empty())
            .isPresent(),
        "Expected the transaction context to be stored");
  }

  @Test
  void testRunTransactionWithFailure() {
    Address miningBeneficiary = Address.fromHexString("0x1");
    Wei blobGasPrice = Wei.ZERO;

    when(transactionProcessor.processTransaction(
            any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            TransactionProcessingResult.failed(
                0,
                0,
                ValidationResult.invalid(
                    TransactionInvalidReason.BLOB_GAS_PRICE_BELOW_CURRENT_BLOB_BASE_FEE),
                Optional.of(Bytes.EMPTY),
                Optional.empty()));

    processor.runTransaction(
        protocolContext,
        blockHeader,
        0,
        transaction,
        miningBeneficiary,
        (__, ___) -> Hash.EMPTY,
        blobGasPrice);

    Optional<TransactionProcessingResult> result =
        processor.applyParallelizedTransactionResult(
            worldState, miningBeneficiary, transaction, 0, Optional.empty(), Optional.empty());
    assertTrue(result.isEmpty(), "Expected the transaction result to indicate a failure");
  }

  @Test
  void testRunTransactionWithConflict() {

    Address miningBeneficiary = Address.fromHexString("0x1");
    Wei blobGasPrice = Wei.ZERO;

    Mockito.when(
            transactionProcessor.processTransaction(
                any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            TransactionProcessingResult.successful(
                Collections.emptyList(), 0, 0, Bytes.EMPTY, ValidationResult.valid()));

    processor.runTransaction(
        protocolContext,
        blockHeader,
        0,
        transaction,
        miningBeneficiary,
        (__, ___) -> Hash.EMPTY,
        blobGasPrice);

    verify(transactionProcessor, times(1))
        .processTransaction(
            any(PathBasedWorldStateUpdateAccumulator.class),
            eq(blockHeader),
            eq(transaction),
            eq(miningBeneficiary),
            any(OperationTracer.class),
            any(BlockHashLookup.class),
            eq(TransactionValidationParams.processingBlock()),
            eq(blobGasPrice));

    // simulate a conflict
    when(transactionCollisionDetector.hasCollision(any(), any(), any(), any())).thenReturn(true);

    Optional<TransactionProcessingResult> result =
        processor.applyParallelizedTransactionResult(
            worldState, miningBeneficiary, transaction, 0, Optional.empty(), Optional.empty());
    assertTrue(result.isEmpty(), "Expected no transaction result to be applied due to conflict");
  }
}
