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
import static org.hyperledger.besu.ethereum.mainnet.feemarket.ExcessBlobGasCalculator.calculateExcessBlobGasForParent;

import org.hyperledger.besu.datatypes.BlobGas;
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
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.Unstable;
import org.hyperledger.besu.plugin.services.TraceService;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.LongStream;

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

  /**
   * Traces range of blocks
   *
   * @param fromBlockNumber the beginning of the range (inclusive)
   * @param toBlockNumber the end of the range (inclusive)
   * @param beforeTracing Function which performs an operation on a MutableWorldState before tracing
   * @param afterTracing Function which performs an operation on a MutableWorldState after tracing
   * @param tracer an instance of OperationTracer
   */
  @Override
  public void trace(
      final long fromBlockNumber,
      final long toBlockNumber,
      final Consumer<WorldUpdater> beforeTracing,
      final Consumer<WorldUpdater> afterTracing,
      final BlockAwareOperationTracer tracer) {
    checkArgument(tracer != null);
    LOG.debug("Tracing from block {} to block {}", fromBlockNumber, toBlockNumber);
    final Blockchain blockchain = blockchainQueries.getBlockchain();
    final List<Block> blocks =
        LongStream.rangeClosed(fromBlockNumber, toBlockNumber)
            .mapToObj(
                number ->
                    blockchain
                        .getBlockByNumber(number)
                        .orElseThrow(() -> new RuntimeException("Block not found " + number)))
            .toList();
    Tracer.processTracing(
        blockchainQueries,
        blocks.get(0).getHash(),
        traceableState -> {
          final WorldUpdater worldStateUpdater = traceableState.updater();
          final ChainUpdater chainUpdater = new ChainUpdater(traceableState, worldStateUpdater);
          beforeTracing.accept(worldStateUpdater);
          final List<TransactionProcessingResult> results = new ArrayList<>();
          blocks.forEach(
              block -> {
                results.addAll(trace(blockchain, block, chainUpdater, tracer));
                tracer.traceEndBlock(block.getHeader(), block.getBody());
              });
          afterTracing.accept(chainUpdater.getNextUpdater());
          return Optional.of(results);
        });
  }

  private void trace(final Block block, final BlockAwareOperationTracer tracer) {
    LOG.debug("Tracing block {}", block.toLogString());
    final Blockchain blockchain = blockchainQueries.getBlockchain();
    Tracer.processTracing(
        blockchainQueries,
        block.getHash(),
        traceableState ->
            Optional.of(trace(blockchain, block, new ChainUpdater(traceableState), tracer)));
    tracer.traceEndBlock(block.getHeader(), block.getBody());
  }

  private List<TransactionProcessingResult> trace(
      final Blockchain blockchain,
      final Block block,
      final ChainUpdater chainUpdater,
      final BlockAwareOperationTracer tracer) {
    final List<TransactionProcessingResult> results = new ArrayList<>();
    final ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(block.getHeader());
    final MainnetTransactionProcessor transactionProcessor = protocolSpec.getTransactionProcessor();
    final BlockHeader header = block.getHeader();
    tracer.traceStartBlock(block.getHeader(), block.getBody());

    block
        .getBody()
        .getTransactions()
        .forEach(
            transaction -> {
              final Optional<BlockHeader> maybeParentHeader =
                  blockchain.getBlockHeader(header.getParentHash());
              final Wei blobGasPrice =
                  protocolSpec
                      .getFeeMarket()
                      .blobGasPricePerGas(
                          maybeParentHeader
                              .map(parent -> calculateExcessBlobGasForParent(protocolSpec, parent))
                              .orElse(BlobGas.ZERO));

              final WorldUpdater worldUpdater = chainUpdater.getNextUpdater();
              tracer.traceStartTransaction(worldUpdater, transaction);
              final TransactionProcessingResult result =
                  transactionProcessor.processTransaction(
                      blockchain,
                      worldUpdater,
                      header,
                      transaction,
                      header.getCoinbase(),
                      tracer,
                      new CachingBlockHashLookup(header, blockchain),
                      false,
                      blobGasPrice);

              long transactionGasUsed = transaction.getGasLimit() - result.getGasRemaining();
              tracer.traceEndTransaction(
                  worldUpdater, transaction, result.getOutput(), transactionGasUsed, 0);

              results.add(result);
            });

    tracer.traceEndBlock(block.getHeader(), block.getBody());

    return results;
  }
}
