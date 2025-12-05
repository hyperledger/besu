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

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.mainnet.block.access.list.BlockAccessList.BlockAccessListBuilder;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.evm.blockhash.BlockHashLookup;
import org.hyperledger.besu.plugin.services.metrics.Counter;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

public interface ParallelBlockTransactionProcessor {
  void runAsyncBlock(
      ProtocolContext protocolContext,
      BlockHeader blockHeader,
      List<Transaction> transactions,
      Address miningBeneficiary,
      BlockHashLookup blockHashLookup,
      Wei blobGasPrice,
      Executor executor,
      Optional<BlockAccessListBuilder> blockAccessListBuilder);

  Optional<TransactionProcessingResult> getProcessingResult(
      MutableWorldState worldState,
      Address miningBeneficiary,
      Transaction transaction,
      int location,
      Optional<Counter> confirmedParallelizedTransactionCounter,
      Optional<Counter> conflictingButCachedTransactionCounter);
}
