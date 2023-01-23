/*
 * Copyright contributors to Hyperledger Besu
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

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

public class FastImportBlocksPercentageCalculationTest {

  @Test
  public void blocksPercent_calculations() {
    assertThat(FastImportBlocksStep.getBlocksPercent(1, 1))
        .isEqualByComparingTo(BigDecimal.valueOf(100));
    assertThat(FastImportBlocksStep.getBlocksPercent(1, 100))
        .isEqualByComparingTo(BigDecimal.valueOf(1));
    assertThat(FastImportBlocksStep.getBlocksPercent(0, 100))
        .isEqualByComparingTo(BigDecimal.valueOf(0));
    assertThat(FastImportBlocksStep.getBlocksPercent(99, 0))
        .isEqualByComparingTo(BigDecimal.valueOf(0));
  }
}
