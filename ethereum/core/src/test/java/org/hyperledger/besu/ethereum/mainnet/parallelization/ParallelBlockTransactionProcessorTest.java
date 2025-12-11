/*
 * Copyright contributors to Besu.
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
import static org.mockito.ArgumentMatchers.argThat;
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
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BlockAccessListBuilder;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.PartialBlockAccessView;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.transaction.TransactionInvalidReason;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.CodeCache;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.NoOpBonsaiCachedWorldStorageManager;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.NoopBonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.worldview.BonsaiWorldState;
import org.hyperledger.besu.ethereum.trie.pathbased.common.trielog.NoOpTrieLogManager;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.evm.tracing.OperationTracer;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParallelBlockTransactionProcessorTest {

  private enum ProcessorVariant {
    PARALLELIZED,
    BAL
  }

  private final Executor sameThreadExecutor = Runnable::run;
  private static final Address MINING_BENEFICIARY = Address.fromHexString("0x1");
  private static final Wei BLOB_GAS_PRICE = Wei.ZERO;

  private Stream<ProcessorVariant> processorVariants() {
    return Stream.of(ProcessorVariant.PARALLELIZED, ProcessorVariant.BAL);
  }

  private BonsaiWorldState createEmptyWorldState() {
    final BonsaiWorldStateKeyValueStorage bonsaiWorldStateKeyValueStorage =
        new BonsaiWorldStateKeyValueStorage(
            new InMemoryKeyValueStorageProvider(),
            new NoOpMetricsSystem(),
            DataStorageConfiguration.DEFAULT_BONSAI_CONFIG);

    return new BonsaiWorldState(
        bonsaiWorldStateKeyValueStorage,
        new NoopBonsaiCachedMerkleTrieLoader(),
        new NoOpBonsaiCachedWorldStorageManager(
            bonsaiWorldStateKeyValueStorage, EvmConfiguration.DEFAULT, new CodeCache()),
        new NoOpTrieLogManager(),
        EvmConfiguration.DEFAULT,
        createStatefulConfigWithTrie(),
        new CodeCache());
  }

  private ParallelBlockTransactionProcessor createProcessor(
      final ProcessorVariant variant,
      final MainnetTransactionProcessor transactionProcessor,
      final TransactionCollisionDetector collisionDetector,
      final BlockAccessList blockAccessList) {

    return switch (variant) {
      case PARALLELIZED ->
          new ParallelizedConcurrentTransactionProcessor(transactionProcessor, collisionDetector);
      case BAL -> new BalConcurrentTransactionProcessor(transactionProcessor, blockAccessList);
    };
  }

  private record TestEnvironment(
      ProtocolContext protocolContext, BlockHeader blockHeader, BonsaiWorldState worldState) {}

  private TestEnvironment createTestEnvironment() {
    final ProtocolContext protocolContext = mock(ProtocolContext.class);
    final MutableBlockchain blockchain = mock(MutableBlockchain.class);
    final BlockHeader chainHeadBlockHeader = mock(BlockHeader.class);
    final BlockHeader blockHeader = mock(BlockHeader.class);
    final WorldStateArchive worldStateArchive = mock(WorldStateArchive.class);
    final BonsaiWorldState worldState = createEmptyWorldState();

    when(protocolContext.getBlockchain()).thenReturn(blockchain);
    when(blockchain.getChainHeadHeader()).thenReturn(chainHeadBlockHeader);
    when(chainHeadBlockHeader.getHash()).thenReturn(Hash.ZERO);
    when(chainHeadBlockHeader.getStateRoot()).thenReturn(Hash.EMPTY_TRIE_HASH);

    when(blockHeader.getParentHash()).thenReturn(Hash.ZERO);

    when(protocolContext.getWorldStateArchive()).thenReturn(worldStateArchive);
    when(worldStateArchive.getWorldState(any())).thenReturn(Optional.of(worldState));

    return new TestEnvironment(protocolContext, blockHeader, worldState);
  }

  private BlockAccessList mockEmptyBlockAccessList() {
    final BlockAccessList blockAccessList = mock(BlockAccessList.class);
    when(blockAccessList.accountChanges()).thenReturn(Collections.emptyList());
    return blockAccessList;
  }

  private Transaction mockTransaction() {
    final Transaction transaction = mock(Transaction.class);
    when(transaction.detachedCopy()).thenReturn(transaction);
    return transaction;
  }

  private record ProcessorTestFixture(
      ProcessorVariant variant,
      MainnetTransactionProcessor transactionProcessor,
      TransactionCollisionDetector collisionDetector,
      BlockAccessList blockAccessList,
      Transaction transaction,
      TestEnvironment env,
      ParallelBlockTransactionProcessor processor) {}

  private ProcessorTestFixture createFixture(final ProcessorVariant variant) {
    final MainnetTransactionProcessor transactionProcessor =
        mock(MainnetTransactionProcessor.class);
    final TransactionCollisionDetector collisionDetector = mock(TransactionCollisionDetector.class);
    final BlockAccessList blockAccessList = mockEmptyBlockAccessList();
    final Transaction transaction = mockTransaction();
    final TestEnvironment env = createTestEnvironment();
    final ParallelBlockTransactionProcessor processor =
        createProcessor(variant, transactionProcessor, collisionDetector, blockAccessList);

    if (variant == ProcessorVariant.PARALLELIZED) {
      when(collisionDetector.hasCollision(any(), any(), any(), any())).thenReturn(false);
    }

    return new ProcessorTestFixture(
        variant,
        transactionProcessor,
        collisionDetector,
        blockAccessList,
        transaction,
        env,
        processor);
  }

  private void stubSuccessfulTransaction(
      final MainnetTransactionProcessor transactionProcessor,
      final Optional<PartialBlockAccessView> partialView) {

    when(transactionProcessor.processTransaction(
            any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            TransactionProcessingResult.successful(
                Collections.emptyList(), 0, 0, Bytes.EMPTY, partialView, ValidationResult.valid()));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("processorVariants")
  void testRunTransaction(final ProcessorVariant variant) {
    final ProcessorTestFixture f = createFixture(variant);
    stubSuccessfulTransaction(f.transactionProcessor(), Optional.empty());

    f.processor()
        .runAsyncBlock(
            f.env().protocolContext(),
            f.env().blockHeader(),
            Collections.singletonList(f.transaction()),
            MINING_BENEFICIARY,
            (__, ___) -> Hash.EMPTY,
            BLOB_GAS_PRICE,
            sameThreadExecutor,
            Optional.empty());

    verify(f.transactionProcessor(), times(1))
        .processTransaction(
            any(WorldUpdater.class),
            eq(f.env().blockHeader()),
            eq(f.transaction()),
            eq(MINING_BENEFICIARY),
            any(OperationTracer.class),
            any(BlockHashLookup.class),
            eq(TransactionValidationParams.processingBlock()),
            eq(BLOB_GAS_PRICE),
            eq(Optional.empty()));

    final Optional<TransactionProcessingResult> maybeResult =
        f.processor()
            .getProcessingResult(
                f.env().worldState(),
                MINING_BENEFICIARY,
                f.transaction(),
                0,
                Optional.empty(),
                Optional.empty());

    assertTrue(maybeResult.isPresent(), "Expected the transaction result to be present");
    assertTrue(maybeResult.get().isSuccessful(), "Expected the processing to be successful");
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("processorVariants")
  void testRunTransactionWithFailure(final ProcessorVariant variant) {
    final ProcessorTestFixture f = createFixture(variant);
    when(f.transactionProcessor()
            .processTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(
            TransactionProcessingResult.failed(
                0,
                0,
                ValidationResult.invalid(
                    TransactionInvalidReason.BLOB_GAS_PRICE_BELOW_CURRENT_BLOB_BASE_FEE),
                Optional.of(Bytes.EMPTY),
                Optional.empty(),
                Optional.empty()));

    f.processor()
        .runAsyncBlock(
            f.env().protocolContext(),
            f.env().blockHeader(),
            Collections.singletonList(f.transaction()),
            MINING_BENEFICIARY,
            (__, ___) -> Hash.EMPTY,
            BLOB_GAS_PRICE,
            sameThreadExecutor,
            Optional.empty());

    final Optional<TransactionProcessingResult> maybeResult =
        f.processor()
            .getProcessingResult(
                f.env().worldState(),
                MINING_BENEFICIARY,
                f.transaction(),
                0,
                Optional.empty(),
                Optional.empty());

    assertTrue(
        maybeResult.isEmpty(),
        "Expected empty result so the block processor re-executes the transaction");
  }

  @Test
  void testRunTransactionWithConflict() {
    final ProcessorTestFixture f = createFixture(ProcessorVariant.PARALLELIZED);
    stubSuccessfulTransaction(f.transactionProcessor(), Optional.empty());

    f.processor()
        .runAsyncBlock(
            f.env().protocolContext(),
            f.env().blockHeader(),
            Collections.singletonList(f.transaction()),
            MINING_BENEFICIARY,
            (__, ___) -> Hash.EMPTY,
            BLOB_GAS_PRICE,
            sameThreadExecutor,
            Optional.empty());

    verify(f.transactionProcessor(), times(1))
        .processTransaction(
            any(WorldUpdater.class),
            eq(f.env().blockHeader()),
            eq(f.transaction()),
            eq(MINING_BENEFICIARY),
            any(OperationTracer.class),
            any(BlockHashLookup.class),
            eq(TransactionValidationParams.processingBlock()),
            eq(BLOB_GAS_PRICE),
            eq(Optional.empty()));

    // simulate a conflict
    when(f.collisionDetector().hasCollision(any(), any(), any(), any())).thenReturn(true);

    final Optional<TransactionProcessingResult> maybeResult =
        f.processor()
            .getProcessingResult(
                f.env().worldState(),
                MINING_BENEFICIARY,
                f.transaction(),
                0,
                Optional.empty(),
                Optional.empty());

    assertTrue(
        maybeResult.isEmpty(), "Expected no transaction result to be applied due to conflict");
  }

  @Test
  void testApplyResultUsesAccessLocationTrackerAndUpdatesPartialBlockAccessView() {
    final ProcessorTestFixture f = createFixture(ProcessorVariant.PARALLELIZED);

    final PartialBlockAccessView partialView = mock(PartialBlockAccessView.class);
    final PartialBlockAccessView.AccountChanges beneficiaryChanges =
        mock(PartialBlockAccessView.AccountChanges.class);
    when(beneficiaryChanges.getAddress()).thenReturn(MINING_BENEFICIARY);
    when(partialView.accountChanges()).thenReturn(Collections.singletonList(beneficiaryChanges));

    stubSuccessfulTransaction(f.transactionProcessor(), Optional.of(partialView));

    final BlockAccessListBuilder balBuilder = mock(BlockAccessListBuilder.class);

    f.processor()
        .runAsyncBlock(
            f.env().protocolContext(),
            f.env().blockHeader(),
            Collections.singletonList(f.transaction()),
            MINING_BENEFICIARY,
            (__, ___) -> Hash.EMPTY,
            BLOB_GAS_PRICE,
            sameThreadExecutor,
            Optional.of(balBuilder));

    verify(f.transactionProcessor())
        .processTransaction(
            any(WorldUpdater.class),
            eq(f.env().blockHeader()),
            eq(f.transaction()),
            eq(MINING_BENEFICIARY),
            any(OperationTracer.class),
            any(BlockHashLookup.class),
            eq(TransactionValidationParams.processingBlock()),
            eq(BLOB_GAS_PRICE),
            argThat(Optional::isPresent));

    final Optional<TransactionProcessingResult> maybeResult =
        f.processor()
            .getProcessingResult(
                f.env().worldState(),
                MINING_BENEFICIARY,
                f.transaction(),
                0,
                Optional.empty(),
                Optional.empty());

    assertTrue(
        maybeResult.isPresent(), "Expected the parallelized transaction result to be applied");
    final TransactionProcessingResult result = maybeResult.get();
    assertTrue(result.getPartialBlockAccessView().isPresent(), "Expected BAL view to be present");
    verify(beneficiaryChanges).setPostBalance(any(Wei.class));
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("processorVariants")
  void testGetProcessingResultReturnsEmptyWhenProcessTransactionThrows(
      final ProcessorVariant variant) {
    final ProcessorTestFixture f = createFixture(variant);

    when(f.transactionProcessor()
            .processTransaction(any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("boom"));

    final Counter confirmedCounter = mock(Counter.class);
    final Counter conflictCounter = mock(Counter.class);

    f.processor()
        .runAsyncBlock(
            f.env().protocolContext(),
            f.env().blockHeader(),
            Collections.singletonList(f.transaction()),
            MINING_BENEFICIARY,
            (__, ___) -> Hash.EMPTY,
            BLOB_GAS_PRICE,
            sameThreadExecutor,
            Optional.empty());

    final Optional<TransactionProcessingResult> maybeResult =
        f.processor()
            .getProcessingResult(
                f.env().worldState(),
                MINING_BENEFICIARY,
                f.transaction(),
                0,
                Optional.of(confirmedCounter),
                Optional.of(conflictCounter));

    assertTrue(
        maybeResult.isEmpty(),
        "Expected empty result when processTransaction throws, so block processor re-executes");

    verify(confirmedCounter, times(0)).inc();
    verify(conflictCounter, times(0)).inc();
  }
}
