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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor.PreprocessingContext;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor.PreprocessingFunction;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.parallelization.MainnetParallelBlockProcessor.ParallelizedPreProcessingContext;
import org.hyperledger.besu.ethereum.privacy.storage.PrivateMetadataUpdater;
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.PathBasedWorldStateProvider;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

public class ParallelTransactionPreprocessing implements PreprocessingFunction {

  private final MainnetTransactionProcessor transactionProcessor;
  private final Executor executor;

  public ParallelTransactionPreprocessing(
      final MainnetTransactionProcessor transactionProcessor, final Executor executor) {
    this.transactionProcessor = transactionProcessor;
    this.executor = executor;
  }

  @Override
  public Optional<PreprocessingContext> run(
      final ProtocolContext protocolContext,
      final PrivateMetadataUpdater privateMetadataUpdater,
      final BlockHeader blockHeader,
      final List<Transaction> transactions,
      final Address miningBeneficiary,
      final BlockHashLookup blockHashLookup,
      final Wei blobGasPrice) {
    if ((protocolContext.getWorldStateArchive() instanceof PathBasedWorldStateProvider)) {
      ParallelizedConcurrentTransactionProcessor parallelizedConcurrentTransactionProcessor =
          new ParallelizedConcurrentTransactionProcessor(transactionProcessor);
      // runAsyncBlock, if activated, facilitates the non-blocking parallel execution
      // of transactions in the background through an optimistic strategy.
      parallelizedConcurrentTransactionProcessor.runAsyncBlock(
          protocolContext,
          blockHeader,
          transactions,
          miningBeneficiary,
          blockHashLookup,
          blobGasPrice,
          privateMetadataUpdater,
          executor);
      return Optional.of(
          new ParallelizedPreProcessingContext(parallelizedConcurrentTransactionProcessor));
    }
    return Optional.empty();
  }
}
