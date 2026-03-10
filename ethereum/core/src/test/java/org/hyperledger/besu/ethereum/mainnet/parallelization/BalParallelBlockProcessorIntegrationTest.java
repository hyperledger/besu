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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.ExecutionContextTestFixture;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.BalConfiguration;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.plugin.services.storage.DataStorageFormat;

import java.util.List;
import java.util.Optional;

/**
 * Integration tests for BAL (Block Access List) based parallel block processing. Extends the
 * abstract test class to verify that BAL-based parallel execution produces identical state roots to
 * sequential execution and identical BAL hashes.
 *
 * <p>All tests are inherited from AbstractParallelBlockProcessorIntegrationTest. This class only
 * provides BAL-specific implementations of hook methods.
 */
class BalParallelBlockProcessorIntegrationTest
    extends AbstractParallelBlockProcessorIntegrationTest {

  @Override
  protected String getVariantName() {
    return "BAL";
  }

  /**
   * For BAL mode:
   * 1. Sequential execution: generates the BAL + state root reference
   * 2. Parallel import: uses BAL from step 1 with BalConcurrentTransactionProcessor
   * 3. Compare state roots
   *
   * This ensures BAL tests don't depend on TransactionCollisionDetector.
   */
  @Override
  protected StateRootComparisonResult executeAndCompare(
      final String expectedRoot, final Wei baseFee, final Transaction... txs) {

    final Block block = createBlock(expectedRoot, baseFee, txs);
    final ProtocolSpec spec = protocolSchedule.getByBlockHeader(block.getHeader());
    final MainnetTransactionProcessor txProcessor = spec.getTransactionProcessor();

    // ========== Step 1: Sequential execution (generates BAL + reference state root) ==========
    final BlockProcessor seqProcessor =
        new MainnetBlockProcessor(
            txProcessor,
            spec.getTransactionReceiptFactory(),
            Wei.ZERO,
            BlockHeader::getCoinbase,
            true,
            protocolSchedule,
            SEQUENTIAL_CONFIG);

    final MutableWorldState seqWorldState = worldStateArchive.getWorldState();
    final BlockProcessingResult seqResult =
        seqProcessor.processBlock(protocolContext, blockchain, seqWorldState, block);
    assertTrue(
        seqResult.isSuccessful(),
        "Sequential execution failed — "
            + seqResult.errorMessage.orElse("(no message)")
            + " — update the hardcoded expectedRoot in the test");

    final Optional<BlockAccessList> generatedBal = getBlockAccessList(seqResult);
    assertThat(generatedBal).as("Sequential execution should produce a BAL").isPresent();

    // ========== Step 2: Parallel import using BAL from Step 1 ==========
    final ExecutionContextTestFixture parContext =
        ExecutionContextTestFixture.builder(GenesisConfig.fromResource(PARALLEL_GENESIS_RESOURCE))
            .dataStorageFormat(DataStorageFormat.BONSAI)
            .build();
    final MutableWorldState parWorldState = parContext.getStateArchive().getWorldState();

    final BlockProcessor parProcessor = createParallelBlockProcessor(txProcessor, spec);
    final ParallelTransactionPreprocessing balImportPreprocessing =
        new ParallelTransactionPreprocessingWithBal(
            txProcessor, generatedBal.get(), BalConfiguration.DEFAULT);

    final BlockProcessingResult parResult =
        parProcessor.processBlock(
            parContext.getProtocolContext(),
            parContext.getBlockchain(),
            parWorldState,
            block,
            balImportPreprocessing);
    assertTrue(parResult.isSuccessful(), "Parallel import failed");

    // ========== Step 3: Compare state roots ==========
    assertThat(parWorldState.rootHash())
        .as("Parallel import state root should match sequential reference")
        .isEqualTo(seqWorldState.rootHash());

    return new StateRootComparisonResult(
        seqWorldState.rootHash(), parWorldState.rootHash(), seqResult, parResult,
        seqWorldState, parWorldState);
  }

  @Override
  protected void assertBalHashesMatch(final StateRootComparisonResult result) {
    // Verify that parallel processing produces a BAL
    Optional<BlockAccessList> parBal = getBlockAccessList(result.parallelResult());
    assertThat(parBal).as("Parallel import should produce a BAL").isPresent();
  }

  @Override
  protected ParallelTransactionPreprocessing createParallelPreprocessing(
      final MainnetTransactionProcessor transactionProcessor) {
    return new ParallelTransactionPreprocessing(
        transactionProcessor, Runnable::run, BalConfiguration.DEFAULT);
  }

  /**
   * Custom preprocessing that injects a pre-computed BAL to force use of
   * BalConcurrentTransactionProcessor with applyWritesFromPriorTransactions.
   */
  private static class ParallelTransactionPreprocessingWithBal
      extends ParallelTransactionPreprocessing {

    private final BlockAccessList preComputedBal;

    ParallelTransactionPreprocessingWithBal(
        final MainnetTransactionProcessor transactionProcessor,
        final BlockAccessList preComputedBal,
        final BalConfiguration balConfiguration) {
      super(transactionProcessor, Runnable::run, balConfiguration);
      this.preComputedBal = preComputedBal;
    }

    @Override
    public Optional<PreprocessingContext> run(
        final ProtocolContext protocolContext,
        final BlockHeader blockHeader,
        final List<Transaction> transactions,
        final Address miningBeneficiary,
        final BlockHashLookup blockHashLookup,
        final Wei blobGasPrice,
        final Optional<BlockAccessList.BlockAccessListBuilder> blockAccessListBuilder,
        final Optional<BlockAccessList> maybeBlockBal) {
      return super.run(
          protocolContext,
          blockHeader,
          transactions,
          miningBeneficiary,
          blockHashLookup,
          blobGasPrice,
          blockAccessListBuilder,
          Optional.of(preComputedBal));
    }
  }
}
