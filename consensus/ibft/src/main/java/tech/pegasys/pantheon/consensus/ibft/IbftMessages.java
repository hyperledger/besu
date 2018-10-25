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
package tech.pegasys.pantheon.consensus.ibft;

import tech.pegasys.pantheon.consensus.ibft.ibftmessage.IbftPrePrepareMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.IbftPrepareMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.IbftRoundChangeMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.IbftV2;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftSignedMessageData;
import tech.pegasys.pantheon.ethereum.p2p.api.Message;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;

public class IbftMessages {

  public static IbftSignedMessageData<?> fromMessage(final Message message) {
    final MessageData messageData = message.getData();

    switch (messageData.getCode()) {
      case IbftV2.PRE_PREPARE:
        return IbftPrePrepareMessage.fromMessage(messageData).decode();

      case IbftV2.PREPARE:
        return IbftPrepareMessage.fromMessage(messageData).decode();

      case IbftV2.ROUND_CHANGE:
        return IbftRoundChangeMessage.fromMessage(messageData).decode();

      default:
        throw new IllegalArgumentException(
            "Received message does not conform to any recognised IBFT message structure.");
    }
  }
}
