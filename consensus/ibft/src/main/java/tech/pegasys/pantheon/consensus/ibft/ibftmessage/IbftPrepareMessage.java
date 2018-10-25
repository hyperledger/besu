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

import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftSignedMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedPrepareMessageData;
import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import io.netty.buffer.ByteBuf;

public class IbftPrepareMessage extends AbstractIbftMessage {

  private static final int MESSAGE_CODE = IbftV2.PREPARE;

  private IbftPrepareMessage(final ByteBuf data) {
    super(data);
  }

  public static IbftPrepareMessage fromMessage(final MessageData message) {
    if (message instanceof IbftPrepareMessage) {
      message.retain();
      return (IbftPrepareMessage) message;
    }
    final int code = message.getCode();
    if (code != MESSAGE_CODE) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a PrepareMessage", code));
    }

    final ByteBuf data = NetworkMemoryPool.allocate(message.getSize());
    message.writeTo(data);
    return new IbftPrepareMessage(data);
  }

  @Override
  public IbftSignedMessageData<IbftUnsignedPrepareMessageData> decode() {
    return IbftSignedMessageData.readIbftSignedPrepareMessageDataFrom(
        RLP.input(BytesValue.wrapBuffer(data)));
  }

  public static IbftPrepareMessage create(
      final IbftSignedMessageData<IbftUnsignedPrepareMessageData> ibftPrepareMessageDecoded) {

    return new IbftPrepareMessage(writeMessageToByteBuf(ibftPrepareMessageDecoded));
  }

  @Override
  public int getCode() {
    return MESSAGE_CODE;
  }
}
