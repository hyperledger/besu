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
package org.hyperledger.besu.consensus.qbft.core.statemachine;

import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.Gossiper;
import org.hyperledger.besu.consensus.common.bft.MessageTracker;
import org.hyperledger.besu.consensus.common.bft.SynchronizerUpdater;
import org.hyperledger.besu.consensus.common.bft.statemachine.BaseBftController;
import org.hyperledger.besu.consensus.common.bft.statemachine.BaseBlockHeightManager;
import org.hyperledger.besu.consensus.common.bft.statemachine.BftFinalState;
import org.hyperledger.besu.consensus.common.bft.statemachine.FutureMessageBuffer;
import org.hyperledger.besu.consensus.qbft.core.messagedata.CommitMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.PrepareMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.ProposalMessageData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.QbftV1;
import org.hyperledger.besu.consensus.qbft.core.messagedata.RoundChangeMessageData;
import org.hyperledger.besu.ethereum.chain.Blockchain;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.Message;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;

/** The Qbft controller. */
public class QbftController extends BaseBftController {

  private BaseQbftBlockHeightManager currentHeightManager;
  private final QbftBlockHeightManagerFactory qbftBlockHeightManagerFactory;
  private final BftExtraDataCodec bftExtraDataCodec;

  /**
   * Instantiates a new Qbft controller.
   *
   * @param blockchain the blockchain
   * @param bftFinalState the bft final state
   * @param qbftBlockHeightManagerFactory the qbft block height manager factory
   * @param gossiper the gossiper
   * @param duplicateMessageTracker the duplicate message tracker
   * @param futureMessageBuffer the future message buffer
   * @param synchronizerUpdater the synchronizer updater
   * @param bftExtraDataCodec the bft extra data codec
   */
  public QbftController(
      final Blockchain blockchain,
      final BftFinalState bftFinalState,
      final QbftBlockHeightManagerFactory qbftBlockHeightManagerFactory,
      final Gossiper gossiper,
      final MessageTracker duplicateMessageTracker,
      final FutureMessageBuffer futureMessageBuffer,
      final SynchronizerUpdater synchronizerUpdater,
      final BftExtraDataCodec bftExtraDataCodec) {

    super(
        blockchain,
        bftFinalState,
        gossiper,
        duplicateMessageTracker,
        futureMessageBuffer,
        synchronizerUpdater);
    this.qbftBlockHeightManagerFactory = qbftBlockHeightManagerFactory;
    this.bftExtraDataCodec = bftExtraDataCodec;
  }

  @Override
  protected void handleMessage(final Message message) {
    final MessageData messageData = message.getData();

    switch (messageData.getCode()) {
      case QbftV1.PROPOSAL:
        consumeMessage(
            message,
            ProposalMessageData.fromMessageData(messageData).decode(bftExtraDataCodec),
            currentHeightManager::handleProposalPayload);
        break;

      case QbftV1.PREPARE:
        consumeMessage(
            message,
            PrepareMessageData.fromMessageData(messageData).decode(),
            currentHeightManager::handlePreparePayload);
        break;

      case QbftV1.COMMIT:
        consumeMessage(
            message,
            CommitMessageData.fromMessageData(messageData).decode(),
            currentHeightManager::handleCommitPayload);
        break;

      case QbftV1.ROUND_CHANGE:
        consumeMessage(
            message,
            RoundChangeMessageData.fromMessageData(messageData).decode(bftExtraDataCodec),
            currentHeightManager::handleRoundChangePayload);
        break;

      default:
        throw new IllegalArgumentException(
            String.format(
                "Received message with messageCode=%d does not conform to any recognised QBFT message structure",
                message.getData().getCode()));
    }
  }

  @Override
  protected void createNewHeightManager(final BlockHeader parentHeader) {
    currentHeightManager = qbftBlockHeightManagerFactory.create(parentHeader);
  }

  @Override
  protected BaseBlockHeightManager getCurrentHeightManager() {
    return currentHeightManager;
  }
}
