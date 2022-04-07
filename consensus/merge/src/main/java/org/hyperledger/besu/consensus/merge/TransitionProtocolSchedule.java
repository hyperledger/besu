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

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.TransactionFilter;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.math.BigInteger;
import java.util.Optional;
import java.util.stream.Stream;

public class TransitionProtocolSchedule extends TransitionUtils<ProtocolSchedule>
    implements ProtocolSchedule {

  public TransitionProtocolSchedule(
      final ProtocolSchedule preMergeProtocolSchedule,
      final ProtocolSchedule postMergeProtocolSchedule) {
    super(preMergeProtocolSchedule, postMergeProtocolSchedule);
  }

  public TransitionProtocolSchedule(
      final ProtocolSchedule preMergeProtocolSchedule,
      final ProtocolSchedule postMergeProtocolSchedule,
      final MergeContext mergeContext) {
    super(preMergeProtocolSchedule, postMergeProtocolSchedule, mergeContext);
  }

  public ProtocolSchedule getPreMergeSchedule() {
    return getPreMergeObject();
  }

  public ProtocolSchedule getPostMergeSchedule() {
    return getPostMergeObject();
  }

  public ProtocolSpec getByBlockHeader(
      final ProtocolContext protocolContext, final BlockHeader blockHeader) {
    // if we do not have a finalized block we might return pre or post merge protocol schedule:
    if (mergeContext.getFinalized().isEmpty()) {

      // if head is not post-merge, return pre-merge schedule:
      if (!mergeContext.isPostMerge()) {
        return getPreMergeSchedule().getByBlockNumber(blockHeader.getNumber());
      }

      // otherwise check to see if this block represents a re-org below TTD:
      MutableBlockchain blockchain = protocolContext.getBlockchain();
      Difficulty parentDifficulty =
          blockchain.getTotalDifficultyByHash(blockHeader.getParentHash()).orElseThrow();
      Difficulty thisDifficulty = parentDifficulty.add(blockHeader.getDifficulty());
      Difficulty terminalDifficulty = mergeContext.getTerminalTotalDifficulty();

      // if this block is pre-merge
      if (thisDifficulty.lessOrEqualThan(terminalDifficulty)
          || TransitionUtils.isTerminalProofOfWorkBlock(blockHeader, protocolContext)) {
        return getPreMergeSchedule().getByBlockNumber(blockHeader.getNumber());
      }
    }
    // else return post-merge schedule
    return getPostMergeSchedule().getByBlockNumber(blockHeader.getNumber());
  }

  @Override
  public ProtocolSpec getByBlockNumber(final long number) {
    return dispatchFunctionAccordingToMergeState(
        protocolSchedule -> protocolSchedule.getByBlockNumber(number));
  }

  @Override
  public Stream<Long> streamMilestoneBlocks() {
    return dispatchFunctionAccordingToMergeState(ProtocolSchedule::streamMilestoneBlocks);
  }

  @Override
  public Optional<BigInteger> getChainId() {
    return dispatchFunctionAccordingToMergeState(ProtocolSchedule::getChainId);
  }

  @Override
  public void setTransactionFilter(final TransactionFilter transactionFilter) {
    dispatchConsumerAccordingToMergeState(
        protocolSchedule -> protocolSchedule.setTransactionFilter(transactionFilter));
  }

  @Override
  public void setPublicWorldStateArchiveForPrivacyBlockProcessor(
      final WorldStateArchive publicWorldStateArchive) {
    dispatchConsumerAccordingToMergeState(
        protocolSchedule ->
            protocolSchedule.setPublicWorldStateArchiveForPrivacyBlockProcessor(
                publicWorldStateArchive));
  }
}
