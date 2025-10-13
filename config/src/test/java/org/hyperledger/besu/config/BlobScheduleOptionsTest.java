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

import org.junit.jupiter.api.Test;

public class BlobScheduleOptionsTest {

  @Test
  public void blobScheduleIsParsed() {
    final GenesisConfig genesisConfigFile =
        GenesisConfig.fromResource("/mainnet_with_blob_schedule.json");
    final GenesisConfigOptions configOptions = genesisConfigFile.getConfigOptions();

    assertThat(configOptions.getBlobScheduleOptions()).isNotEmpty();
    final BlobScheduleOptions blobScheduleOptions = configOptions.getBlobScheduleOptions().get();
    assertThat(blobScheduleOptions.getCancun()).isNotEmpty();
    assertThat(blobScheduleOptions.getCancun().get().getTarget()).isEqualTo(4);
    assertThat(blobScheduleOptions.getCancun().get().getMax()).isEqualTo(7);
    assertThat(blobScheduleOptions.getCancun().get().getBaseFeeUpdateFraction()).isEqualTo(3338477);
    assertThat(blobScheduleOptions.getPrague()).isNotEmpty();
    assertThat(blobScheduleOptions.getPrague().get().getTarget()).isEqualTo(7);
    assertThat(blobScheduleOptions.getPrague().get().getMax()).isEqualTo(10);
    assertThat(blobScheduleOptions.getPrague().get().getBaseFeeUpdateFraction()).isEqualTo(5007716);
    assertThat(blobScheduleOptions.getOsaka()).isNotEmpty();
    assertThat(blobScheduleOptions.getOsaka().get().getTarget()).isEqualTo(10);
    assertThat(blobScheduleOptions.getOsaka().get().getMax()).isEqualTo(13);
    assertThat(blobScheduleOptions.getOsaka().get().getBaseFeeUpdateFraction()).isEqualTo(5007716);
  }
}
