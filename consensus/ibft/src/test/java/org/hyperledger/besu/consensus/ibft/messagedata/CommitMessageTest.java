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

import org.hyperledger.besu.consensus.ibft.messagewrappers.Commit;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CommitMessageTest {
  @Mock private Commit commitPayload;
  @Mock private Bytes messageBytes;
  @Mock private MessageData messageData;
  @Mock private CommitMessageData commitMessage;

  @Test
  public void createMessageFromCommitMessageData() {
    when(commitPayload.encode()).thenReturn(messageBytes);
    CommitMessageData commitMessage = CommitMessageData.create(commitPayload);

    assertThat(commitMessage.getData()).isEqualTo(messageBytes);
    assertThat(commitMessage.getCode()).isEqualTo(IbftV2.COMMIT);
    verify(commitPayload).encode();
  }

  @Test
  public void createMessageFromCommitMessage() {
    CommitMessageData message = CommitMessageData.fromMessageData(commitMessage);
    assertThat(message).isSameAs(commitMessage);
  }

  @Test
  public void createMessageFromGenericMessageData() {
    when(messageData.getData()).thenReturn(messageBytes);
    when(messageData.getCode()).thenReturn(IbftV2.COMMIT);
    CommitMessageData commitMessage = CommitMessageData.fromMessageData(messageData);

    assertThat(commitMessage.getData()).isEqualTo(messageData.getData());
    assertThat(commitMessage.getCode()).isEqualTo(IbftV2.COMMIT);
  }

  @Test
  public void createMessageFailsWhenIncorrectMessageCode() {
    when(messageData.getCode()).thenReturn(42);
    assertThatThrownBy(() -> CommitMessageData.fromMessageData(messageData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MessageData has code 42 and thus is not a CommitMessageData");
  }
}
