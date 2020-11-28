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
package org.hyperledger.besu.ethereum.mainnet;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class EtcHashTest {

  @Test
  public void testDefaultEpochCalculator() {
    EpochCalculator epochCalculator = new EpochCalculator.DefaultEpochCalculator();

    // check before epoch 1
    Assertions.assertThat(epochCalculator.cacheEpoch(29_999L)).isEqualTo(0);

    // check at epoch 1
    Assertions.assertThat(epochCalculator.cacheEpoch(30_000L)).isEqualTo(1);
  }

  @Test
  public void testDefaultEpochCalculatorStartBlock() {
    EpochCalculator epochCalculator = new EpochCalculator.DefaultEpochCalculator();

    // check before epoch 0 ends
    Assertions.assertThat(epochCalculator.epochStartBlock(29_999L)).isEqualTo(1);

    // check at epoch 1 start
    Assertions.assertThat(epochCalculator.epochStartBlock(30_000L)).isEqualTo(30_001L);
  }

  @Test
  public void testEcip1099EpochCalculator() {
    EpochCalculator epochCalculator = new EpochCalculator.Ecip1099EpochCalculator();

    // check before epoch 1
    Assertions.assertThat(epochCalculator.cacheEpoch(59_999L)).isEqualTo(0);

    // check at epoch 1
    Assertions.assertThat(epochCalculator.cacheEpoch(60_000L)).isEqualTo(1);
  }

  @Test
  public void testEcip1099EpochCalculatorStartBlock() {
    EpochCalculator epochCalculator = new EpochCalculator.Ecip1099EpochCalculator();

    // check before epoch 0 ends
    Assertions.assertThat(epochCalculator.epochStartBlock(59_999L)).isEqualTo(1);

    // check at epoch 1 start
    Assertions.assertThat(epochCalculator.epochStartBlock(60_000L)).isEqualTo(60_001L);
  }
}
