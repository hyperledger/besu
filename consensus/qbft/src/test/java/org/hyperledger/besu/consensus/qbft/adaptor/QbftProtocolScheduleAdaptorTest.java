/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.adaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.consensus.qbft.core.types.QbftProtocolSchedule;
import org.hyperledger.besu.consensus.qbft.core.types.QbftProtocolSpec;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QbftProtocolScheduleAdaptorTest {
  @Mock private ProtocolSchedule besuProtocolSchedule;
  @Mock private ProtocolSpec besuProtocolSpec;
  @Mock private ProtocolContext besuProtocolContext;

  @Test
  void createsAProtocolSpecUsingBesuProtocolSpec() {
    final BlockHeader besuHeader = new BlockHeaderTestFixture().number(1).buildHeader();
    final QbftBlockHeader qbftHeader = new QbftBlockHeaderAdaptor(besuHeader);

    when(besuProtocolSchedule.getByBlockHeader(besuHeader)).thenReturn(besuProtocolSpec);

    final QbftProtocolSchedule qbftProtocolSchedule =
        new QbftProtocolScheduleAdaptor(besuProtocolSchedule, besuProtocolContext);
    final QbftProtocolSpec protocolSpec = qbftProtocolSchedule.getByBlockHeader(qbftHeader);
    assertThat(protocolSpec).hasFieldOrPropertyWithValue("besuProtocolSpec", besuProtocolSpec);
  }
}
