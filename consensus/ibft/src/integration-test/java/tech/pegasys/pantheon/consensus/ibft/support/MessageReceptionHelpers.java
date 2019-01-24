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
import static org.assertj.core.api.Fail.fail;

import tech.pegasys.pantheon.consensus.ibft.messagedata.CommitMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.IbftV2;
import tech.pegasys.pantheon.consensus.ibft.messagedata.NewRoundMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.PrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.ProposalMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.RoundChangeMessageData;
import tech.pegasys.pantheon.consensus.ibft.payload.Payload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
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
            messageMatchesExpected(rxMsgData, msg);
          });
    }
    allPeers.forEach(ValidatorPeer::clearReceivedMessages);
  }

  public static void messageMatchesExpected(
      final MessageData actual, final SignedData<? extends Payload> signedExpectedPayload) {
    final Payload expectedPayload = signedExpectedPayload.getPayload();
    SignedData<?> actualSignedPayload = null;

    switch (expectedPayload.getMessageType()) {
      case IbftV2.PROPOSAL:
        actualSignedPayload = ProposalMessageData.fromMessageData(actual).decode();
        break;
      case IbftV2.PREPARE:
        actualSignedPayload = PrepareMessageData.fromMessageData(actual).decode();
        break;
      case IbftV2.COMMIT:
        actualSignedPayload = CommitMessageData.fromMessageData(actual).decode();
        break;
      case IbftV2.NEW_ROUND:
        actualSignedPayload = NewRoundMessageData.fromMessageData(actual).decode();
        break;
      case IbftV2.ROUND_CHANGE:
        actualSignedPayload = RoundChangeMessageData.fromMessageData(actual).decode();
        break;
      default:
        fail("Illegal IBFTV2 message type.");
        break;
    }
    assertThat(signedExpectedPayload)
        .isEqualToComparingFieldByFieldRecursively(actualSignedPayload);
  }
}
