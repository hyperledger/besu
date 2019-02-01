/*
 * Copyright 2018 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.consensus.ibft.messagedata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.messagewrappers.NewRound;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NewRoundMessageTest {
  @Mock private NewRound newRoundPayload;
  @Mock private BytesValue messageBytes;
  @Mock private MessageData messageData;
  @Mock private NewRoundMessageData newRoundMessage;

  @Test
  public void createMessageFromNewRoundChangeMessageData() {
    when(newRoundPayload.encode()).thenReturn(messageBytes);
    NewRoundMessageData prepareMessage = NewRoundMessageData.create(newRoundPayload);

    assertThat(prepareMessage.getData()).isEqualTo(messageBytes);
    assertThat(prepareMessage.getCode()).isEqualTo(IbftV2.NEW_ROUND);
    verify(newRoundPayload).encode();
  }

  @Test
  public void createMessageFromNewRoundMessage() {
    NewRoundMessageData message = NewRoundMessageData.fromMessageData(newRoundMessage);
    assertThat(message).isSameAs(newRoundMessage);
  }

  @Test
  public void createMessageFromGenericMessageData() {
    when(messageData.getData()).thenReturn(messageBytes);
    when(messageData.getCode()).thenReturn(IbftV2.NEW_ROUND);
    NewRoundMessageData newRoundMessage = NewRoundMessageData.fromMessageData(messageData);

    assertThat(newRoundMessage.getData()).isEqualTo(messageData.getData());
    assertThat(newRoundMessage.getCode()).isEqualTo(IbftV2.NEW_ROUND);
  }

  @Test
  public void createMessageFailsWhenIncorrectMessageCode() {
    when(messageData.getCode()).thenReturn(42);
    assertThatThrownBy(() -> NewRoundMessageData.fromMessageData(messageData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MessageData has code 42 and thus is not a NewRoundMessageData");
  }
}
