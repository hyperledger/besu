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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftBlockHashing;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QbftBlockHashingAdaptorTest {
  @Mock private BftBlockHashing bftBlockHashing;

  @Test
  void calculatesHashForCommittedSeal() {
    BlockHeader header = new BlockHeaderTestFixture().buildHeader();
    QbftBlockHeader qbftBlockHeader = new QbftBlockHeaderAdaptor(header);
    final BftExtraData bftExtraData =
        new BftExtraData(Bytes.wrap(new byte[32]), emptyList(), Optional.empty(), 0, emptyList());
    when(bftBlockHashing.calculateDataHashForCommittedSeal(header, bftExtraData))
        .thenReturn(Hash.EMPTY);

    QbftBlockHashingAdaptor qbftBlockHashingAdaptor = new QbftBlockHashingAdaptor(bftBlockHashing);
    Hash hash =
        qbftBlockHashingAdaptor.calculateDataHashForCommittedSeal(qbftBlockHeader, bftExtraData);
    assertThat(hash).isEqualTo(Hash.EMPTY);
  }
}
