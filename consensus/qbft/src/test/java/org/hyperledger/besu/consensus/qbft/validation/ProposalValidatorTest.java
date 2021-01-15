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
package org.hyperledger.besu.consensus.qbft.validation;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.common.bft.BftContextBuilder.setupContextWithValidators;
import static org.hyperledger.besu.consensus.qbft.validation.ValidationTestHelpers.createEmptyRoundChangePayloads;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.ProposedBlockHelpers;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.BlockValidator.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProposalValidatorTest {

  private enum ROUND_ID {
    ZERO,
    ONE
  }

  private class RoundSpecificItems {

    public final Block block;
    public final ConsensusRoundIdentifier roundIdentifier;
    public final ProposalValidator messageValidator;

    public RoundSpecificItems(final Block block,
        final ConsensusRoundIdentifier roundIdentifier,
        final ProposalValidator messageValidator) {
      this.block = block;
      this.roundIdentifier = roundIdentifier;
      this.messageValidator = messageValidator;
    }
  }

  private static final int VALIDATOR_COUNT = 4;
  private static final QbftNodeList validators = QbftNodeList.createNodes(VALIDATOR_COUNT);
  @Mock
  private BlockValidator blockValidator;
  @Mock
  private MutableBlockchain blockChain;
  @Mock
  private WorldStateArchive worldStateArchive;
  private ProtocolContext protocolContext;

  private Map<ROUND_ID, RoundSpecificItems> roundItems = new HashMap<>();

  @Before
  public void setup() {
    protocolContext =
        new ProtocolContext(blockChain, worldStateArchive, setupContextWithValidators(emptyList()));

    // typically tests require the blockValidation to be successful
    when(blockValidator.validateAndProcessBlock(
        eq(protocolContext),
        any(),
        eq(HeaderValidationMode.LIGHT),
        eq(HeaderValidationMode.FULL)))
        .thenReturn(Optional.of(new BlockProcessingOutputs(null, null)));

    roundItems.put(ROUND_ID.ZERO, createRoundSpecificItems(0));
    roundItems.put(ROUND_ID.ONE, createRoundSpecificItems(1));
  }

  private RoundSpecificItems createRoundSpecificItems(final int roundNumber) {
    final ConsensusRoundIdentifier roundIdentifier =
        new ConsensusRoundIdentifier(1, roundNumber);

    return new RoundSpecificItems(
        ProposedBlockHelpers.createProposalBlock(Collections.emptyList(), roundIdentifier),
        roundIdentifier,
        new ProposalValidator(
            blockValidator,
            protocolContext,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            validators.getNodeAddresses(),
            roundIdentifier,
            validators.getNode(0).getAddress()));
  }

  // NOTE: tests herein assume the ProposalPayloadValidator works as expected, so other than
  // a bad-block test (to ensure invocation) - no tests exist which fail based on payload data.

  private Proposal createProposal(final RoundSpecificItems roundItem,
      List<SignedData<RoundChangePayload>> roundChanges,
      List<SignedData<PreparePayload>> prepares) {
    return
        validators
            .getMessageFactory(0)
            .createProposal(roundItem.roundIdentifier,
                roundItem.block, roundChanges, prepares);
  }

  @Test
  public void validationPassesForRoundZeroWithNoPiggybackedPayloads() {
    final Proposal proposal =
        createProposal(roundItems.get(ROUND_ID.ZERO), emptyList(), emptyList());
    assertThat(roundItems.get(ROUND_ID.ZERO).messageValidator.validate(proposal)).isTrue();
  }

  @Test
  public void validationFailsIfBlockIsInvalid() {
    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ZERO);
    final Proposal proposal =
        createProposal(roundItem, emptyList(), emptyList());

    when(blockValidator.validateAndProcessBlock(
        eq(protocolContext),
        any(),
        eq(HeaderValidationMode.LIGHT),
        eq(HeaderValidationMode.FULL)))
        .thenReturn(Optional.empty());

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationFailsIfRoundZeroHasNonEmptyPrepares() {
    final Prepare prepareMsg =
        validators
            .getMessageFactory(1)
            .createPrepare(roundItems.get(ROUND_ID.ZERO).roundIdentifier,
                roundItems.get(ROUND_ID.ZERO).block.getHash());

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(
                roundItems.get(ROUND_ID.ZERO).roundIdentifier,
                roundItems.get(ROUND_ID.ZERO).block,
                emptyList(),
                List.of(prepareMsg.getSignedPayload()));

    assertThat(roundItems.get(ROUND_ID.ZERO).messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationFailsIfRoundZeroHasNonEmptyRoundChanges() {
    final RoundChange roundChange =
        validators.getMessageFactory(1)
            .createRoundChange(roundItems.get(ROUND_ID.ZERO).roundIdentifier, Optional.empty());

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(
                roundItems.get(ROUND_ID.ZERO).roundIdentifier,
                roundItems.get(ROUND_ID.ZERO).block,
                List.of(roundChange.getSignedPayload()),
                emptyList());

    assertThat(roundItems.get(ROUND_ID.ZERO).messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationPassesAtNonRoundZeroIfSufficientEmptyRoundChangesMatchTargetRound() {
    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);

    final List<SignedData<RoundChangePayload>> roundChanges =
        createEmptyRoundChangePayloads(
            roundItem.roundIdentifier, validators.getNode(0), validators.getNode(1),
            validators.getNode(2));

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(roundItem.roundIdentifier,
                roundItem.block, roundChanges, emptyList());

    assertThat(roundItem.messageValidator.validate(proposal)).isTrue();
  }

  @Test
  public void validationFailsAtNonRoundZeroIfInsufficientRoundChangesExist() {
    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);

    final List<SignedData<RoundChangePayload>> roundChanges =
        createEmptyRoundChangePayloads(
            roundItem.roundIdentifier, validators.getNode(0), validators.getNode(1));

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(roundItem.roundIdentifier, roundItem.block, roundChanges, emptyList());

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  // Piggybacked RoundChange tests
  @Test
  public void validationFailsIfPiggybackedRoundChangePayloadIsFromNonValidation() {
    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);
    final QbftNode nonValidatorNode = QbftNode.create();

    final List<SignedData<RoundChangePayload>> roundChanges =
        createEmptyRoundChangePayloads(
            roundItem.roundIdentifier,
            validators.getNode(0),
            validators.getNode(1),
            nonValidatorNode);

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(roundItem.roundIdentifier, roundItem.block, roundChanges, emptyList());

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();

  }
/*
  @Test
  public void validationFailsIfPiggybackedRoundChangePayloadHasDuplicatedAuthors() {
  }

  @Test
  public void validationFailsIfInsufficientRoundChangePayloadMessages() {
  }

  @Test
  public void validationFailsIfRoundChangePayloadsTargetADifferentRoundToProposal() {
  }

  @Test
  public void validationFailsIfBlockHashInMetadataDoesNotMatchProposedBlock() {
  }

  // Piggybacked RoundChange tests
  @Test
  public void validationFailsIfPreparesAreNonEmptyButNoRoundChangeHasPreapredMetadata() {
  }

  @Test
  public void validationFailsIfPiggybackedPreparePayloadIsFromNonValidation() {
  }

  @Test
  public void validationFailsIfPiggybackedPreparePayloadHasDuplicatedAuthors() {
  }

  @Test
  public void validationFailsIfInsufficientPiggybackedPreparePayloads() {
  }

  @Test
  public void validationFailsIfPreparePayloadsDoNotMatchMetadataInRoundChanges() {
  }

  @Test
  public void validationFailsIfPreparePayloadsDoNotMatchBlockHashInRoundChanges() {
  }
 */
}
