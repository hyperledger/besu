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
package tech.pegasys.pantheon.consensus.ibft.validation;

import static org.assertj.core.api.Assertions.assertThat;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockHeaderFunctions;
import tech.pegasys.pantheon.consensus.ibft.IbftBlockInterface;
import tech.pegasys.pantheon.consensus.ibft.TestHelpers;
import tech.pegasys.pantheon.consensus.ibft.messagewrappers.Proposal;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.util.Collections;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProposalBlockConsistencyValidatorTest {

  private final KeyPair proposerKey = KeyPair.generate();
  private final MessageFactory proposerMessageFactory = new MessageFactory(proposerKey);
  private final long chainHeight = 2;
  private final ConsensusRoundIdentifier roundIdentifier =
      new ConsensusRoundIdentifier(chainHeight, 4);

  private final Block block =
      TestHelpers.createProposalBlock(Collections.emptyList(), roundIdentifier);
  private ProposalBlockConsistencyValidator consistencyChecker;

  @Before
  public void setup() {

    consistencyChecker = new ProposalBlockConsistencyValidator();
  }

  @Test
  public void blockDigestMisMatchWithMessageRoundFails() {
    final Proposal proposalMsg =
        proposerMessageFactory.createProposal(roundIdentifier, block, Optional.empty());

    final Block misMatchedBlock =
        IbftBlockInterface.replaceRoundInBlock(
            block,
            roundIdentifier.getRoundNumber() + 1,
            IbftBlockHeaderFunctions.forCommittedSeal());

    assertThat(
            consistencyChecker.validateProposalMatchesBlock(
                proposalMsg.getSignedPayload(), misMatchedBlock))
        .isFalse();
  }

  @Test
  public void blockDigestMatchesButRoundDiffersFails() {
    final ConsensusRoundIdentifier futureRound = TestHelpers.createFrom(roundIdentifier, 0, +1);
    final Proposal proposalMsg =
        proposerMessageFactory.createProposal(futureRound, block, Optional.empty());

    assertThat(
            consistencyChecker.validateProposalMatchesBlock(proposalMsg.getSignedPayload(), block))
        .isFalse();
  }

  @Test
  public void blockWithMismatchedNumberFails() {
    final ConsensusRoundIdentifier futureHeight = TestHelpers.createFrom(roundIdentifier, +1, 0);
    final Proposal proposalMsg =
        proposerMessageFactory.createProposal(futureHeight, block, Optional.empty());

    assertThat(
            consistencyChecker.validateProposalMatchesBlock(proposalMsg.getSignedPayload(), block))
        .isFalse();
  }
}
