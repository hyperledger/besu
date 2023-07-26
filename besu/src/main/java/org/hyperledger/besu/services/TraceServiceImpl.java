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
package org.hyperledger.besu.services;

import static com.google.common.base.Preconditions.checkArgument;
import static org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessDataGasCalculator.calculateExcessDataGasForParent;

import org.hyperledger.besu.datatypes.DataGas;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.methods.TraceBlock.ChainUpdater;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.MainnetTransactionProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.processing.TransactionProcessingResult;
import org.hyperledger.besu.ethereum.vm.CachingBlockHashLookup;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.services.TraceService;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Trace service implementation. */
@Unstable
public class TraceServiceImpl implements TraceService {
  private static final Logger LOG = LoggerFactory.getLogger(TraceServiceImpl.class);

  private final BlockchainQueries blockchainQueries;
  private final ProtocolSchedule protocolSchedule;

  /**
   * Instantiates a new TraceServiceImpl service.
   *
   * @param blockchainQueries the blockchainQueries
   * @param protocolSchedule the protocolSchedule
   */
  public TraceServiceImpl(
      final BlockchainQueries blockchainQueries, final ProtocolSchedule protocolSchedule) {
    this.blockchainQueries = blockchainQueries;
    this.protocolSchedule = protocolSchedule;
  }

  /**
   * Traces block
   *
   * @param blockNumber the block number to be traced
   * @param tracer an instance of OperationTracer
   */
  @Override
  public void traceBlock(final long blockNumber, final BlockAwareOperationTracer tracer) {
    checkArgument(tracer != null);
    final Optional<Block> block = blockchainQueries.getBlockchain().getBlockByNumber(blockNumber);
    block.ifPresent(value -> trace(value, tracer));
  }

  /**
   * Traces block
   *
   * @param hash the block hash to be traced
   * @param tracer an instance of OperationTracer
   */
  @Override
  public void traceBlock(final Hash hash, final BlockAwareOperationTracer tracer) {
    checkArgument(tracer != null);
    final Optional<Block> block = blockchainQueries.getBlockchain().getBlockByHash(hash);
    block.ifPresent(value -> trace(value, tracer));
  }

  private void trace(final Block block, final BlockAwareOperationTracer tracer) {
    LOG.debug("Tracing block {}", block.toLogString());
    final List<TransactionProcessingResult> results = new ArrayList<>();
    Tracer.processTracing(
        blockchainQueries,
        block.getHash(),
        traceableState -> {
          final Blockchain blockchain = blockchainQueries.getBlockchain();
          final ChainUpdater chainUpdater = new ChainUpdater(traceableState);
          final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(block.getHeader());
          final MainnetTransactionProcessor transactionProcessor =
              protocolSpec.getTransactionProcessor();
          final BlockHeader header = block.getHeader();

          tracer.traceStartBlock(block.getHeader(), block.getBody());

          block
              .getBody()
              .getTransactions()
              .forEach(
                  transaction -> {
                    final Optional<BlockHeader> maybeParentHeader =
                        blockchain.getBlockHeader(header.getParentHash());
                    final Wei dataGasPrice =
                        protocolSpec
                            .getFeeMarket()
                            .dataPricePerGas(
                                maybeParentHeader
                                    .map(
                                        parent ->
                                            calculateExcessDataGasForParent(protocolSpec, parent))
                                    .orElse(DataGas.ZERO));

                    tracer.traceStartTransaction(transaction);

                    final TransactionProcessingResult result =
                        transactionProcessor.processTransaction(
                            blockchain,
                            chainUpdater.getNextUpdater(),
                            header,
                            transaction,
                            header.getCoinbase(),
                            tracer,
                            new CachingBlockHashLookup(header, blockchain),
                            false,
                            dataGasPrice);

                    long transactionGasUsed = transaction.getGasLimit() - result.getGasRemaining();
                    tracer.traceEndTransaction(result.getOutput(), transactionGasUsed, 0);

                    results.add(result);
                  });
          return Optional.of(results);
        });

    tracer.traceEndBlock(block.getHeader(), block.getBody());
  }
}
