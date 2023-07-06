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
package org.hyperledger.besu.consensus.common.bft.statemachine;

import static java.util.Arrays.copyOfRange;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.common.bft.network.MockPeerFactory;
import org.hyperledger.besu.ethereum.core.AddressHelpers;
import org.hyperledger.besu.ethereum.p2p.rlpx.connections.PeerConnection;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.DefaultMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class FutureMessageBufferTest {
  private Message message;
  private FutureMessageBuffer futureMsgBuffer;
  private final PeerConnection peerConnection = MockPeerFactory.create(AddressHelpers.ofValue(9));

  @Before
  public void setup() {
    message = createMessage(10);
    futureMsgBuffer = new FutureMessageBuffer(5, 5, 0);
  }

  @Test
  public void addsFutureMessages() {
    futureMsgBuffer.addMessage(1, message);
    futureMsgBuffer.addMessage(2, message);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(1)).containsExactly(message);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(2)).containsExactly(message);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(2)).isEmpty();
  }

  @Test
  public void retrieveMessagesOnHeightWithNoMessagesReturnsEmptySet() {
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(3)).isEmpty();
  }

  @Test
  public void retrieveMessagesDiscardsOldHeights() {
    final Message msg1 = createMessage(1);
    final Message msg2 = createMessage(2);
    final Message msg3 = createMessage(3);
    futureMsgBuffer.addMessage(1, msg1);
    futureMsgBuffer.addMessage(2, msg2);
    futureMsgBuffer.addMessage(3, msg3);

    assertThat(futureMsgBuffer.retrieveMessagesForHeight(2)).containsExactly(msg2);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(1)).isEmpty();
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(3)).containsExactly(msg3);
  }

  @Test
  public void doNotAddFutureMessagesWhenBeyondMaxDistanceFromCurrentHeight() {
    futureMsgBuffer.addMessage(6, message);
    futureMsgBuffer.addMessage(7, message);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(6)).isEmpty();
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(7)).isEmpty();

    futureMsgBuffer.retrieveMessagesForHeight(0);
    futureMsgBuffer.addMessage(1, message);
    futureMsgBuffer.addMessage(4, message);
    futureMsgBuffer.addMessage(5, message);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(1)).containsExactly(message);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(4)).containsExactly(message);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(5)).containsExactly(message);
  }

  @Test
  public void removeHighestChainHeightWhenOverMessageLimit() {
    DefaultMessage[] height1Msgs = addMessages(1, 4);
    addMessages(2, 1);
    futureMsgBuffer.addMessage(1, message);

    List<Message> actualHeight1Msgs = futureMsgBuffer.retrieveMessagesForHeight(1);
    assertThat(actualHeight1Msgs).contains(height1Msgs);
    assertThat(actualHeight1Msgs).contains(message);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(2)).isEmpty();
  }

  @Test
  public void removeOldestMessageWhenOverLimitAndSingleHeightInBuffer() {
    DefaultMessage[] messages = addMessages(1, 5);
    futureMsgBuffer.addMessage(1, message);

    final DefaultMessage[] messagesExcludingFirst = copyOfRange(messages, 1, messages.length);
    final List<Message> actualHeight1Msgs = futureMsgBuffer.retrieveMessagesForHeight(1L);
    assertThat(actualHeight1Msgs).contains(messagesExcludingFirst);
    assertThat(actualHeight1Msgs).contains(message);
  }

  @Test
  public void doNotAddMessageLessThanOrEqualToCurrentChainHeight() {
    futureMsgBuffer = new FutureMessageBuffer(5, 5, 2);
    futureMsgBuffer.addMessage(0, message);
    futureMsgBuffer.addMessage(1, message);
    futureMsgBuffer.addMessage(2, message);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(2)).isEmpty();

    futureMsgBuffer.addMessage(3, message);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(3)).containsExactly(message);
  }

  @Test
  public void doNotAddMessagesWhenLimitIsZero() {
    futureMsgBuffer = new FutureMessageBuffer(1, 0, 0);
    futureMsgBuffer.addMessage(1, message);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(0)).isEmpty();
  }

  @Test
  public void doNotMessageWhenMessageHeightIsHighestAndOverLimit() {
    DefaultMessage[] height1Msgs = addMessages(1, 4);
    DefaultMessage[] height2Msgs = addMessages(2, 1);
    futureMsgBuffer.addMessage(3, message);

    assertThat(futureMsgBuffer.retrieveMessagesForHeight(1)).containsExactly(height1Msgs);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(2)).containsExactly(height2Msgs);
    assertThat(futureMsgBuffer.retrieveMessagesForHeight(3)).isEmpty();
  }

  @Test
  public void totalMessagesSizeUpdatedWhenMessagesAdded() {
    futureMsgBuffer = new FutureMessageBuffer(5, 10, 0);

    addMessages(1, 2);
    assertThat(futureMsgBuffer.totalMessagesSize()).isEqualTo(2);

    addMessages(2, 3);
    assertThat(futureMsgBuffer.totalMessagesSize()).isEqualTo(5);

    addMessages(1, 3);
    assertThat(futureMsgBuffer.totalMessagesSize()).isEqualTo(8);
  }

  @Test
  public void totalMessagesSizeUpdatedWhenMessagesAreRetrieved() {
    futureMsgBuffer = new FutureMessageBuffer(10, 10, 0);
    addMessages(1, 2);
    addMessages(2, 3);
    addMessages(3, 4);

    futureMsgBuffer.retrieveMessagesForHeight(1);
    assertThat(futureMsgBuffer.totalMessagesSize()).isEqualTo(7);

    futureMsgBuffer.retrieveMessagesForHeight(2);
    assertThat(futureMsgBuffer.totalMessagesSize()).isEqualTo(4);

    futureMsgBuffer.retrieveMessagesForHeight(3);
    assertThat(futureMsgBuffer.totalMessagesSize()).isZero();
  }

  @Test
  public void totalSizeUpdatedWhenMessagesAreEvicted() {
    futureMsgBuffer = new FutureMessageBuffer(5, 5, 0);
    addMessages(1, 2);
    addMessages(2, 3);

    // height 2 will be evicted
    futureMsgBuffer.addMessage(1, message);
    assertThat(futureMsgBuffer.totalMessagesSize()).isEqualTo(3);

    for (int i = 0; i <= 5; i++) {
      futureMsgBuffer.addMessage(1, message);
    }
    assertThat(futureMsgBuffer.totalMessagesSize()).isEqualTo(5);
  }

  private DefaultMessage[] addMessages(final long height, final int count) {
    return IntStream.range(0, count)
        .mapToObj(
            i -> {
              final DefaultMessage message = createMessage(i);
              futureMsgBuffer.addMessage(height, message);
              return message;
            })
        .collect(Collectors.toList())
        .toArray(new DefaultMessage[] {});
  }

  private DefaultMessage createMessage(final int i) {
    final MessageData messageData = new RawMessage(0, Bytes.fromHexStringLenient("0x" + i));
    return new DefaultMessage(peerConnection, messageData);
  }
}
