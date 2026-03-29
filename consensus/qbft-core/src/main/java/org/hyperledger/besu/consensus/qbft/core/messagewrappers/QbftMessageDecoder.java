/*
 * Copyright contributors to Besu.
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
package org.hyperledger.besu.consensus.qbft.core.messagewrappers;

import org.hyperledger.besu.consensus.common.bft.messagewrappers.BftMessage;
import org.hyperledger.besu.consensus.qbft.core.messagedata.CommitMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.PrepareMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.ProposalMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.QbftV1;
import org.hyperledger.besu.consensus.qbft.core.messagedata.RoundChangeMessageData;
import org.hyperledger.besu.consensus.qbft.core.types.QbftBlockCodec;
import org.hyperledger.besu.consensus.qbft.core.types.QbftMessage;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

/** The QbftMessageDecoder decodes a QbftMessage into a BftMessage. */
public class QbftMessageDecoder {

  /** Instantiates a new Qbft message decoder. */
  public QbftMessageDecoder() {}

  /**
   * Decode a QbftMessage into a BftMessage.
   *
   * @param message the QbftMessage to decode
   * @param blockCodec the block codec for decoding block data in Proposal and RoundChange messages
   * @return the decoded BftMessage
   * @throws IllegalArgumentException if the message code is not recognized
   */
  public BftMessage<?> decode(final QbftMessage message, final QbftBlockCodec blockCodec) {
    final MessageData messageData = message.getData();

    return switch (messageData.getCode()) {
      case QbftV1.PROPOSAL -> ProposalMessageData.fromMessageData(messageData).decode(blockCodec);
      case QbftV1.PREPARE -> PrepareMessageData.fromMessageData(messageData).decode();
      case QbftV1.COMMIT -> CommitMessageData.fromMessageData(messageData).decode();
      case QbftV1.ROUND_CHANGE ->
          RoundChangeMessageData.fromMessageData(messageData).decode(blockCodec);
      default ->
          throw new IllegalArgumentException(
              String.format(
                  "Received message with messageCode=%d does not conform to any recognised QBFT message structure",
                  message.getData().getCode()));
    };
  }
}
