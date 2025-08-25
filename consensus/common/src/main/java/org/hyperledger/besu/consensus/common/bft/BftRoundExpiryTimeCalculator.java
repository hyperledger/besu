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
package org.hyperledger.besu.consensus.common.bft;

import java.time.Duration;

/**
 * Default implementation of {@link RoundExpiryTimeCalculator} for BFT consensus algorithms. This
 * implementation calculates the expiry time for a round based on an exponential backoff strategy
 * where round expiry = baseExpiryPeriod * 2^roundNumber.
 */
public class BftRoundExpiryTimeCalculator implements RoundExpiryTimeCalculator {
  private final Duration baseExpiryPeriod;

  /**
   * Constructs a BftRoundExpiryTimeCalculator with a specified base expiry period.
   *
   * @param baseExpiryPeriod the base duration for round expiry calculations
   */
  public BftRoundExpiryTimeCalculator(final Duration baseExpiryPeriod) {
    this.baseExpiryPeriod = baseExpiryPeriod;
  }

  /**
   * Calculates the expiry time for a given round based on an exponential backoff strategy.
   *
   * @param round the round identifier for which to calculate the expiry time
   * @return the duration until the round expires
   */
  @Override
  public Duration calculateRoundExpiry(final ConsensusRoundIdentifier round) {
    return Duration.ofMillis(
        baseExpiryPeriod.toMillis() * (long) Math.pow(2, round.getRoundNumber()));
  }
}
