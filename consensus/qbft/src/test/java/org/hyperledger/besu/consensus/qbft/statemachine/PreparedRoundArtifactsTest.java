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
package org.hyperledger.besu.consensus.qbft.statemachine;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;

import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockBody;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.BlockHeaderTestFixture;

import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;

public class PreparedRoundArtifactsTest {

  private final NodeKey nodeKey = NodeKeyUtils.generate();
  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 0);
  private final MessageFactory messageFactory = new MessageFactory(nodeKey);

  private Block proposedBlock;
  private BftExtraData proposedExtraData;

  @Before
  public void setup() {
    proposedExtraData =
        new BftExtraData(Bytes.wrap(new byte[32]), emptyList(), Optional.empty(), 0, emptyList());
    final BlockHeaderTestFixture headerTestFixture = new BlockHeaderTestFixture();
    headerTestFixture.extraData(proposedExtraData.encode());
    headerTestFixture.number(1);

    final BlockHeader header = headerTestFixture.buildHeader();
    proposedBlock = new Block(header, new BlockBody(emptyList(), emptyList()));
  }

  @Test
  public void blockReportedIsThatTakenFromProposal() {
    final Proposal proposal =
        messageFactory.createProposal(roundIdentifier, proposedBlock, emptyList(), emptyList());

    final PreparedRoundArtifacts preparedRoundArtifacts =
        new PreparedRoundArtifacts(proposal, emptyList());

    assertThat(preparedRoundArtifacts.getBlock()).isEqualTo(proposedBlock);
    assertThat(preparedRoundArtifacts.getPreparedCertificate().getPrepares()).isEmpty();
  }

  @Test
  public void prepareMessagesInConstructorAreInCertificate() {
    final Proposal proposal =
        messageFactory.createProposal(roundIdentifier, proposedBlock, emptyList(), emptyList());
    final Prepare prepare =
        messageFactory.createPrepare(roundIdentifier, proposal.getBlock().getHash());

    final PreparedRoundArtifacts preparedRoundArtifacts =
        new PreparedRoundArtifacts(proposal, List.of(prepare));

    assertThat(preparedRoundArtifacts.getBlock()).isEqualTo(proposedBlock);
    assertThat(preparedRoundArtifacts.getPreparedCertificate().getPrepares())
        .containsOnly(prepare.getSignedPayload());
  }
}
