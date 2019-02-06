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

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockHashing;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.NewRound;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Prepare;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.statemachine.PreparedRoundArtifacts;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.util.List;
import java.util.stream.Collectors;

public class TestHelpers {

  public static SignedData<CommitPayload> createSignedCommitPayload(
      final ConsensusRoundIdentifier roundId, final Block block, final KeyPair signingKeyPair) {

    final IbftExtraData extraData = IbftExtraData.decode(block.getHeader().getExtraData());

    final Signature commitSeal =
        SECP256K1.sign(
            IbftBlockHashing.calculateDataHashForCommittedSeal(block.getHeader(), extraData),
            signingKeyPair);

    final MessageFactory messageFactory = new MessageFactory(signingKeyPair);

    return messageFactory.createCommit(roundId, block.getHash(), commitSeal).getSignedPayload();
  }

  public static PreparedRoundArtifacts createValidPreparedRoundArtifacts(
      final TestContext context, final ConsensusRoundIdentifier preparedRound, final Block block) {
    final RoundSpecificPeers peers = context.roundSpecificPeers(preparedRound);

    return new PreparedRoundArtifacts(
        peers.getProposer().getMessageFactory().createProposal(preparedRound, block),
        peers
            .createSignedPreparePayloadOfNonProposing(preparedRound, block.getHash())
            .stream()
            .map(Prepare::new)
            .collect(Collectors.toList()));
  }

  public static NewRound injectEmptyNewRound(
      final ConsensusRoundIdentifier targetRoundId,
      final ValidatorPeer proposer,
      final List<SignedData<RoundChangePayload>> roundChangePayloads,
      final Block blockToPropose) {

    final Proposal proposal =
        proposer.getMessageFactory().createProposal(targetRoundId, blockToPropose);

    return proposer.injectNewRound(
        targetRoundId,
        new RoundChangeCertificate(roundChangePayloads),
        proposal.getSignedPayload());
  }
}
