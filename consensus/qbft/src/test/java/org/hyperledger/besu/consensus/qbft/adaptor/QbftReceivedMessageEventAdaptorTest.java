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

import org.hyperledger.besu.consensus.common.bft.events.BftEvents;
import org.hyperledger.besu.consensus.common.bft.events.BftReceivedMessageEvent;
import org.hyperledger.besu.consensus.qbft.core.types.QbftMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QbftReceivedMessageEventAdaptorTest {

  @Mock private BftReceivedMessageEvent mockBftReceivedMessageEvent;
  @Mock private Message mockMessage;

  private final MessageData testMessageData = new RawMessage(1, Bytes.of(1, 2, 3, 4));

  private QbftReceivedMessageEventAdaptor adaptor;

  @BeforeEach
  void setUp() {
    when(mockBftReceivedMessageEvent.getMessage()).thenReturn(mockMessage);
    when(mockMessage.getData()).thenReturn(testMessageData);

    adaptor = new QbftReceivedMessageEventAdaptor(mockBftReceivedMessageEvent);
  }

  @Test
  void shouldExtractMessageFromBftReceivedMessageEvent() {
    QbftMessage qbftMessage = adaptor.getMessage();

    assertThat(qbftMessage).isInstanceOf(QbftMessageAdaptor.class);
    assertThat(qbftMessage.getMessageData()).isEqualTo(testMessageData);
    assertThat(((QbftMessageAdaptor) qbftMessage).getBesuMessage()).isEqualTo(mockMessage);
  }

  @Test
  void shouldReturnCorrectEventType() {
    assertThat(adaptor.getType()).isEqualTo(BftEvents.Type.MESSAGE);
  }
}
