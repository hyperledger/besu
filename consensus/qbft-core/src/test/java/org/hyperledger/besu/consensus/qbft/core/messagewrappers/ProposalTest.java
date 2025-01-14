/*
 * Copyright 2020 ConsenSys AG.
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

import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.core.messagedata.QbftV1;
import org.hyperledger.besu.consensus.qbft.core.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.core.payload.PreparedRoundMetadata;
import org.hyperledger.besu.consensus.qbft.core.payload.ProposalPayload;
import org.hyperledger.besu.consensus.qbft.core.payload.RoundChangePayload;
import org.hyperledger.besu.cryptoservices.NodeKey;
import org.hyperledger.besu.cryptoservices.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;
import org.hyperledger.besu.ethereum.core.Util;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProposalTest {
  @Mock private BftExtraDataCodec bftExtraDataCodec;

  private static final Block BLOCK =
      new Block(
          new BlockHeaderTestFixture().buildHeader(),
          new BlockBody(
              Collections.emptyList(),
              Collections.emptyList(),
              Optional.of(Collections.emptyList())));

  @Test
  public void canRoundTripProposalMessage() {
    final NodeKey nodeKey = NodeKeyUtils.generate();
    final Address addr = Util.publicKeyToAddress(nodeKey.getPublicKey());

    final ProposalPayload payload = new ProposalPayload(new ConsensusRoundIdentifier(1, 1), BLOCK);

    final SignedData<ProposalPayload> signedPayload =
        SignedData.create(payload, nodeKey.sign(payload.hashForSignature()));

    final PreparePayload preparePayload =
        new PreparePayload(new ConsensusRoundIdentifier(1, 0), BLOCK.getHash());
    final SignedData<PreparePayload> prepare =
        SignedData.create(preparePayload, nodeKey.sign(preparePayload.hashForSignature()));

    final RoundChangePayload roundChangePayload =
        new RoundChangePayload(
            new ConsensusRoundIdentifier(1, 0),
            Optional.of(new PreparedRoundMetadata(BLOCK.getHash(), 0)));

    final SignedData<RoundChangePayload> roundChange =
        SignedData.create(roundChangePayload, nodeKey.sign(roundChangePayload.hashForSignature()));

    final Proposal proposal = new Proposal(signedPayload, List.of(roundChange), List.of(prepare));

    final Proposal decodedProposal = Proposal.decode(proposal.encode(), bftExtraDataCodec);

    assertThat(decodedProposal.getAuthor()).isEqualTo(addr);
    assertThat(decodedProposal.getMessageType()).isEqualTo(QbftV1.PROPOSAL);
    assertThat(decodedProposal.getPrepares()).hasSize(1);
    assertThat(decodedProposal.getPrepares().get(0)).isEqualToComparingFieldByField(prepare);
    assertThat(decodedProposal.getRoundChanges()).hasSize(1);
    assertThat(decodedProposal.getRoundChanges().get(0))
        .isEqualToComparingFieldByField(roundChange);
    assertThat(decodedProposal.getSignedPayload().getPayload().getProposedBlock()).isEqualTo(BLOCK);
    assertThat(decodedProposal.getSignedPayload().getPayload().getRoundIdentifier())
        .isEqualTo(payload.getRoundIdentifier());
  }
}
