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
package org.hyperledger.besu.consensus.qbft.core.network;

import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.Gossiper;
import org.hyperledger.besu.consensus.common.bft.network.ValidatorMulticaster;
import org.hyperledger.besu.consensus.common.bft.payload.Authored;
import org.hyperledger.besu.consensus.qbft.core.messagedata.CommitMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.PrepareMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.ProposalMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.QbftV1;
import org.hyperledger.besu.consensus.qbft.core.messagedata.RoundChangeMessageData;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

import java.util.List;

import com.google.common.collect.Lists;

/** Class responsible for rebroadcasting QBFT messages to known validators */
public class QbftGossip implements Gossiper {

  private final ValidatorMulticaster multicaster;
  private final BftExtraDataCodec bftExtraDataCodec;

  /**
   * Constructor that attaches gossip logic to a set of multicaster
   *
   * @param multicaster Network connections to the remote validators
   * @param bftExtraDataCodec Codec used when decoding MessageData
   */
  public QbftGossip(
      final ValidatorMulticaster multicaster, final BftExtraDataCodec bftExtraDataCodec) {
    this.multicaster = multicaster;
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  /**
   * Retransmit a given QBFT message to other known validators nodes
   *
   * @param message The raw message to be gossiped
   */
  @Override
  public void send(final Message message) {
    final MessageData messageData = message.getData();
    final Authored decodedMessage;
    switch (messageData.getCode()) {
      case QbftV1.PROPOSAL:
        decodedMessage = ProposalMessageData.fromMessageData(messageData).decode(bftExtraDataCodec);
        break;
      case QbftV1.PREPARE:
        decodedMessage = PrepareMessageData.fromMessageData(messageData).decode();
        break;
      case QbftV1.COMMIT:
        decodedMessage = CommitMessageData.fromMessageData(messageData).decode();
        break;
      case QbftV1.ROUND_CHANGE:
        decodedMessage =
            RoundChangeMessageData.fromMessageData(messageData).decode(bftExtraDataCodec);
        break;
      default:
        throw new IllegalArgumentException(
            "Received message does not conform to any recognised QBFT message structure.");
    }
    final List<Address> excludeAddressesList =
        Lists.newArrayList(
            message.getConnection().getPeerInfo().getAddress(), decodedMessage.getAuthor());

    multicaster.send(messageData, excludeAddressesList);
  }
}
