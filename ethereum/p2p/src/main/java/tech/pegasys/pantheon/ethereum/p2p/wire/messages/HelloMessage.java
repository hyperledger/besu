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
package tech.pegasys.pantheon.ethereum.p2p.wire.messages;

import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.utils.ByteBufUtils;
import tech.pegasys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.PeerInfo;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;

import io.netty.buffer.ByteBuf;

public final class HelloMessage extends AbstractMessageData {

  private HelloMessage(final ByteBuf data) {
    super(data);
  }

  public static HelloMessage create(final PeerInfo peerInfo) {
    final BytesValueRLPOutput out = new BytesValueRLPOutput();
    peerInfo.writeTo(out);
    final ByteBuf buf = ByteBufUtils.fromRLPOutput(out);

    return new HelloMessage(buf);
  }

  public static HelloMessage create(final ByteBuf data) {
    return new HelloMessage(data);
  }

  public static HelloMessage readFrom(final MessageData message) {
    if (message instanceof HelloMessage) {
      message.retain();
      return (HelloMessage) message;
    }
    final int code = message.getCode();
    if (code != WireMessageCodes.HELLO) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a HelloMessage.", code));
    }
    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new HelloMessage(data);
  }

  @Override
  public int getCode() {
    return WireMessageCodes.HELLO;
  }

  public PeerInfo getPeerInfo() {
    return PeerInfo.readFrom(ByteBufUtils.toRLPInput(data));
  }

  @Override
  public String toString() {
    return "HelloMessage{" + "data=" + data + '}';
  }
}
