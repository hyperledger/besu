/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods;

import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.TransactionTrace;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Transaction;
import org.hyperledger.besu.ethereum.debug.InputDataManager;
import org.hyperledger.besu.ethereum.debug.TraceFrame;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.BlockHashLookup;
import org.hyperledger.besu.ethereum.vm.DebugOperationTracer;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class ExecuteTransactionStep implements Function<Transaction, TransactionTrace> {

  private final TraceBlock.ChainUpdater chainUpdater;
  private final Block block;
  private final DebugOperationTracer tracer;
  private final MainnetTransactionProcessor transactionProcessor;
  private final Blockchain blockchain;
  private final ProtocolSpec protocolSpec;

  public ExecuteTransactionStep(
      final TraceBlock.ChainUpdater chainUpdater,
      final Block block,
      final MainnetTransactionProcessor transactionProcessor,
      final Blockchain blockchain,
      final DebugOperationTracer tracer,
      final ProtocolSpec protocolSpec) {
    this.chainUpdater = chainUpdater;
    this.block = block;
    this.transactionProcessor = transactionProcessor;
    this.blockchain = blockchain;
    this.tracer = tracer;
    this.protocolSpec = protocolSpec;
  }

  @Override
  public TransactionTrace apply(final Transaction transaction) {
    BlockHeader header = block.getHeader();
    final Optional<BlockHeader> maybeParentHeader =
        blockchain.getBlockHeader(header.getParentHash());
    final Wei dataGasPrice =
        protocolSpec
            .getFeeMarket()
            .dataPrice(
                maybeParentHeader.flatMap(BlockHeader::getExcessDataGas).orElse(DataGas.ZERO));
    final TransactionProcessingResult result =
        transactionProcessor.processTransaction(
            blockchain,
            chainUpdater.getNextUpdater(),
            header,
            transaction,
            header.getCoinbase(),
            tracer,
            new BlockHashLookup(header, blockchain),
            false,
            dataGasPrice);

    final List<TraceFrame> traceFrames = tracer.copyTraceFrames();
    tracer.reset();
    tracer.setInputDataManager(new InputDataManager());
    return new TransactionTrace(transaction, result, traceFrames);
  }
}
