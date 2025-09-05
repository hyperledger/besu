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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.hyperledger.besu.consensus.qbft.core.types.QbftBlock;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockHeader;
import org.hyperledger.besu.consensus.qbft.core.types.QbftMessage;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;

import org.junit.jupiter.api.Test;

class AdaptorUtilTest {

  @Test
  void canConvertQbftBlockToBesuBlock() {
    BlockHeader header = new BlockHeaderTestFixture().buildHeader();
    Block besuBlock = new Block(header, BlockBody.empty());
    QbftBlock qbftBlock = new QbftBlockAdaptor(besuBlock);

    assertThat(AdaptorUtil.toBesuBlock(qbftBlock)).isSameAs(besuBlock);
  }

  @Test
  void toBesuBlockThrowsExceptionForUnsupportedBlockType() {
    QbftBlock unsupportedBlock = mock(QbftBlock.class);

    assertThatThrownBy(() -> AdaptorUtil.toBesuBlock(unsupportedBlock))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported block type");
  }

  @Test
  void canConvertQbftBlockHeaderToBesuBlockHeader() {
    BlockHeader besuHeader = new BlockHeaderTestFixture().buildHeader();
    QbftBlockHeader qbftHeader = new QbftBlockHeaderAdaptor(besuHeader);

    assertThat(AdaptorUtil.toBesuBlockHeader(qbftHeader)).isSameAs(besuHeader);
  }

  @Test
  void toBesuBlockHeaderThrowsExceptionForUnsupportedHeaderType() {
    QbftBlockHeader unsupportedHeader = mock(QbftBlockHeader.class);

    assertThatThrownBy(() -> AdaptorUtil.toBesuBlockHeader(unsupportedHeader))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported block header type");
  }

  @Test
  void canConvertQbftMessageToBesuMessage() {
    Message besuMessage = mock(Message.class);
    QbftMessage qbftMessage = new QbftMessageAdaptor(besuMessage);

    assertThat(AdaptorUtil.toBesuMessage(qbftMessage)).isSameAs(besuMessage);
  }

  @Test
  void toBesuMessageThrowsExceptionForUnsupportedMessageType() {
    QbftMessage unsupportedMessage = mock(QbftMessage.class);

    assertThatThrownBy(() -> AdaptorUtil.toBesuMessage(unsupportedMessage))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported message type");
  }
}
