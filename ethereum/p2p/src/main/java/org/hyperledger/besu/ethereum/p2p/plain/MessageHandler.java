/*
 * Copyright Hyperledger Besu Contributors.
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

import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import io.netty.buffer.ByteBuf;
import org.apache.tuweni.bytes.Bytes;

public class MessageHandler {

  public static Bytes buildMessage(final PlainMessage message) {
    final BytesValueRLPOutput ret = new BytesValueRLPOutput();
    ret.startList();
    ret.writeInt(message.getMessageType().getValue());
    if (MessageType.DATA.equals(message.getMessageType())) {
      ret.writeInt(message.getCode());
    }
    ret.writeBytes(message.getData());
    ret.endList();
    return ret.encoded();
  }

  public static Bytes buildMessage(final MessageType messageType, final Bytes data) {
    return buildMessage(new PlainMessage(messageType, data));
  }

  public static Bytes buildMessage(
      final MessageType messageType, final int code, final Bytes data) {
    return buildMessage(new PlainMessage(messageType, code, data));
  }

  public static Bytes buildMessage(final MessageType messageType, final byte[] data) {
    return buildMessage(new PlainMessage(messageType, data));
  }

  public static PlainMessage parseMessage(final ByteBuf buf) {
    PlainMessage ret = null;
    final ByteBuf bufferedBytes = buf.readSlice(buf.readableBytes());
    final byte[] byteArr = new byte[bufferedBytes.readableBytes()];
    bufferedBytes.getBytes(0, byteArr);
    Bytes bytes = Bytes.wrap(byteArr);
    final RLPInput input = new BytesValueRLPInput(bytes, true);
    input.enterList();
    MessageType type = MessageType.forNumber(input.readInt());
    if (MessageType.DATA.equals(type)) {
      ret = new PlainMessage(type, input.readInt(), input.readBytes());
    } else {
      ret = new PlainMessage(type, input.readBytes());
    }
    return ret;
  }
}
