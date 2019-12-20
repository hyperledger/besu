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
package org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.RawMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.messages.DisconnectMessage.DisconnectReason;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Test;

public class DisconnectMessageTest {

  @Test
  public void readFromWithReason() {
    MessageData messageData =
        new RawMessage(WireMessageCodes.DISCONNECT, Bytes.fromHexString("0xC103"));
    DisconnectMessage disconnectMessage = DisconnectMessage.readFrom(messageData);

    DisconnectReason reason = disconnectMessage.getReason();
    assertThat(reason).isEqualTo(DisconnectReason.USELESS_PEER);
  }

  @Test
  public void readFromWithNoReason() {
    MessageData messageData =
        new RawMessage(WireMessageCodes.DISCONNECT, Bytes.fromHexString("0xC180"));
    DisconnectMessage disconnectMessage = DisconnectMessage.readFrom(messageData);

    DisconnectReason reason = disconnectMessage.getReason();
    assertThat(reason).isEqualTo(DisconnectReason.UNKNOWN);
  }

  @Test
  public void readFromWithInvalidReason() {
    String[] invalidReasons = {
      "0xC10C",
      "0xC155",
      // List containing a byte > 128 (negative valued)
      "0xC281FF",
      // List containing a multi-byte reason
      "0xC3820101"
    };

    for (String invalidReason : invalidReasons) {
      MessageData messageData =
          new RawMessage(WireMessageCodes.DISCONNECT, Bytes.fromHexString(invalidReason));
      DisconnectMessage disconnectMessage = DisconnectMessage.readFrom(messageData);
      DisconnectReason reason = disconnectMessage.getReason();
      assertThat(reason).isEqualTo(DisconnectReason.UNKNOWN);
    }
  }

  @Test
  public void readFromWithWrongMessageType() {
    MessageData messageData = new RawMessage(WireMessageCodes.PING, Bytes.fromHexString("0xC103"));
    assertThatThrownBy(() -> DisconnectMessage.readFrom(messageData))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Message has code 2 and thus is not a DisconnectMessage");
  }

  @Test
  public void createWithReason() {
    DisconnectMessage disconnectMessage = DisconnectMessage.create(DisconnectReason.USELESS_PEER);

    assertThat(disconnectMessage.getReason()).isEqualTo(DisconnectReason.USELESS_PEER);
    assertThat(disconnectMessage.getData().toString()).isEqualToIgnoringCase("0xC103");
  }

  @Test
  public void createWithUnknownReason() {
    DisconnectMessage disconnectMessage = DisconnectMessage.create(DisconnectReason.UNKNOWN);

    assertThat(disconnectMessage.getReason()).isEqualTo(DisconnectReason.UNKNOWN);
    assertThat(disconnectMessage.getData().toString()).isEqualToIgnoringCase("0xC180");
  }
}
