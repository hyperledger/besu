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
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedPrePrepareMessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.util.bytes.BytesValue;

public class IbftPrePrepareMessage extends AbstractIbftMessage {

  private static final int MESSAGE_CODE = IbftV2.PRE_PREPARE;

  private IbftPrePrepareMessage(final BytesValue data) {
    super(data);
  }

  public static IbftPrePrepareMessage fromMessage(final MessageData message) {
    return fromMessage(
        message, MESSAGE_CODE, IbftPrePrepareMessage.class, IbftPrePrepareMessage::new);
  }

  @Override
  public IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> decode() {
    return IbftSignedMessageData.readIbftSignedPrePrepareMessageDataFrom(RLP.input(data));
  }

  public static IbftPrePrepareMessage create(
      final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> ibftPrepareMessageDecoded) {

    return new IbftPrePrepareMessage(ibftPrepareMessageDecoded.encode());
  }

  @Override
  public int getCode() {
    return MESSAGE_CODE;
  }
}
