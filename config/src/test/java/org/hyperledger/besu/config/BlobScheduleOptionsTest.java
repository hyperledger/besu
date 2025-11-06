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
package org.hyperledger.besu.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BlobScheduleOptionsTest {

  private BlobScheduleOptions options;

  @BeforeEach
  public void setupConfig() {
    final GenesisConfig genesisConfig =
        GenesisConfig.fromResource("/mainnet_with_blob_schedule.json");
    final GenesisConfigOptions configOptions = genesisConfig.getConfigOptions();
    assertThat(configOptions.getBlobScheduleOptions()).isNotEmpty();
    options = configOptions.getBlobScheduleOptions().get();
  }

  @Test
  public void blobScheduleIsParsed() {
    assertParsed(options::getCancun, 4, 7, 3338477);
    assertParsed(options::getPrague, 7, 10, 5007716);
    assertParsed(options::getBpo1, 11, 12, 5007716);
    assertParsed(options::getBpo2, 21, 22, 5007716);
    assertParsed(options::getBpo3, 31, 32, 5007716);
    assertParsed(options::getBpo4, 41, 42, 5007716);
    assertParsed(options::getBpo5, 51, 52, 5007716);
    assertParsed(options::getAmsterdam, 8, 11, 6000000);
  }

  @Test
  public void blobScheduleDefaults() {
    assertThat(BlobSchedule.CANCUN_DEFAULT.getTarget()).isEqualTo(3);
    assertThat(BlobSchedule.CANCUN_DEFAULT.getMax()).isEqualTo(6);
    assertThat(BlobSchedule.PRAGUE_DEFAULT.getTarget()).isEqualTo(6);
    assertThat(BlobSchedule.PRAGUE_DEFAULT.getMax()).isEqualTo(9);
  }

  private void assertParsed(
      final Supplier<Optional<BlobSchedule>> scheduleSupplier,
      final int expectedTarget,
      final int expectedMax,
      final int expectedBaseFeeUpdateFraction) {

    Optional<BlobSchedule> schedule = scheduleSupplier.get();
    assertThat(schedule).isNotEmpty();
    assertThat(schedule.get().getTarget()).isEqualTo(expectedTarget);
    assertThat(schedule.get().getMax()).isEqualTo(expectedMax);
    assertThat(schedule.get().getBaseFeeUpdateFraction()).isEqualTo(expectedBaseFeeUpdateFraction);
  }
}
