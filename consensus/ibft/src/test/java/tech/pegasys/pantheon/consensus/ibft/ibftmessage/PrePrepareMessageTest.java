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
package tech.pegasys.pantheon.consensus.ibft.ibftmessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.SignedData;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PrePrepareMessageTest {
  @Mock private SignedData<ProposalPayload> prePrepareMessageData;
  @Mock private BytesValue messageBytes;
  @Mock private MessageData messageData;
  @Mock private ProposalMessage proposalMessage;

  @Test
  public void createMessageFromPrePrepareMessageData() {
    when(prePrepareMessageData.encode()).thenReturn(messageBytes);
    ProposalMessage proposalMessage = ProposalMessage.create(prePrepareMessageData);

    assertThat(proposalMessage.getData()).isEqualTo(messageBytes);
    assertThat(proposalMessage.getCode()).isEqualTo(IbftV2.PROPOSAL);
    verify(prePrepareMessageData).encode();
  }

  @Test
  public void createMessageFromPrePrepareMessage() {
    ProposalMessage message = ProposalMessage.fromMessage(proposalMessage);
    assertThat(message).isSameAs(proposalMessage);
  }

  @Test
  public void createMessageFromGenericMessageData() {
    when(messageData.getCode()).thenReturn(IbftV2.PROPOSAL);
    when(messageData.getData()).thenReturn(messageBytes);
    ProposalMessage proposalMessage = ProposalMessage.fromMessage(messageData);

    assertThat(proposalMessage.getData()).isEqualTo(messageData.getData());
    assertThat(proposalMessage.getCode()).isEqualTo(IbftV2.PROPOSAL);
  }

  @Test
  public void createMessageFailsWhenIncorrectMessageCode() {
    when(messageData.getCode()).thenReturn(42);
    assertThatThrownBy(() -> ProposalMessage.fromMessage(messageData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Message has code 42 and thus is not a ProposalMessage");
  }
}
