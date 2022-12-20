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
package org.hyperledger.besu.consensus.merge;

import static org.hyperledger.besu.util.Slf4jLambdaHelper.debugLambda;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.mainnet.HeaderBasedProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TimestampSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransitionProtocolSchedule implements ProtocolSchedule {
  private final TransitionUtils<ProtocolSchedule> transitionUtils;
  private static final Logger LOG = LoggerFactory.getLogger(TransitionProtocolSchedule.class);
  private final TimestampSchedule timestampSchedule;
  private final MergeContext mergeContext;
  private ProtocolContext protocolContext;

  public TransitionProtocolSchedule(
      final ProtocolSchedule preMergeProtocolSchedule,
      final ProtocolSchedule postMergeProtocolSchedule,
      final MergeContext mergeContext,
      final TimestampSchedule timestampSchedule) {
    this.timestampSchedule = timestampSchedule;
    this.mergeContext = mergeContext;
    transitionUtils =
        new TransitionUtils<>(preMergeProtocolSchedule, postMergeProtocolSchedule, mergeContext);
  }

  public ProtocolSchedule getPreMergeSchedule() {
    return transitionUtils.getPreMergeObject();
  }

  public ProtocolSchedule getPostMergeSchedule() {
    return transitionUtils.getPostMergeObject();
  }

  @Override
  public ProtocolSpec getByBlockHeader(final ProcessableBlockHeader blockHeader) {
    return this.timestampSchedule
        .getByTimestamp(blockHeader.getTimestamp())
        .orElseGet(() -> getByBlockHeaderFromTransitionUtils(blockHeader));
  }

  private ProtocolSpec getByBlockHeaderFromTransitionUtils(
      final ProcessableBlockHeader blockHeader) {
    // if we do not have a finalized block we might return pre or post merge protocol schedule:
    if (mergeContext.getFinalized().isEmpty()) {

      // if head is not post-merge, return pre-merge schedule:
      if (!mergeContext.isPostMerge()) {
        debugLambda(
            LOG,
            "for {} returning a pre-merge schedule because we are not post-merge",
            blockHeader::toLogString);
        return getPreMergeSchedule().getByBlockNumber(blockHeader.getNumber());
      }

      // otherwise check to see if this block represents a re-org TTD block:
      Difficulty parentDifficulty =
          protocolContext
              .getBlockchain()
              .getTotalDifficultyByHash(blockHeader.getParentHash())
              .orElseThrow();
      Difficulty thisDifficulty = parentDifficulty.add(blockHeader.getDifficulty());
      Difficulty terminalDifficulty = mergeContext.getTerminalTotalDifficulty();
      debugLambda(
          LOG,
          " block {} ttd is: {}, parent total diff is: {}, this total diff is: {}",
          blockHeader::toLogString,
          () -> terminalDifficulty,
          () -> parentDifficulty,
          () -> thisDifficulty);

      // if this block is pre-merge or a TTD block
      if (thisDifficulty.lessThan(terminalDifficulty)
          || TransitionUtils.isTerminalProofOfWorkBlock(blockHeader, protocolContext)) {
        debugLambda(
            LOG,
            "returning a pre-merge schedule because block {} is pre-merge or TTD",
            blockHeader::toLogString);
        return getPreMergeSchedule().getByBlockNumber(blockHeader.getNumber());
      }
    }
    // else return post-merge schedule
    debugLambda(LOG, " for {} returning a post-merge schedule", blockHeader::toLogString);
    return getPostMergeSchedule().getByBlockNumber(blockHeader.getNumber());
  }

  @Override
  public ProtocolSpec getByBlockNumber(final long number) {

    return Optional.ofNullable(protocolContext)
        .map(ProtocolContext::getBlockchain)
        .flatMap(blockchain -> blockchain.getBlockByNumber(number))
        .map(Block::getHeader)
        .map(timestampSchedule::getByBlockHeader)
        .orElseGet(
            () ->
                transitionUtils.dispatchFunctionAccordingToMergeState(
                    protocolSchedule -> protocolSchedule.getByBlockNumber(number)));
  }

  @Override
  public Stream<Long> streamMilestoneBlocks() {
    return transitionUtils.dispatchFunctionAccordingToMergeState(
        ProtocolSchedule::streamMilestoneBlocks);
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return transitionUtils.dispatchFunctionAccordingToMergeState(ProtocolSchedule::getChainId);
  }

  @Override
  public void putMilestone(final long blockOrTimestamp, final ProtocolSpec protocolSpec) {
    throw new UnsupportedOperationException(
        "Should not use TransitionProtocolSchedule wrapper class to create milestones");
  }

  @Override
  public String listMilestones() {
    String blockNumberMilestones =
        transitionUtils.dispatchFunctionAccordingToMergeState(
            HeaderBasedProtocolSchedule::listMilestones);
    return blockNumberMilestones + ";" + timestampSchedule.listMilestones();
  }

  @Override
  public void setTransactionFilter(final TransactionFilter transactionFilter) {
    timestampSchedule.setTransactionFilter(transactionFilter);
    transitionUtils.dispatchConsumerAccordingToMergeState(
        protocolSchedule -> protocolSchedule.setTransactionFilter(transactionFilter));
  }

  @Override
  public void setPublicWorldStateArchiveForPrivacyBlockProcessor(
      final WorldStateArchive publicWorldStateArchive) {
    timestampSchedule.setPublicWorldStateArchiveForPrivacyBlockProcessor(publicWorldStateArchive);
    transitionUtils.dispatchConsumerAccordingToMergeState(
        protocolSchedule ->
            protocolSchedule.setPublicWorldStateArchiveForPrivacyBlockProcessor(
                publicWorldStateArchive));
  }

  public void setProtocolContext(final ProtocolContext protocolContext) {
    this.protocolContext = protocolContext;
  }
}
