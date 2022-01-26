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
package org.hyperledger.besu.consensus.qbft.network;

import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.network.ValidatorMulticaster;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagedata.CommitMessageData;
import org.hyperledger.besu.consensus.qbft.messagedata.PrepareMessageData;
import org.hyperledger.besu.consensus.qbft.messagedata.ProposalMessageData;
import org.hyperledger.besu.consensus.qbft.messagedata.RoundChangeMessageData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Commit;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.qbft.statemachine.PreparedCertificate;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.plugin.services.securitymodule.SecurityModuleException;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QbftMessageTransmitter {

  private static final Logger LOG = LoggerFactory.getLogger(QbftMessageTransmitter.class);

  private final MessageFactory messageFactory;
  private final ValidatorMulticaster multicaster;

  public QbftMessageTransmitter(
      final MessageFactory messageFactory, final ValidatorMulticaster multicaster) {
    this.messageFactory = messageFactory;
    this.multicaster = multicaster;
  }

  public void multicastProposal(
      final ConsensusRoundIdentifier roundIdentifier,
      final Block block,
      final List<SignedData<RoundChangePayload>> roundChanges,
      final List<SignedData<PreparePayload>> prepares) {
    try {
      final Proposal data =
          messageFactory.createProposal(roundIdentifier, block, roundChanges, prepares);

      final ProposalMessageData message = ProposalMessageData.create(data);

      multicaster.send(message);
    } catch (final SecurityModuleException e) {
      LOG.warn("Failed to generate signature for Proposal (not sent): {} ", e.getMessage());
    }
  }

  public void multicastPrepare(final ConsensusRoundIdentifier roundIdentifier, final Hash digest) {
    try {
      final Prepare data = messageFactory.createPrepare(roundIdentifier, digest);

      final PrepareMessageData message = PrepareMessageData.create(data);

      multicaster.send(message);
    } catch (final SecurityModuleException e) {
      LOG.warn("Failed to generate signature for Prepare (not sent): {} ", e.getMessage());
    }
  }

  public void multicastCommit(
      final ConsensusRoundIdentifier roundIdentifier,
      final Hash digest,
      final SECPSignature commitSeal) {
    try {
      final Commit data = messageFactory.createCommit(roundIdentifier, digest, commitSeal);

      final CommitMessageData message = CommitMessageData.create(data);

      multicaster.send(message);
    } catch (final SecurityModuleException e) {
      LOG.warn("Failed to generate signature for Commit (not sent): {} ", e.getMessage());
    }
  }

  public void multicastRoundChange(
      final ConsensusRoundIdentifier roundIdentifier,
      final Optional<PreparedCertificate> preparedRoundCertificate) {
    try {
      final RoundChange data =
          messageFactory.createRoundChange(roundIdentifier, preparedRoundCertificate);

      final RoundChangeMessageData message = RoundChangeMessageData.create(data);

      multicaster.send(message);
    } catch (final SecurityModuleException e) {
      LOG.warn("Failed to generate signature for RoundChange (not sent): {} ", e.getMessage());
    }
  }
}
