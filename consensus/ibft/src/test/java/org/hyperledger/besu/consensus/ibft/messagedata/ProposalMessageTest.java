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
package org.hyperledger.besu.consensus.ibft.messagedata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.ibft.messagewrappers.Proposal;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProposalMessageTest {
  @Mock private Proposal proposalMessageData;
  @Mock private Bytes messageBytes;
  @Mock private MessageData messageData;
  @Mock private ProposalMessageData proposalMessage;

  @Test
  public void createMessageFromPrePrepareMessageData() {
    when(proposalMessageData.encode()).thenReturn(messageBytes);
    final ProposalMessageData proposalMessage = ProposalMessageData.create(proposalMessageData);

    assertThat(proposalMessage.getData()).isEqualTo(messageBytes);
    assertThat(proposalMessage.getCode()).isEqualTo(IbftV2.PROPOSAL);
    verify(proposalMessageData).encode();
  }

  @Test
  public void createMessageFromPrePrepareMessage() {
    final ProposalMessageData message = ProposalMessageData.fromMessageData(proposalMessage);
    assertThat(message).isSameAs(proposalMessage);
  }

  @Test
  public void createMessageFromGenericMessageData() {
    when(messageData.getCode()).thenReturn(IbftV2.PROPOSAL);
    when(messageData.getData()).thenReturn(messageBytes);
    final ProposalMessageData proposalMessage = ProposalMessageData.fromMessageData(messageData);

    assertThat(proposalMessage.getData()).isEqualTo(messageData.getData());
    assertThat(proposalMessage.getCode()).isEqualTo(IbftV2.PROPOSAL);
  }

  @Test
  public void createMessageFailsWhenIncorrectMessageCode() {
    when(messageData.getCode()).thenReturn(42);
    assertThatThrownBy(() -> ProposalMessageData.fromMessageData(messageData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MessageData has code 42 and thus is not a ProposalMessageData");
  }
}
