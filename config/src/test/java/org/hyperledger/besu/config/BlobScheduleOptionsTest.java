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

import org.hyperledger.besu.datatypes.HardforkId.MainnetHardforkId;

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
  }

  @Test
  public void blobScheduleDefaults() {
    assertThat(BlobSchedule.CANCUN_DEFAULT.getTarget()).isEqualTo(3);
    assertThat(BlobSchedule.CANCUN_DEFAULT.getMax()).isEqualTo(6);
    assertThat(BlobSchedule.PRAGUE_DEFAULT.getTarget()).isEqualTo(6);
    assertThat(BlobSchedule.PRAGUE_DEFAULT.getMax()).isEqualTo(9);
  }

  @Test
  public void getBlobScheduleByHardforkId() {
    // Cancun and Cancun_EOF should return Cancun schedule
    assertBlobScheduleMatches(
        options.getBlobSchedule(MainnetHardforkId.CANCUN), options.getCancun());
    assertBlobScheduleMatches(
        options.getBlobSchedule(MainnetHardforkId.CANCUN_EOF), options.getCancun());

    // Prague and Osaka should return Prague schedule
    assertBlobScheduleMatches(
        options.getBlobSchedule(MainnetHardforkId.PRAGUE), options.getPrague());
    assertBlobScheduleMatches(
        options.getBlobSchedule(MainnetHardforkId.OSAKA), options.getPrague());

    // BPO forks should return their own schedules
    assertBlobScheduleMatches(options.getBlobSchedule(MainnetHardforkId.BPO1), options.getBpo1());
    assertBlobScheduleMatches(options.getBlobSchedule(MainnetHardforkId.BPO2), options.getBpo2());
    assertBlobScheduleMatches(options.getBlobSchedule(MainnetHardforkId.BPO3), options.getBpo3());
    assertBlobScheduleMatches(options.getBlobSchedule(MainnetHardforkId.BPO4), options.getBpo4());
    assertBlobScheduleMatches(options.getBlobSchedule(MainnetHardforkId.BPO5), options.getBpo5());

    // Forks without blob schedules should return empty
    assertThat(options.getBlobSchedule(MainnetHardforkId.LONDON)).isEmpty();
    assertThat(options.getBlobSchedule(MainnetHardforkId.SHANGHAI)).isEmpty();
    assertThat(options.getBlobSchedule(MainnetHardforkId.AMSTERDAM)).isEmpty();
  }

  private void assertBlobScheduleMatches(
      final Optional<BlobSchedule> actual, final Optional<BlobSchedule> expected) {
    assertThat(actual.isPresent()).isEqualTo(expected.isPresent());
    if (actual.isPresent()) {
      assertThat(actual.get().getTarget()).isEqualTo(expected.get().getTarget());
      assertThat(actual.get().getMax()).isEqualTo(expected.get().getMax());
      assertThat(actual.get().getBaseFeeUpdateFraction())
          .isEqualTo(expected.get().getBaseFeeUpdateFraction());
    }
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
