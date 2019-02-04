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
package tech.pegasys.pantheon.consensus.ibft.network;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.messagedata.CommitMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.NewRoundMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.PrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.ProposalMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagedata.RoundChangeMessageData;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Commit;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.NewRound;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.RoundChange;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.statemachine.TerminatedRoundArtefacts;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;

import java.util.Optional;

public class IbftMessageTransmitter {

  private final MessageFactory messageFactory;
  private final ValidatorMulticaster multicaster;

  public IbftMessageTransmitter(
      final MessageFactory messageFactory, final ValidatorMulticaster multicaster) {
    this.messageFactory = messageFactory;
    this.multicaster = multicaster;
  }

  public void multicastProposal(final ConsensusRoundIdentifier roundIdentifier, final Block block) {
    final Proposal data = messageFactory.createSignedProposalPayload(roundIdentifier, block);

    final ProposalMessageData message = ProposalMessageData.create(data);

    multicaster.send(message);
  }

  public void multicastPrepare(final ConsensusRoundIdentifier roundIdentifier, final Hash digest) {
    final Prepare data = messageFactory.createSignedPreparePayload(roundIdentifier, digest);

    final PrepareMessageData message = PrepareMessageData.create(data);

    multicaster.send(message);
  }

  public void multicastCommit(
      final ConsensusRoundIdentifier roundIdentifier,
      final Hash digest,
      final Signature commitSeal) {
    final Commit data =
        messageFactory.createSignedCommitPayload(roundIdentifier, digest, commitSeal);

    final CommitMessageData message = CommitMessageData.create(data);

    multicaster.send(message);
  }

  public void multicastRoundChange(
      final ConsensusRoundIdentifier roundIdentifier,
      final Optional<TerminatedRoundArtefacts> terminatedRoundArtefacts) {

    final RoundChange data =
        messageFactory.createSignedRoundChangePayload(roundIdentifier, terminatedRoundArtefacts);

    final RoundChangeMessageData message = RoundChangeMessageData.create(data);

    multicaster.send(message);
  }

  public void multicastNewRound(
      final ConsensusRoundIdentifier roundIdentifier,
      final RoundChangeCertificate roundChangeCertificate,
      final SignedData<ProposalPayload> proposalPayload) {

    final NewRound signedPayload =
        messageFactory.createSignedNewRoundPayload(
            roundIdentifier, roundChangeCertificate, proposalPayload);

    final NewRoundMessageData message = NewRoundMessageData.create(signedPayload);

    multicaster.send(message);
  }
}
