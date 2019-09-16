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
package org.hyperledger.besu.consensus.ibft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.ibft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.ibft.payload.MessageFactory;
import org.hyperledger.besu.consensus.ibft.payload.PreparedCertificate;
import org.hyperledger.besu.consensus.ibft.statemachine.PreparedRoundArtifacts;
import org.hyperledger.besu.crypto.SECP256K1.KeyPair;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.Hash;

import java.util.Optional;

import com.google.common.collect.Lists;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class IbftHelpersTest {

  @Test
  public void calculateRequiredValidatorQuorum1Validator() {
    Assertions.assertThat(IbftHelpers.calculateRequiredValidatorQuorum(1)).isEqualTo(1);
  }

  @Test
  public void calculateRequiredValidatorQuorum2Validator() {
    Assertions.assertThat(IbftHelpers.calculateRequiredValidatorQuorum(2)).isEqualTo(2);
  }

  @Test
  public void calculateRequiredValidatorQuorum3Validator() {
    Assertions.assertThat(IbftHelpers.calculateRequiredValidatorQuorum(3)).isEqualTo(2);
  }

  @Test
  public void calculateRequiredValidatorQuorum4Validator() {
    Assertions.assertThat(IbftHelpers.calculateRequiredValidatorQuorum(4)).isEqualTo(3);
  }

  @Test
  public void calculateRequiredValidatorQuorum5Validator() {
    Assertions.assertThat(IbftHelpers.calculateRequiredValidatorQuorum(5)).isEqualTo(4);
  }

  @Test
  public void calculateRequiredValidatorQuorum7Validator() {
    Assertions.assertThat(IbftHelpers.calculateRequiredValidatorQuorum(7)).isEqualTo(5);
  }

  @Test
  public void calculateRequiredValidatorQuorum10Validator() {
    Assertions.assertThat(IbftHelpers.calculateRequiredValidatorQuorum(10)).isEqualTo(7);
  }

  @Test
  public void calculateRequiredValidatorQuorum15Validator() {
    Assertions.assertThat(IbftHelpers.calculateRequiredValidatorQuorum(15)).isEqualTo(10);
  }

  @Test
  public void calculateRequiredValidatorQuorum20Validator() {
    Assertions.assertThat(IbftHelpers.calculateRequiredValidatorQuorum(20)).isEqualTo(14);
  }

  @Test
  public void latestPreparedCertificateIsExtractedFromRoundChangeCertificate() {
    // NOTE: This function does not validate that all RoundCHanges/Prepares etc. come from valid
    // sources, it is only responsible for determine which of the list or RoundChange messages
    // contains the newest
    // NOTE: This capability is tested as part of the NewRoundMessageValidationTests.
    final KeyPair proposerKey = KeyPair.generate();
    final MessageFactory proposerMessageFactory = new MessageFactory(proposerKey);
    final Block proposedBlock = mock(Block.class);
    when(proposedBlock.getHash()).thenReturn(Hash.fromHexStringLenient("1"));
    final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 4);

    final ConsensusRoundIdentifier preparedRound = TestHelpers.createFrom(roundIdentifier, 0, -1);
    final Proposal differentProposal =
        proposerMessageFactory.createProposal(preparedRound, proposedBlock, Optional.empty());

    final Optional<PreparedRoundArtifacts> latterPreparedRoundArtifacts =
        Optional.of(
            new PreparedRoundArtifacts(
                differentProposal,
                Lists.newArrayList(
                    proposerMessageFactory.createPrepare(roundIdentifier, proposedBlock.getHash()),
                    proposerMessageFactory.createPrepare(
                        roundIdentifier, proposedBlock.getHash()))));

    // An earlier PrepareCert is added to ensure the path to find the latest PrepareCert
    // is correctly followed.
    final ConsensusRoundIdentifier earlierPreparedRound =
        TestHelpers.createFrom(roundIdentifier, 0, -2);
    final Proposal earlierProposal =
        proposerMessageFactory.createProposal(
            earlierPreparedRound, proposedBlock, Optional.empty());
    final Optional<PreparedRoundArtifacts> earlierPreparedRoundArtifacts =
        Optional.of(
            new PreparedRoundArtifacts(
                earlierProposal,
                Lists.newArrayList(
                    proposerMessageFactory.createPrepare(
                        earlierPreparedRound, proposedBlock.getHash()),
                    proposerMessageFactory.createPrepare(
                        earlierPreparedRound, proposedBlock.getHash()))));

    final Optional<PreparedCertificate> newestCert =
        IbftHelpers.findLatestPreparedCertificate(
            Lists.newArrayList(
                proposerMessageFactory
                    .createRoundChange(roundIdentifier, earlierPreparedRoundArtifacts)
                    .getSignedPayload(),
                proposerMessageFactory
                    .createRoundChange(roundIdentifier, latterPreparedRoundArtifacts)
                    .getSignedPayload()));

    assertThat(newestCert.get())
        .isEqualTo(latterPreparedRoundArtifacts.get().getPreparedCertificate());
  }

  @Test
  public void allRoundChangeHaveNoPreparedReturnsEmptyOptional() {
    final KeyPair proposerKey = KeyPair.generate();
    final MessageFactory proposerMessageFactory = new MessageFactory(proposerKey);
    final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, 4);

    final Optional<PreparedCertificate> newestCert =
        IbftHelpers.findLatestPreparedCertificate(
            Lists.newArrayList(
                proposerMessageFactory
                    .createRoundChange(roundIdentifier, Optional.empty())
                    .getSignedPayload(),
                proposerMessageFactory
                    .createRoundChange(roundIdentifier, Optional.empty())
                    .getSignedPayload()));

    assertThat(newestCert).isEmpty();
  }
}
