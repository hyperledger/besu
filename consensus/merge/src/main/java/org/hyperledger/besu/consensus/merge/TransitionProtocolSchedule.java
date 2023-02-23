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

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.mainnet.HeaderBasedProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.TimestampSchedule;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Transition protocol schedule. */
public class TransitionProtocolSchedule implements ProtocolSchedule {
  private final TransitionUtils<ProtocolSchedule> transitionUtils;
  private static final Logger LOG = LoggerFactory.getLogger(TransitionProtocolSchedule.class);
  private final TimestampSchedule timestampSchedule;
  private final MergeContext mergeContext;
  private ProtocolContext protocolContext;

  /**
   * Instantiates a new Transition protocol schedule.
   *
   * @param preMergeProtocolSchedule the pre merge protocol schedule
   * @param postMergeProtocolSchedule the post merge protocol schedule
   * @param mergeContext the merge context
   * @param timestampSchedule the timestamp schedule
   */
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

  /**
   * Create a Proof-of-Stake protocol schedule from a config object
   *
   * @param genesisConfigOptions {@link GenesisConfigOptions} containing the config options for the
   *     milestone starting points
   * @return an initialised TransitionProtocolSchedule using post-merge defaults
   */
  public static TransitionProtocolSchedule fromConfig(
      final GenesisConfigOptions genesisConfigOptions) {
    ProtocolSchedule preMergeProtocolSchedule =
        MainnetProtocolSchedule.fromConfig(genesisConfigOptions);
    ProtocolSchedule postMergeProtocolSchedule =
        MergeProtocolSchedule.create(genesisConfigOptions, false);
    TimestampSchedule timestampSchedule =
        MergeProtocolSchedule.createTimestamp(
            genesisConfigOptions, PrivacyParameters.DEFAULT, false);
    return new TransitionProtocolSchedule(
        preMergeProtocolSchedule,
        postMergeProtocolSchedule,
        PostMergeContext.get(),
        timestampSchedule);
  }

  /**
   * Gets pre merge schedule.
   *
   * @return the pre merge schedule
   */
  public ProtocolSchedule getPreMergeSchedule() {
    return transitionUtils.getPreMergeObject();
  }

  /**
   * Gets post merge schedule.
   *
   * @return the post merge schedule
   */
  public ProtocolSchedule getPostMergeSchedule() {
    return transitionUtils.getPostMergeObject();
  }

  /**
   * Gets by block header.
   *
   * @param blockHeader the block header
   * @return the by block header
   */
  @Override
  public ProtocolSpec getByBlockHeader(final ProcessableBlockHeader blockHeader) {
    return this.timestampSchedule
        .getByTimestamp(blockHeader.getTimestamp())
        .orElseGet(
            () ->
                transitionUtils.dispatchFunctionAccordingToMergeState(
                    protocolSchedule -> protocolSchedule.getByBlockHeader(blockHeader)));
  }

  public ProtocolSpec getByBlockHeaderForBws(final ProcessableBlockHeader blockHeader) {
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
        return getPreMergeSchedule().getByBlockHeader(blockHeader);
      }

      // otherwise check to see if this block represents a re-org TTD block:
      Difficulty parentDifficulty =
          protocolContext
              .getBlockchain()
              .getTotalDifficultyByHash(blockHeader.getParentHash())
              .orElse(Difficulty.ZERO);
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
        return getPreMergeSchedule().getByBlockHeader(blockHeader);
      }
    }
    // else return post-merge schedule
    debugLambda(LOG, " for {} returning a post-merge schedule", blockHeader::toLogString);
    return getPostMergeSchedule().getByBlockHeader(blockHeader);
  }

  /**
   * Gets by block number.
   *
   * @param number the number
   * @return the by block number
   */
  @Override
  public ProtocolSpec getByBlockNumber(final long number) {

    return Optional.ofNullable(protocolContext)
        .map(ProtocolContext::getBlockchain)
        .flatMap(blockchain -> blockchain.getBlockHeader(number))
        .map(timestampSchedule::getByBlockHeader)
        .orElseGet(
            () ->
                transitionUtils.dispatchFunctionAccordingToMergeState(
                    protocolSchedule -> protocolSchedule.getByBlockNumber(number)));
  }

  /**
   * Stream milestone blocks stream.
   *
   * @return the stream
   */
  @Override
  public Stream<Long> streamMilestoneBlocks() {
    Stream<Long> milestoneBlockNumbers =
        transitionUtils.dispatchFunctionAccordingToMergeState(
            ProtocolSchedule::streamMilestoneBlocks);
    return Stream.concat(milestoneBlockNumbers, timestampSchedule.streamMilestoneBlocks());
  }

  /**
   * Gets chain id.
   *
   * @return the chain id
   */
  @Override
  public Optional<BigInteger> getChainId() {
    return transitionUtils.dispatchFunctionAccordingToMergeState(ProtocolSchedule::getChainId);
  }

  /**
   * Put milestone.
   *
   * @param blockOrTimestamp the block or timestamp
   * @param protocolSpec the protocol spec
   */
  @Override
  public void putMilestone(final long blockOrTimestamp, final ProtocolSpec protocolSpec) {
    throw new UnsupportedOperationException(
        "Should not use TransitionProtocolSchedule wrapper class to create milestones");
  }

  /**
   * List milestones.
   *
   * @return the string
   */
  @Override
  public String listMilestones() {
    String blockNumberMilestones =
        transitionUtils.dispatchFunctionAccordingToMergeState(
            HeaderBasedProtocolSchedule::listMilestones);
    return blockNumberMilestones + ";" + timestampSchedule.listMilestones();
  }

  /**
   * Sets transaction filter.
   *
   * @param transactionFilter the transaction filter
   */
  @Override
  public void setTransactionFilter(final TransactionFilter transactionFilter) {
    timestampSchedule.setTransactionFilter(transactionFilter);
    transitionUtils.dispatchConsumerAccordingToMergeState(
        protocolSchedule -> protocolSchedule.setTransactionFilter(transactionFilter));
  }

  /**
   * Sets public world state archive for privacy block processor.
   *
   * @param publicWorldStateArchive the public world state archive
   */
  @Override
  public void setPublicWorldStateArchiveForPrivacyBlockProcessor(
      final WorldStateArchive publicWorldStateArchive) {
    timestampSchedule.setPublicWorldStateArchiveForPrivacyBlockProcessor(publicWorldStateArchive);
    transitionUtils.dispatchConsumerAccordingToMergeState(
        protocolSchedule ->
            protocolSchedule.setPublicWorldStateArchiveForPrivacyBlockProcessor(
                publicWorldStateArchive));
  }

  /**
   * Sets protocol context.
   *
   * @param protocolContext the protocol context
   */
  public void setProtocolContext(final ProtocolContext protocolContext) {
    this.protocolContext = protocolContext;
  }
}
