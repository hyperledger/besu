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

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.ProcessableBlockHeader;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Transition utils.
 *
 * @param <SwitchingObject> the type parameter
 */
public class TransitionUtils<SwitchingObject> {
  private static final Logger LOG = LoggerFactory.getLogger(TransitionUtils.class);

  /** The Merge context. */
  protected final MergeContext mergeContext;

  private final SwitchingObject preMergeObject;
  private final SwitchingObject postMergeObject;

  /**
   * Instantiates a new Transition utils.
   *
   * @param preMergeObject the pre merge object
   * @param postMergeObject the post merge object
   * @param mergeContext the merge context
   */
  public TransitionUtils(
      final SwitchingObject preMergeObject,
      final SwitchingObject postMergeObject,
      final MergeContext mergeContext) {
    this.preMergeObject = preMergeObject;
    this.postMergeObject = postMergeObject;
    this.mergeContext = mergeContext;
  }

  /**
   * Dispatch consumer according to merge state.
   *
   * @param consumer the consumer
   */
  void dispatchConsumerAccordingToMergeState(final Consumer<SwitchingObject> consumer) {
    consumer.accept(mergeContext.isPostMerge() ? postMergeObject : preMergeObject);
  }

  /**
   * Dispatch function according to merge state t.
   *
   * @param <T> the type parameter
   * @param function the function
   * @return the t
   */
  public <T> T dispatchFunctionAccordingToMergeState(final Function<SwitchingObject, T> function) {
    return function.apply(mergeContext.isPostMerge() ? postMergeObject : preMergeObject);
  }

  /**
   * Gets pre merge object.
   *
   * @return the pre merge object
   */
  public SwitchingObject getPreMergeObject() {
    return preMergeObject;
  }

  /**
   * Gets post merge object.
   *
   * @return the post merge object
   */
  SwitchingObject getPostMergeObject() {
    return postMergeObject;
  }

  /**
   * Is terminal proof of work block boolean.
   *
   * @param header the header
   * @param context the context
   * @return the boolean
   */
  public static boolean isTerminalProofOfWorkBlock(
      final ProcessableBlockHeader header, final ProtocolContext context) {

    Difficulty headerDifficulty =
        Optional.ofNullable(header.getDifficulty()).orElse(Difficulty.ZERO);

    Difficulty currentChainTotalDifficulty =
        context
            .getBlockchain()
            .getTotalDifficultyByHash(header.getParentHash())
            // if we cannot find difficulty or are merge-at-genesis
            .orElse(Difficulty.ZERO);

    final MergeContext consensusContext = context.getConsensusContext(MergeContext.class);

    // Genesis is configured for post-merge we will never have a terminal pow block
    if (consensusContext.isPostMergeAtGenesis()) {
      return false;
    }

    if (currentChainTotalDifficulty.isZero()) {
      LOG.atWarn()
          .setMessage("unable to get total difficulty for {}, parent hash {} difficulty not found")
          .addArgument(header::toLogString)
          .addArgument(header::getParentHash)
          .log();
    }
    Difficulty configuredTotalTerminalDifficulty = consensusContext.getTerminalTotalDifficulty();

    if (currentChainTotalDifficulty
            .add(headerDifficulty)
            .greaterOrEqualThan(
                configuredTotalTerminalDifficulty) // adding would equal or go over limit
        && currentChainTotalDifficulty.lessThan(
            configuredTotalTerminalDifficulty) // parent was under
    ) {
      return true;
    }

    // return true for genesis block when merge-at-genesis, otherwise false
    return header.getNumber() == 0L
        && header.getDifficulty().greaterOrEqualThan(configuredTotalTerminalDifficulty);
  }
}
