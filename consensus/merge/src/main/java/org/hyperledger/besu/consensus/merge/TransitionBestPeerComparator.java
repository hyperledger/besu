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

import static org.hyperledger.besu.ethereum.eth.manager.EthPeers.CHAIN_HEIGHT;

import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.eth.manager.EthPeer;

import java.math.BigInteger;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

public class TransitionBestPeerComparator implements Comparator<EthPeer>, MergeStateHandler {

  private static final AtomicReference<Difficulty> terminalTotalDifficulty =
      new AtomicReference<>();

  static final BiFunction<EthPeer, Difficulty, BigInteger> distanceFromTTD =
      (a, ttd) ->
          a.chainState()
              .getEstimatedTotalDifficulty()
              .getAsBigInteger()
              .subtract(ttd.getAsBigInteger())
              .abs()
              .negate();

  public static final Comparator<EthPeer> EXACT_DIFFICULTY =
      (a, b) -> {
        var ttd = terminalTotalDifficulty.get();
        var aDelta = distanceFromTTD.apply(a, ttd);
        var bDelta = distanceFromTTD.apply(b, ttd);
        return aDelta.compareTo(bDelta);
      };

  public static final Comparator<EthPeer> BEST_MERGE_CHAIN =
      EXACT_DIFFICULTY.thenComparing(CHAIN_HEIGHT);

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
  public int compare(final EthPeer o1, final EthPeer o2) {
    return BEST_MERGE_CHAIN.compare(o1, o2);
  }
}
