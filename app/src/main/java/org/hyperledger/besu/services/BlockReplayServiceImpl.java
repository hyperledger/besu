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

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.AbstractBlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.BlockProcessor;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockProcessingResult;
import org.hyperledger.besu.plugin.data.BlockReplayResult;
import org.hyperledger.besu.plugin.services.BlockReplayService;
import org.hyperledger.besu.plugin.services.tracer.BlockAwareOperationTracer;

import java.util.ArrayList;
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

  /**
   * Constructs a BlockReplayServiceImpl.
   *
   * @param blockchain the blockchain to replay blocks from
   * @param protocolSchedule the protocol schedule to determine block processing rules
   * @param protocolContext the protocol context containing world state and other necessary data
   */
  public BlockReplayServiceImpl(
      final Blockchain blockchain,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext) {
    this.blockchain = blockchain;
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

    MutableWorldState worldState = getParentWorldState(blocks.getFirst());
    beforeTracing.accept(worldState);

    final List<BlockReplayResult> results = new ArrayList<>(blocks.size());

    for (final Block block : blocks) {
      worldState = getParentWorldState(block);
      final BlockProcessor processor =
          protocolSchedule.getByBlockHeader(block.getHeader()).getBlockProcessor();
      final BlockProcessingResult processingResult =
          processor.processBlock(
              protocolContext,
              blockchain,
              worldState,
              block,
              Optional.empty(),
              new AbstractBlockProcessor.PreprocessingFunction.NoPreprocessing(),
              tracer);
      if (processingResult.isSuccessful()) {
        results.add(new BlockReplayResultImpl(block, processingResult));
      } else {
        throw new RuntimeException(
            "Block processing failed for block: " + block.getHeader().toLogString());
      }
    }
    afterTracing.accept(worldState);
    return results;
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

  private MutableWorldState getParentWorldState(final Block block) {
    final BlockHeader parentHeader =
        blockchain
            .getBlockByHash(block.getHeader().getParentHash())
            .map(Block::getHeader)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Parent block not found for: " + block.getHeader().toLogString()));
    return getWorldState(parentHeader);
  }

  private MutableWorldState getWorldState(final BlockHeader header) {
    final WorldStateQueryParams params =
        WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead(header);

    return protocolContext
        .getWorldStateArchive()
        .getWorldState(params)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "World state not available for block: " + header.toLogString()));
  }

  private record BlockReplayResultImpl(Block block, BlockProcessingResult processingResult)
      implements BlockReplayResult {

    @Override
    public BlockContext blockContext() {
      return new BlockContext() {
        @Override
        public org.hyperledger.besu.plugin.data.BlockHeader getBlockHeader() {
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
