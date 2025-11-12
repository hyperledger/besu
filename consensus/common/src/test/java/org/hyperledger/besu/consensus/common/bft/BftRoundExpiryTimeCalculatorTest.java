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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class BftRoundExpiryTimeCalculatorTest {

  @Test
  public void calculatesRoundExpiry() {
    final Duration baseExpiry = Duration.ofSeconds(2);
    final BftRoundExpiryTimeCalculator calculator = new BftRoundExpiryTimeCalculator(baseExpiry);

    // Round 0: 2000 * 2^0 = 2000ms
    assertThat(calculator.calculateRoundExpiry(new ConsensusRoundIdentifier(1, 0))).hasMillis(2000);

    // Round 1: 2000 * 2^1 = 4000ms
    assertThat(calculator.calculateRoundExpiry(new ConsensusRoundIdentifier(1, 1))).hasMillis(4000);

    // Round 2: 2000 * 2^2 = 8000ms
    assertThat(calculator.calculateRoundExpiry(new ConsensusRoundIdentifier(1, 2))).hasMillis(8000);

    // Round 3: 2000 * 2^3 = 16000ms
    assertThat(calculator.calculateRoundExpiry(new ConsensusRoundIdentifier(1, 3)))
        .hasMillis(16000);

    // Round 5: 2000 * 2^5 = 64000ms
    assertThat(calculator.calculateRoundExpiry(new ConsensusRoundIdentifier(1, 5)))
        .hasMillis(64000);
  }
}
