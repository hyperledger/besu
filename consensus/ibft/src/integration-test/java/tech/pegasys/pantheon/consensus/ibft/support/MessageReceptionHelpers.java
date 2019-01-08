/*
 * Copyright 2019 ConsenSys AG.
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
package tech.pegasys.pantheon.consensus.ibft.support;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.consensus.ibft.ibftmessage.CommitMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.IbftV2;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.NewRoundMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.PrepareMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.ProposalMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.RoundChangeMessage;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.Payload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.SignedData;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class MessageReceptionHelpers {

  public static void assertPeersReceivedNoMessages(final Collection<ValidatorPeer> nodes) {
    nodes.forEach(n -> assertThat(n.getReceivedMessages()).isEmpty());
  }

  @SafeVarargs
  public static void assertPeersReceivedExactly(
      final Collection<ValidatorPeer> allPeers, final SignedData<? extends Payload>... msgs) {
    allPeers.forEach(n -> assertThat(n.getReceivedMessages().size()).isEqualTo(msgs.length));

    List<SignedData<? extends Payload>> msgList = Arrays.asList(msgs);

    for (int i = 0; i < msgList.size(); i++) {
      final int index = i;
      final SignedData<? extends Payload> msg = msgList.get(index);
      allPeers.forEach(
          n -> {
            final List<MessageData> rxMsgs = n.getReceivedMessages();
            final MessageData rxMsgData = rxMsgs.get(index);
            assertThat(msgMatchesExpected(rxMsgData, msg)).isTrue();
          });
    }
    allPeers.forEach(p -> p.clearReceivedMessages());
  }

  public static boolean msgMatchesExpected(
      final MessageData actual, final SignedData<? extends Payload> expected) {
    final Payload expectedPayload = expected.getPayload();

    switch (expectedPayload.getMessageType()) {
      case IbftV2.PROPOSAL:
        return ProposalMessage.fromMessage(actual).decode().equals(expected);
      case IbftV2.PREPARE:
        return PrepareMessage.fromMessage(actual).decode().equals(expected);
      case IbftV2.COMMIT:
        return CommitMessage.fromMessage(actual).decode().equals(expected);
      case IbftV2.NEW_ROUND:
        return NewRoundMessage.fromMessage(actual).decode().equals(expected);
      case IbftV2.ROUND_CHANGE:
        return RoundChangeMessage.fromMessage(actual).decode().equals(expected);
      default:
        return false;
    }
  }
}
