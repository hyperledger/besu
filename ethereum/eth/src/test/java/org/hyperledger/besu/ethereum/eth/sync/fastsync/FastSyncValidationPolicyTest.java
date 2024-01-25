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
package org.hyperledger.besu.ethereum.eth.sync.fastsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.FULL;
import static org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode.LIGHT;
import static org.hyperledger.besu.metrics.noop.NoOpMetricsSystem.NO_OP_LABELLED_1_COUNTER;

import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;

import org.junit.jupiter.api.Test;

public class FastSyncValidationPolicyTest {
  @Test
  public void shouldAlwaysUseFastValidationWhenFullValidationRateIsZero() {
    final FastSyncValidationPolicy policy =
        new FastSyncValidationPolicy(0, LIGHT, FULL, NO_OP_LABELLED_1_COUNTER);
    assertThat(policy.getValidationModeForNextBlock()).isEqualTo(LIGHT);
  }

  @Test
  public void shouldAlwaysUseFullValidationWhenFullValidationRateIsOne() {
    final FastSyncValidationPolicy policy =
        new FastSyncValidationPolicy(1, LIGHT, FULL, NO_OP_LABELLED_1_COUNTER);
    assertThat(policy.getValidationModeForNextBlock()).isEqualTo(FULL);
  }

  @Test
  public void shouldEventuallyUseBothModesWhenValidationPolicyIsHalf() {
    final FastSyncValidationPolicy policy =
        new FastSyncValidationPolicy(0.5f, LIGHT, FULL, NO_OP_LABELLED_1_COUNTER);
    boolean seenLight = false;
    boolean seenFull = false;
    // It's theoretically possible to flip a coin 2^31-1 times and only ever get heads but
    // we're probably more likely to get intermittent failures from the hard drive failing...
    for (int i = 0; i < Integer.MAX_VALUE && (!seenLight || !seenFull); i++) {
      final HeaderValidationMode mode = policy.getValidationModeForNextBlock();
      if (mode == LIGHT) {
        seenLight = true;
      } else if (mode == FULL) {
        seenFull = true;
      } else {
        fail("Unexpected validation mode " + mode);
      }
    }
    assertThat(seenLight).describedAs("used light validation").isTrue();
    assertThat(seenFull).describedAs("used full validation").isTrue();
  }
}
