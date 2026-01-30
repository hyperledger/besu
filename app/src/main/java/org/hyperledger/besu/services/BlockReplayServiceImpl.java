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
package org.hyperledger.besu.services;

import static com.google.common.base.Preconditions.checkArgument;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.api.jsonrpc.internal.processor.Tracer;
import org.hyperledger.besu.ethereum.api.query.BlockchainQueries;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockProcessingResult;
import org.hyperledger.besu.plugin.data.BlockReplayResult;
import org.hyperledger.besu.plugin.services.BlockReplayService;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.LongStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link BlockReplayService}.
 *
 * <p>Replays and traces block execution across a contiguous range of block numbers, restoring the
 * correct pre-execution world state for each block.
 */
public class BlockReplayServiceImpl implements BlockReplayService {

  private static final Logger LOG = LoggerFactory.getLogger(BlockReplayServiceImpl.class);

  private final Blockchain blockchain;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;

  private final BlockchainQueries blockchainQueries;

  /**
   * Constructs a BlockReplayServiceImpl.
   *
   * @param blockchainQueries the blockchainQueries to replay blocks from
   * @param protocolSchedule the protocol schedule to determine block processing rules
   * @param protocolContext the protocol context containing world state and other necessary data
   */
  public BlockReplayServiceImpl(
      final BlockchainQueries blockchainQueries,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext) {
    this.blockchain = blockchainQueries.getBlockchain();
    this.blockchainQueries = blockchainQueries;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
  }

  @Override
  public List<BlockReplayResult> replayBlocks(
      final long fromBlockNumber,
      final long toBlockNumber,
      final Consumer<WorldView> beforeTracing,
      final Consumer<WorldView> afterTracing,
      final BlockAwareOperationTracer tracer) {
    validateInputs(fromBlockNumber, toBlockNumber, tracer);

    LOG.debug("Replaying blocks [{} → {}]", fromBlockNumber, toBlockNumber);

    final List<Block> blocks = getBlocks(fromBlockNumber, toBlockNumber);
    withWorldView(blocks.getFirst().getHeader().getParentHash(), beforeTracing);

    final List<BlockReplayResult> results = new ArrayList<>(blocks.size());
    for (final Block block : blocks) {
      final BlockProcessingResult result =
          replayBlock(block, tracer)
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Unexpected empty result while replaying block: "
                              + block.getHeader().toLogString()));
      results.add(new BlockReplayResultImpl(block, result));
    }
    withWorldView(blocks.getLast().getHash(), afterTracing);
    return Collections.unmodifiableList(results);
  }

  private Optional<BlockProcessingResult> replayBlock(
      final Block block, final BlockAwareOperationTracer tracer) {
    return Tracer.processTracing(
        blockchainQueries,
        block.getHeader().getHash(),
        mutableWorldState -> {
          final BlockProcessor processor =
              protocolSchedule.getByBlockHeader(block.getHeader()).getBlockProcessor();
          final BlockProcessingResult processingResult =
              processor.processBlock(
                  protocolContext,
                  blockchain,
                  mutableWorldState,
                  block,
                  Optional.empty(),
                  new AbstractBlockProcessor.PreprocessingFunction.NoPreprocessing(),
                  tracer);
          if (!processingResult.isSuccessful()) {
            throw new RuntimeException(
                "Block processing failed for block: " + block.getHeader().toLogString());
          }
          return Optional.of(processingResult);
        });
  }

  private void validateInputs(
      final long fromBlockNumber,
      final long toBlockNumber,
      final BlockAwareOperationTracer tracer) {

    checkArgument(tracer != null, "Tracer must not be null");
    checkArgument(fromBlockNumber <= toBlockNumber, "Invalid block range: from > to");
  }

  private List<Block> getBlocks(final long from, final long to) {
    return LongStream.rangeClosed(from, to)
        .mapToObj(
            number ->
                blockchain
                    .getBlockByNumber(number)
                    .orElseThrow(() -> new IllegalArgumentException("Block not found: " + number)))
        .toList();
  }

  private void withWorldView(final Hash blockHash, final Consumer<WorldView> consumer) {
    blockchainQueries.getAndMapWorldState(
        blockHash,
        ws -> {
          consumer.accept(ws);
          return Optional.empty();
        });
  }

  private record BlockReplayResultImpl(Block block, BlockProcessingResult processingResult)
      implements BlockReplayResult {

    @Override
    public BlockContext blockContext() {
      return new BlockContext() {
        @Override
        public BlockHeader getBlockHeader() {
          return block.getHeader();
        }

        @Override
        public BlockBody getBlockBody() {
          return block.getBody();
        }
      };
    }
  }
}
