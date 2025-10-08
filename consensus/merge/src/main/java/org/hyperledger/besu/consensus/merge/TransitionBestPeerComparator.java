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

import static org.hyperledger.besu.ethereum.eth.manager.EthPeers.CHAIN_HEIGHT;

import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.EthPeerImmutableAttributes;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/** The Transition best peer comparator. */
public class TransitionBestPeerComparator
    implements Comparator<EthPeerImmutableAttributes>, MergeStateHandler {

  private static final AtomicReference<Difficulty> terminalTotalDifficulty =
      new AtomicReference<>();

  /** The Distance from ttd. */
  static final BiFunction<EthPeerImmutableAttributes, Difficulty, BigInteger> distanceFromTTD =
      (a, ttd) ->
          a.estimatedTotalDifficulty()
              .toBigInteger()
              .subtract(ttd.getAsBigInteger())
              .abs()
              .negate();

  /** The constant EXACT_DIFFICULTY. */
  public static final Comparator<EthPeerImmutableAttributes> EXACT_DIFFICULTY =
      (a, b) -> {
        var ttd = terminalTotalDifficulty.get();
        var aDelta = distanceFromTTD.apply(a, ttd);
        var bDelta = distanceFromTTD.apply(b, ttd);
        return aDelta.compareTo(bDelta);
      };

  /** The constant BEST_MERGE_CHAIN. */
  public static final Comparator<EthPeerImmutableAttributes> BEST_MERGE_CHAIN =
      EXACT_DIFFICULTY.thenComparing(CHAIN_HEIGHT);

  /**
   * Instantiates a new Transition best peer comparator.
   *
   * @param configuredTerminalTotalDifficulty the configured terminal total difficulty
   */
  public TransitionBestPeerComparator(final Difficulty configuredTerminalTotalDifficulty) {
    terminalTotalDifficulty.set(configuredTerminalTotalDifficulty);
  }

  @Override
  public void mergeStateChanged(
      final boolean isPoS,
      final Optional<Boolean> oldState,
      final Optional<Difficulty> difficultyStoppedAt) {
    if (isPoS && difficultyStoppedAt.isPresent()) {
      terminalTotalDifficulty.set(difficultyStoppedAt.get());
    }
  }

  @Override
  public int compare(final EthPeerImmutableAttributes o1, final EthPeerImmutableAttributes o2) {
    return BEST_MERGE_CHAIN.compare(o1, o2);
  }
}
