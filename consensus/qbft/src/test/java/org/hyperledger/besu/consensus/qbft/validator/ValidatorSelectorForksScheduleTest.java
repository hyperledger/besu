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
package org.hyperledger.besu.consensus.qbft.validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class ValidatorSelectorForksScheduleTest {

  @Test
  public void retrievesGenesisFork() {
    final ValidatorSelectorConfig genesisFork = ValidatorSelectorConfig.createBlockConfig(0);

    final ValidatorSelectorForksSchedule schedule =
        new ValidatorSelectorForksSchedule(genesisFork, Collections.emptyList());
    assertThat(schedule.getFork(0)).contains(genesisFork);
    assertThat(schedule.getFork(1)).contains(genesisFork);
  }

  @Test
  public void retrievesLatestFork() {
    final ValidatorSelectorConfig genesisFork = ValidatorSelectorConfig.createBlockConfig(0);
    final ValidatorSelectorConfig contractFork =
        ValidatorSelectorConfig.createContractConfig(1, "0x1");

    final ValidatorSelectorForksSchedule schedule =
        new ValidatorSelectorForksSchedule(genesisFork, List.of(contractFork));

    assertThat(schedule.getFork(0)).contains(genesisFork);
    assertThat(schedule.getFork(1)).contains(contractFork);
    assertThat(schedule.getFork(2)).contains(contractFork);
    assertThat(schedule.getFork(3)).contains(contractFork);
  }
}
