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
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.BlockProcessingResult;
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
 * <p>Supports replaying and tracing block execution across a range of block numbers.
 */
public class BlockReplayServiceImpl implements BlockReplayService {

  private static final Logger LOG = LoggerFactory.getLogger(BlockReplayServiceImpl.class);

  private final Blockchain blockchain;
  private final ProtocolSchedule protocolSchedule;
  private final ProtocolContext protocolContext;

  public BlockReplayServiceImpl(
      final Blockchain blockchain,
      final ProtocolSchedule protocolSchedule,
      final ProtocolContext protocolContext) {
    this.blockchain = blockchain;
    this.protocolSchedule = protocolSchedule;
    this.protocolContext = protocolContext;
  }

  @Override
  public List<BlockProcessingResult> replayBlocks(
      final long fromBlockNumber,
      final long toBlockNumber,
      final Consumer<WorldUpdater> beforeTracing,
      final Consumer<WorldUpdater> afterTracing,
      final BlockAwareOperationTracer tracer) {
    checkArgument(tracer != null, "Tracer must not be null");
    checkArgument(fromBlockNumber <= toBlockNumber, "Invalid block range: from > to");
    LOG.debug("Replaying from block {} to block {}", fromBlockNumber, toBlockNumber);
    final List<Block> blocks =
        LongStream.rangeClosed(fromBlockNumber, toBlockNumber)
            .mapToObj(
                number ->
                    blockchain
                        .getBlockByNumber(number)
                        .orElseThrow(
                            () -> new IllegalArgumentException("Block not found: " + number)))
            .toList();

    if (blocks.isEmpty()) {
      LOG.info("No blocks to replay in range {}–{}", fromBlockNumber, toBlockNumber);
      return List.of();
    }

    final MutableWorldState worldState = getParentWorldState(blockchain, blocks.getFirst());
    final WorldUpdater updater = worldState.updater();

    // Apply any pre-tracing operations
    beforeTracing.accept(updater);

    // Replay the blocks with tracing
    final List<BlockProcessingResult> results = replayBlocks(blocks, worldState, tracer);

    // Apply any post-tracing operations
    afterTracing.accept(updater);

    return results;
  }

  private MutableWorldState getParentWorldState(final Blockchain blockchain, final Block block) {
    final BlockHeader parentHeader =
        blockchain
            .getBlockByHash(block.getHeader().getParentHash())
            .map(Block::getHeader)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Parent block not found for: " + block.getHeader().toLogString()));
    final WorldStateQueryParams params =
        WorldStateQueryParams.withBlockHeaderAndNoUpdateNodeHead(parentHeader);
    return protocolContext
        .getWorldStateArchive()
        .getWorldState(params)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "World state not available for block: " + parentHeader.toLogString()));
  }

  private List<BlockProcessingResult> replayBlocks(
      final List<Block> blocks,
      final MutableWorldState worldState,
      final BlockAwareOperationTracer tracer) {
    final List<BlockProcessingResult> results = new ArrayList<>(blocks.size());
    for (final Block block : blocks) {
      final BlockProcessor processor =
          protocolSchedule.getByBlockHeader(block.getHeader()).getBlockProcessor();
      final BlockProcessingResult result =
          processor.processBlock(
              protocolContext,
              blockchain,
              worldState,
              block,
              Optional.empty(),
              new AbstractBlockProcessor.PreprocessingFunction.NoPreprocessing(),
              tracer);
      results.add(result);
    }
    return results;
  }
}
