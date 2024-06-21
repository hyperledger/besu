/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.ethereum.eth.manager;

import org.hyperledger.besu.ethereum.chain.ChainHead;
import org.hyperledger.besu.ethereum.core.Difficulty;

public interface ChainHeadEstimate {

  Difficulty getEstimatedTotalDifficulty();

  long getEstimatedHeight();

  /**
   * Returns true if this chain state represents a chain that is "better" than the chain represented
   * by the supplied {@link ChainHead}. "Better" currently means that this chain is longer or
   * heavier than the supplied {@code chainToCheck}.
   *
   * @param chainToCheck The chain being compared.
   * @return true if this {@link ChainState} represents a better chain than {@code chainToCheck}.
   */
  default boolean chainIsBetterThan(final ChainHead chainToCheck) {
    return hasHigherDifficultyThan(chainToCheck) || hasLongerChainThan(chainToCheck);
  }

  default boolean hasHigherDifficultyThan(final ChainHead chainToCheck) {
    return getEstimatedTotalDifficulty().compareTo(chainToCheck.getTotalDifficulty()) > 0;
  }

  default boolean hasLongerChainThan(final ChainHead chainToCheck) {
    return getEstimatedHeight() > chainToCheck.getHeight();
  }
}
