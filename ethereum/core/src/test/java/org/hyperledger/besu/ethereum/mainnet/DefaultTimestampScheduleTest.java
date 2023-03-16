/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderBuilder;

import java.math.BigInteger;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class DefaultTimestampScheduleTest {

  private static final Optional<BigInteger> CHAIN_ID = Optional.of(BigInteger.ONE);

  @Test
  public void getForNextBlockHeader_shouldGetHeaderForNextTimestamp() {
    final ProtocolSpec spec1 = mock(ProtocolSpec.class);
    final ProtocolSpec spec2 = mock(ProtocolSpec.class);

    final TimestampSchedule protocolSchedule = new DefaultTimestampSchedule(CHAIN_ID);
    protocolSchedule.putMilestone(0, spec1);
    protocolSchedule.putMilestone(1000, spec2);

    final BlockHeader blockHeader =
        BlockHeaderBuilder.createDefault().number(0L).buildBlockHeader();
    final ProtocolSpec spec = protocolSchedule.getForNextBlockHeader(blockHeader, 1000);

    assertThat(spec).isEqualTo(spec2);
  }
}
