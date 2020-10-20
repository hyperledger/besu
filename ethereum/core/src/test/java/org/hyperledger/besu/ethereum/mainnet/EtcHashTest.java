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

import java.util.function.Function;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class EtcHashTest {

  @Test
  public void testEpoch() {
    Function<Long, Long> epochCalculator = EthHash.ecip1099Epoch(2_000_000);

    // check before activation block (1,000,000/30,000 = 33)
    Assertions.assertThat(epochCalculator.apply(1_000_000L)).isEqualTo(33);

    // check after activation block (3,000,000/60,000 = 50)
    Assertions.assertThat(epochCalculator.apply(3_000_000L)).isEqualTo(50);
  }
}
