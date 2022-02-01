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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigInteger;
import java.util.Optional;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProtocolScheduleTest {

  private static final Optional<BigInteger> CHAIN_ID = Optional.of(BigInteger.ONE);

  @SuppressWarnings("unchecked")
  @Test
  public void getByBlockNumber() {
    final ProtocolSpec spec1 = mock(ProtocolSpec.class);
    final ProtocolSpec spec2 = mock(ProtocolSpec.class);
    final ProtocolSpec spec3 = mock(ProtocolSpec.class);
    final ProtocolSpec spec4 = mock(ProtocolSpec.class);

    final MutableProtocolSchedule schedule = new MutableProtocolSchedule(CHAIN_ID);
    schedule.putMilestone(20, spec3);
    schedule.putMilestone(0, spec1);
    schedule.putMilestone(30, spec4);
    schedule.putMilestone(10, spec2);

    assertThat(schedule.getByBlockNumber(0)).isEqualTo(spec1);
    assertThat(schedule.getByBlockNumber(15)).isEqualTo(spec2);
    assertThat(schedule.getByBlockNumber(35)).isEqualTo(spec4);
    assertThat(schedule.getByBlockNumber(105)).isEqualTo(spec4);
  }

  @Test
  public void emptySchedule() {
    Assertions.assertThatThrownBy(() -> new MutableProtocolSchedule(CHAIN_ID).getByBlockNumber(0))
        .hasMessage("At least 1 milestone must be provided to the protocol schedule");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void conflictingSchedules() {
    final ProtocolSpec spec1 = mock(ProtocolSpec.class);
    final ProtocolSpec spec2 = mock(ProtocolSpec.class);

    final MutableProtocolSchedule protocolSchedule = new MutableProtocolSchedule(CHAIN_ID);
    protocolSchedule.putMilestone(0, spec1);
    protocolSchedule.putMilestone(0, spec2);
    assertThat(protocolSchedule.getByBlockNumber(0)).isSameAs(spec2);
  }
}
