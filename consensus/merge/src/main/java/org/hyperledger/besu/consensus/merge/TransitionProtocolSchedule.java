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
package org.hyperledger.besu.consensus.merge;

import org.hyperledger.besu.config.GenesisConfigOptions;
import org.hyperledger.besu.datatypes.HardforkId;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.PermissionTransactionFilter;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.ScheduledProtocolSpec;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.plugin.services.MetricsSystem;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Transition protocol schedule. */
public class TransitionProtocolSchedule implements ProtocolSchedule {
  private final TransitionUtils<ProtocolSchedule> transitionUtils;
  private static final Logger LOG = LoggerFactory.getLogger(TransitionProtocolSchedule.class);
  private final MergeContext mergeContext;
  private ProtocolContext protocolContext;

  /**
   * Instantiates a new Transition protocol schedule.
   *
   * @param preMergeProtocolSchedule the pre merge protocol schedule
   * @param postMergeProtocolSchedule the post merge protocol schedule
   * @param mergeContext the merge context
   */
  public TransitionProtocolSchedule(
      final ProtocolSchedule preMergeProtocolSchedule,
      final ProtocolSchedule postMergeProtocolSchedule,
      final MergeContext mergeContext) {
    this.mergeContext = mergeContext;
    transitionUtils =
        new TransitionUtils<>(preMergeProtocolSchedule, postMergeProtocolSchedule, mergeContext);
  }

  /**
   * Create a Proof-of-Stake protocol schedule from a config object
   *
   * @param genesisConfigOptions {@link GenesisConfigOptions} containing the config options for the
   *     milestone starting points
   * @param miningConfiguration the mining parameters
   * @param badBlockManager the cache to use to keep invalid blocks
   * @param isParallelTxProcessingEnabled indicates whether parallel transaction is enabled.
   * @return an initialised TransitionProtocolSchedule using post-merge defaults
   */
  public static TransitionProtocolSchedule fromConfig(
      final GenesisConfigOptions genesisConfigOptions,
      final MiningConfiguration miningConfiguration,
      final BadBlockManager badBlockManager,
      final boolean isParallelTxProcessingEnabled,
      final MetricsSystem metricsSystem) {
    ProtocolSchedule preMergeProtocolSchedule =
        MainnetProtocolSchedule.fromConfig(
            genesisConfigOptions,
            miningConfiguration,
            badBlockManager,
            isParallelTxProcessingEnabled,
            metricsSystem);
    ProtocolSchedule postMergeProtocolSchedule =
        MergeProtocolSchedule.create(
            genesisConfigOptions,
            false,
            miningConfiguration,
            badBlockManager,
            isParallelTxProcessingEnabled,
            metricsSystem);
    return new TransitionProtocolSchedule(
        preMergeProtocolSchedule, postMergeProtocolSchedule, PostMergeContext.get());
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
   * Gets protocol spec by block header.
   *
   * @param blockHeader the block header
   * @return the ProtocolSpec to be used by the provided block
   */
  @Override
  public ProtocolSpec getByBlockHeader(final ProcessableBlockHeader blockHeader) {
    return this.transitionUtils.dispatchFunctionAccordingToMergeState(
        protocolSchedule -> protocolSchedule.getByBlockHeader(blockHeader));
  }

  /**
   * Gets the protocol spec by block header, with some additional logic used by backwards sync (BWS)
   *
   * @param blockHeader the block header
   * @return the ProtocolSpec to be used by the provided block
   */
  public ProtocolSpec getByBlockHeaderWithTransitionReorgHandling(
      final ProcessableBlockHeader blockHeader) {
    // if we do not have a finalized block we might return pre or post merge protocol schedule:
    if (mergeContext.getFinalized().isEmpty()) {

      // if head is not post-merge, return pre-merge schedule:
      if (!mergeContext.isPostMerge()) {
        LOG.atDebug()
            .setMessage("for {} returning a pre-merge schedule because we are not post-merge")
            .addArgument(blockHeader::toLogString)
            .log();
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
      LOG.atDebug()
          .setMessage(" block {} ttd is: {}, parent total diff is: {}, this total diff is: {}")
          .addArgument(blockHeader::toLogString)
          .addArgument(terminalDifficulty)
          .addArgument(parentDifficulty)
          .addArgument(thisDifficulty)
          .log();

      // if this block is pre-merge or a TTD block
      if (thisDifficulty.lessThan(terminalDifficulty)
          || TransitionUtils.isTerminalProofOfWorkBlock(blockHeader, protocolContext)) {
        LOG.atDebug()
            .setMessage("returning a pre-merge schedule because block {} is pre-merge or TTD")
            .addArgument(blockHeader::toLogString)
            .log();
        return getPreMergeSchedule().getByBlockHeader(blockHeader);
      }
    }
    // else return post-merge schedule
    LOG.atDebug()
        .setMessage(" for {} returning a post-merge schedule")
        .addArgument(blockHeader::toLogString)
        .log();
    return getPostMergeSchedule().getByBlockHeader(blockHeader);
  }

  @Override
  public boolean anyMatch(final Predicate<ScheduledProtocolSpec> predicate) {
    return transitionUtils.dispatchFunctionAccordingToMergeState(
        schedule -> schedule.anyMatch(predicate));
  }

  @Override
  public boolean isOnMilestoneBoundary(final BlockHeader blockHeader) {
    return transitionUtils.dispatchFunctionAccordingToMergeState(
        schedule -> schedule.isOnMilestoneBoundary(blockHeader));
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
   * Put blockNumber milestone.
   *
   * @param blockNumber the block or timestamp
   * @param protocolSpec the protocol spec
   */
  @Override
  public void putBlockNumberMilestone(final long blockNumber, final ProtocolSpec protocolSpec) {
    throw new UnsupportedOperationException(
        "Should not use TransitionProtocolSchedule wrapper class to create milestones");
  }

  /**
   * Put timestamp milestone.
   *
   * @param timestamp the block or timestamp
   * @param protocolSpec the protocol spec
   */
  @Override
  public void putTimestampMilestone(final long timestamp, final ProtocolSpec protocolSpec) {
    throw new UnsupportedOperationException(
        "Should not use TransitionProtocolSchedule wrapper class to create milestones");
  }

  @Override
  public Optional<ScheduledProtocolSpec.Hardfork> hardforkFor(
      final Predicate<ScheduledProtocolSpec> predicate) {
    return this.transitionUtils.dispatchFunctionAccordingToMergeState(
        schedule -> schedule.hardforkFor(predicate));
  }

  /**
   * List milestones.
   *
   * @return the string
   */
  @Override
  public String listMilestones() {
    return transitionUtils.dispatchFunctionAccordingToMergeState(ProtocolSchedule::listMilestones);
  }

  @Override
  public Optional<Long> milestoneFor(final HardforkId hardforkId) {
    return mergeContext.isPostMerge()
        ? transitionUtils.getPostMergeObject().milestoneFor(hardforkId)
        : transitionUtils.getPreMergeObject().milestoneFor(hardforkId);
  }

  /**
   * Sets transaction filter.
   *
   * @param permissionTransactionFilter the transaction filter
   */
  @Override
  public void setPermissionTransactionFilter(
      final PermissionTransactionFilter permissionTransactionFilter) {
    transitionUtils.dispatchConsumerAccordingToMergeState(
        protocolSchedule ->
            protocolSchedule.setPermissionTransactionFilter(permissionTransactionFilter));
  }

  /**
   * Sets public world state archive for privacy block processor.
   *
   * @param publicWorldStateArchive the public world state archive
   */
  @Override
  public void setPublicWorldStateArchiveForPrivacyBlockProcessor(
      final WorldStateArchive publicWorldStateArchive) {
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
