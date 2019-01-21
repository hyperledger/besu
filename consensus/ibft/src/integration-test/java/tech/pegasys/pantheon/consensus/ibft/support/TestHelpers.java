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

import static java.util.Optional.empty;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockHashing;
import tech.pegasys.pantheon.consensus.ibft.IbftExtraData;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparedCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.util.Collection;
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

    return messageFactory.createSignedCommitPayload(roundId, block.getHash(), commitSeal);
  }

  public static PreparedCertificate createValidPreparedCertificate(
      final TestContext context, final ConsensusRoundIdentifier preparedRound, final Block block) {
    final RoundSpecificNodeRoles roles = context.getRoundSpecificRoles(preparedRound);

    return new PreparedCertificate(
        roles.getProposer().getMessageFactory().createSignedProposalPayload(preparedRound, block),
        roles
            .getNonProposingPeers()
            .stream()
            .map(
                role ->
                    role.getMessageFactory()
                        .createSignedPreparePayload(preparedRound, block.getHash()))
            .collect(Collectors.toList()));
  }

  public static SignedData<NewRoundPayload> injectEmptyNewRound(
      final ConsensusRoundIdentifier targetRoundId,
      final ValidatorPeer proposer,
      final Collection<ValidatorPeer> peers,
      final Block blockToPropose) {

    final List<SignedData<RoundChangePayload>> roundChangePayloads =
        peers
            .stream()
            .map(p -> p.getMessageFactory().createSignedRoundChangePayload(targetRoundId, empty()))
            .collect(Collectors.toList());

    final SignedData<ProposalPayload> proposal =
        proposer.getMessageFactory().createSignedProposalPayload(targetRoundId, blockToPropose);

    return proposer.injectNewRound(
        targetRoundId, new RoundChangeCertificate(roundChangePayloads), proposal);
  }
}
