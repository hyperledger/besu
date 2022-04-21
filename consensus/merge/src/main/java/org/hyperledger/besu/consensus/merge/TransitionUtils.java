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

import static org.hyperledger.besu.util.Slf4jLambdaHelper.warnLambda;

import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TransitionUtils<SwitchingObject> {
  private static final Logger LOG = LoggerFactory.getLogger(TransitionUtils.class);

  protected final MergeContext mergeContext;
  private final SwitchingObject preMergeObject;
  private final SwitchingObject postMergeObject;

  public TransitionUtils(
      final SwitchingObject preMergeObject, final SwitchingObject postMergeObject) {
    this(preMergeObject, postMergeObject, PostMergeContext.get());
  }

  public TransitionUtils(
      final SwitchingObject preMergeObject,
      final SwitchingObject postMergeObject,
      final MergeContext mergeContext) {
    this.preMergeObject = preMergeObject;
    this.postMergeObject = postMergeObject;
    this.mergeContext = mergeContext;
  }

  protected void dispatchConsumerAccordingToMergeState(final Consumer<SwitchingObject> consumer) {
    consumer.accept(mergeContext.isPostMerge() ? postMergeObject : preMergeObject);
  }

  protected <T> T dispatchFunctionAccordingToMergeState(
      final Function<SwitchingObject, T> function) {
    return function.apply(mergeContext.isPostMerge() ? postMergeObject : preMergeObject);
  }

  public SwitchingObject getPreMergeObject() {
    return preMergeObject;
  }

  SwitchingObject getPostMergeObject() {
    return postMergeObject;
  }

  public static boolean isTerminalProofOfWorkBlock(
      final BlockHeader header, final ProtocolContext context) {

    Difficulty headerDifficulty =
        Optional.ofNullable(header.getDifficulty()).orElse(Difficulty.ZERO);

    Difficulty currentChainTotalDifficulty =
        context
            .getBlockchain()
            .getTotalDifficultyByHash(header.getParentHash())
            // if we cannot find difficulty or are merge-at-genesis
            .orElse(Difficulty.ZERO);

    if (currentChainTotalDifficulty.isZero()) {
      warnLambda(
          LOG,
          "unable to get total difficulty for {}, parent hash {} difficulty not found",
          header::toLogString,
          header::getParentHash);
    }
    Difficulty configuredTotalTerminalDifficulty =
        context.getConsensusContext(MergeContext.class).getTerminalTotalDifficulty();

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
