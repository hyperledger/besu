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
    final GenesisConfigFile genesisConfigFile =
        GenesisConfigFile.fromResource("/mainnet_with_blob_schedule.json");
    final GenesisConfigOptions configOptions = genesisConfigFile.getConfigOptions();

    assertThat(configOptions.getBlobScheduleOptions().getCancun().getTarget()).isEqualTo(3);
    assertThat(configOptions.getBlobScheduleOptions().getCancun().getMax()).isEqualTo(6);
    assertThat(configOptions.getBlobScheduleOptions().getPrague().getTarget()).isEqualTo(6);
    assertThat(configOptions.getBlobScheduleOptions().getPrague().getMax()).isEqualTo(9);
    assertThat(configOptions.getBlobScheduleOptions().getOsaka().getTarget()).isEqualTo(9);
    assertThat(configOptions.getBlobScheduleOptions().getOsaka().getMax()).isEqualTo(12);
  }
}
