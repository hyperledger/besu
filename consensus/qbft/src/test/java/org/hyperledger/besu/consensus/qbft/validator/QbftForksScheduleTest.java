/*
 *  Copyright ConsenSys AG.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations under the License.
 *
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.besu.consensus.qbft.validator;

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.config.QbftFork;
import org.hyperledger.besu.config.QbftFork.VALIDATOR_SELECTION_MODE;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

public class QbftForksScheduleTest {

  @Test
  public void retrievesGenesisFork() {
    final QbftFork genesisFork =
        new QbftFork(
            0,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(VALIDATOR_SELECTION_MODE.BLOCKHEADER),
            Optional.empty());

    final QbftForksSchedule schedule = new QbftForksSchedule(genesisFork, Collections.emptyList());
    assertThat(schedule.getForkWithValidatorSelectionMode(0)).contains(genesisFork);
    assertThat(schedule.getForkWithValidatorSelectionMode(1)).contains(genesisFork);
  }

  @Test
  public void retrievesLatestForkWithValidatorSelectionMode() {
    final QbftFork genesisFork =
        new QbftFork(
            0,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(VALIDATOR_SELECTION_MODE.BLOCKHEADER),
            Optional.empty());
    final QbftFork contractFork =
        new QbftFork(
            1,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(VALIDATOR_SELECTION_MODE.CONTRACT),
            Optional.of("0x1"));
    final QbftFork blockPeriodFork =
        new QbftFork(
            2,
            Optional.of(1),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of("0x1"));

    final QbftForksSchedule schedule =
        new QbftForksSchedule(genesisFork, List.of(blockPeriodFork, contractFork));

    assertThat(schedule.getForkWithValidatorSelectionMode(0)).contains(genesisFork);
    assertThat(schedule.getForkWithValidatorSelectionMode(1)).contains(contractFork);
    assertThat(schedule.getForkWithValidatorSelectionMode(2)).contains(contractFork);
    assertThat(schedule.getForkWithValidatorSelectionMode(3)).contains(contractFork);
  }
}
