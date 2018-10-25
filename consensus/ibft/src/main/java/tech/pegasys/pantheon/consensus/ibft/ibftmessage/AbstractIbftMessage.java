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
import tech.pegasys.pantheon.ethereum.p2p.NetworkMemoryPool;
import tech.pegasys.pantheon.ethereum.p2p.wire.AbstractMessageData;
import tech.pegasys.pantheon.ethereum.rlp.BytesValueRLPOutput;

import io.netty.buffer.ByteBuf;

public abstract class AbstractIbftMessage extends AbstractMessageData {
  protected AbstractIbftMessage(final ByteBuf data) {
    super(data);
  }

  public abstract IbftSignedMessageData<?> decode();

  protected static ByteBuf writeMessageToByteBuf(
      final IbftSignedMessageData<?> ibftSignedMessageData) {

    BytesValueRLPOutput rlpEncode = new BytesValueRLPOutput();
    ibftSignedMessageData.writeTo(rlpEncode);

    final ByteBuf data = NetworkMemoryPool.allocate(rlpEncode.encodedSize());
    data.writeBytes(rlpEncode.encoded().extractArray());

    return data;
  }
}
