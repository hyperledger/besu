/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.ethereum.p2p.plain;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.Unpooled;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class MessageHandlerTest {

  private static final byte[] PING_DATA = new byte[] {0, 1, 2, 3};

  private static final byte[] PONG_DATA = new byte[] {3, 2, 1, 0};

  private static final byte[] MESSAGE_DATA = new byte[] {0, 1, 1, 0};

  private static final Bytes MESSAGE = Bytes.wrap(MESSAGE_DATA);

  private static final int DATA_CODE = 1000;

  @Test
  public void buildPingMessage() {
    Bytes message = MessageHandler.buildMessage(MessageType.PING, PING_DATA);
    assertThat(message).isNotNull();
    PlainMessage parsed = MessageHandler.parseMessage(Unpooled.wrappedBuffer(message.toArray()));
    assertThat(parsed).isNotNull();
    assertThat(parsed.getMessageType()).isEqualTo(MessageType.PING);
    assertThat(parsed.getData().toArray()).isEqualTo(PING_DATA);
  }

  @Test
  public void buildPongMessage() {
    Bytes message = MessageHandler.buildMessage(MessageType.PONG, PONG_DATA);
    assertThat(message).isNotNull();
    PlainMessage parsed = MessageHandler.parseMessage(Unpooled.wrappedBuffer(message.toArray()));
    assertThat(parsed).isNotNull();
    assertThat(parsed.getMessageType()).isEqualTo(MessageType.PONG);
    assertThat(parsed.getData().toArray()).isEqualTo(PONG_DATA);
  }

  @Test
  public void buildDataMessage() {
    Bytes message = MessageHandler.buildMessage(MessageType.DATA, DATA_CODE, MESSAGE);
    assertThat(message).isNotNull();
    PlainMessage parsed = MessageHandler.parseMessage(Unpooled.wrappedBuffer(message.toArray()));
    assertThat(parsed).isNotNull();
    assertThat(parsed.getMessageType()).isEqualTo(MessageType.DATA);
    assertThat(parsed.getCode()).isEqualTo(DATA_CODE);
    assertThat(parsed.getData().toArray()).isEqualTo(MESSAGE_DATA);
  }
}
