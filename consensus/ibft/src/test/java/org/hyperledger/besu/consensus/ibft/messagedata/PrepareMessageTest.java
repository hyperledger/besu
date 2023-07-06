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

import org.hyperledger.besu.consensus.ibft.messagewrappers.Prepare;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PrepareMessageTest {
  @Mock private Prepare preparePayload;
  @Mock private Bytes messageBytes;
  @Mock private MessageData messageData;
  @Mock private PrepareMessageData prepareMessage;

  @Test
  public void createMessageFromPrepareMessageData() {
    when(preparePayload.encode()).thenReturn(messageBytes);
    PrepareMessageData prepareMessage = PrepareMessageData.create(preparePayload);

    assertThat(prepareMessage.getData()).isEqualTo(messageBytes);
    assertThat(prepareMessage.getCode()).isEqualTo(IbftV2.PREPARE);
    verify(preparePayload).encode();
  }

  @Test
  public void createMessageFromPrepareMessage() {
    PrepareMessageData message = PrepareMessageData.fromMessageData(prepareMessage);
    assertThat(message).isSameAs(prepareMessage);
  }

  @Test
  public void createMessageFromGenericMessageData() {
    when(messageData.getData()).thenReturn(messageBytes);
    when(messageData.getCode()).thenReturn(IbftV2.PREPARE);
    PrepareMessageData prepareMessage = PrepareMessageData.fromMessageData(messageData);

    assertThat(prepareMessage.getData()).isEqualTo(messageBytes);
    assertThat(prepareMessage.getCode()).isEqualTo(IbftV2.PREPARE);
  }

  @Test
  public void createMessageFailsWhenIncorrectMessageCode() {
    when(messageData.getCode()).thenReturn(42);
    assertThatThrownBy(() -> PrepareMessageData.fromMessageData(messageData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MessageData has code 42 and thus is not a PrepareMessageData");
  }
}
