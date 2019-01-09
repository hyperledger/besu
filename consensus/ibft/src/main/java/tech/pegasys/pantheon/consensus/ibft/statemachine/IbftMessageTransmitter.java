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
package tech.pegasys.pantheon.consensus.ibft.statemachine;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.CommitMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.NewRoundMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.PrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.ProposalMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessage.RoundChangeMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.SignedData;
import tech.pegasys.pantheon.consensus.ibft.network.IbftMulticaster;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;

import java.util.Optional;

public class IbftMessageTransmitter {

  private final MessageFactory messageFactory;
  private final IbftMulticaster multicaster;

  public IbftMessageTransmitter(
      final MessageFactory messageFactory, final IbftMulticaster multicaster) {
    this.messageFactory = messageFactory;
    this.multicaster = multicaster;
  }

  public void multicastProposal(final ConsensusRoundIdentifier roundIdentifier, final Block block) {
    final SignedData<ProposalPayload> signedPayload =
        messageFactory.createSignedProposalPayload(roundIdentifier, block);

    final ProposalMessageData message = ProposalMessageData.create(signedPayload);

    multicaster.multicastToValidators(message);
  }

  public void multicastPrepare(final ConsensusRoundIdentifier roundIdentifier, final Hash digest) {
    final SignedData<PreparePayload> signedPayload =
        messageFactory.createSignedPreparePayload(roundIdentifier, digest);

    final PrepareMessageData message = PrepareMessageData.create(signedPayload);

    multicaster.multicastToValidators(message);
  }

  public void multicastCommit(
      final ConsensusRoundIdentifier roundIdentifier,
      final Hash digest,
      final Signature commitSeal) {
    final SignedData<CommitPayload> signedPayload =
        messageFactory.createSignedCommitPayload(roundIdentifier, digest, commitSeal);

    final CommitMessageData message = CommitMessageData.create(signedPayload);

    multicaster.multicastToValidators(message);
  }

  public void multicastRoundChange(
      final ConsensusRoundIdentifier roundIdentifier,
      final Optional<PreparedCertificate> preparedCertificate) {

    final SignedData<RoundChangePayload> signedPayload =
        messageFactory.createSignedRoundChangePayload(roundIdentifier, preparedCertificate);

    final RoundChangeMessageData message = RoundChangeMessageData.create(signedPayload);

    multicaster.multicastToValidators(message);
  }

  public void multicastNewRound(
      final ConsensusRoundIdentifier roundIdentifier,
      final RoundChangeCertificate roundChangeCertificate,
      final SignedData<ProposalPayload> proposalPayload) {

    final SignedData<NewRoundPayload> signedPayload =
        messageFactory.createSignedNewRoundPayload(
            roundIdentifier, roundChangeCertificate, proposalPayload);

    final NewRoundMessageData message = NewRoundMessageData.create(signedPayload);

    multicaster.multicastToValidators(message);
  }
}
