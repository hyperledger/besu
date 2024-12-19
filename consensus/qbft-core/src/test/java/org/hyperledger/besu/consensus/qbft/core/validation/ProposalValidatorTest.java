/*
 * Copyright contributors to Hyperledger Besu.
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
package org.hyperledger.besu.consensus.qbft.core.validation;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.common.bft.BftContextBuilder.setupContextWithBftExtraDataEncoder;
import static org.hyperledger.besu.consensus.qbft.core.validation.ValidationTestHelpers.createEmptyRoundChangePayloads;
import static org.hyperledger.besu.consensus.qbft.core.validation.ValidationTestHelpers.createPreparePayloads;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftContext;
import org.hyperledger.besu.consensus.common.bft.BftExtraData;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.BftProtocolSchedule;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.ProposedBlockHelpers;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Prepare;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.core.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.core.payload.PreparePayload;
import org.hyperledger.besu.consensus.qbft.core.payload.PreparedRoundMetadata;
import org.hyperledger.besu.consensus.qbft.core.payload.RoundChangePayload;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockProcessingResult;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProposalValidatorTest {

  private enum ROUND_ID {
    ZERO,
    ONE
  }

  private static class RoundSpecificItems {

    public final Block block;
    public final ConsensusRoundIdentifier roundIdentifier;
    public final ProposalValidator messageValidator;

    public RoundSpecificItems(
        final Block block,
        final ConsensusRoundIdentifier roundIdentifier,
        final ProposalValidator messageValidator) {
      this.block = block;
      this.roundIdentifier = roundIdentifier;
      this.messageValidator = messageValidator;
    }
  }

  private static final int VALIDATOR_COUNT = 4;
  private static final QbftNodeList validators = QbftNodeList.createNodes(VALIDATOR_COUNT);
  @Mock private BlockValidator blockValidator;
  @Mock private MutableBlockchain blockChain;
  @Mock private WorldStateArchive worldStateArchive;
  @Mock private BftProtocolSchedule protocolSchedule;
  @Mock private ProtocolSpec protocolSpec;
  @Mock private BftExtraDataCodec bftExtraDataCodec;
  private ProtocolContext protocolContext;

  private final Map<ROUND_ID, RoundSpecificItems> roundItems = new HashMap<>();

  @BeforeEach
  public void setup() {
    protocolContext =
        new ProtocolContext(
            blockChain,
            worldStateArchive,
            setupContextWithBftExtraDataEncoder(BftContext.class, emptyList(), bftExtraDataCodec),
            new BadBlockManager());

    // typically tests require the blockValidation to be successful
    when(blockValidator.validateAndProcessBlock(
            eq(protocolContext),
            any(),
            eq(HeaderValidationMode.LIGHT),
            eq(HeaderValidationMode.FULL),
            eq(false)))
        .thenReturn(new BlockProcessingResult(Optional.empty()));

    when(protocolSchedule.getByBlockHeader(any())).thenReturn(protocolSpec);

    when(protocolSpec.getBlockValidator()).thenReturn(blockValidator);

    when(bftExtraDataCodec.encode(any())).thenReturn(Bytes.EMPTY);
    roundItems.put(ROUND_ID.ZERO, createRoundSpecificItems(0));
    roundItems.put(ROUND_ID.ONE, createRoundSpecificItems(1));
  }

  private RoundSpecificItems createRoundSpecificItems(final int roundNumber) {
    final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(1, roundNumber);

    return new RoundSpecificItems(
        ProposedBlockHelpers.createProposalBlock(
            validators.getNodeAddresses(), roundIdentifier, bftExtraDataCodec),
        roundIdentifier,
        new ProposalValidator(
            protocolContext,
            protocolSchedule,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            validators.getNodeAddresses(),
            roundIdentifier,
            validators.getNode(0).getAddress(),
            bftExtraDataCodec));
  }

  // NOTE: tests herein assume the ProposalPayloadValidator works as expected, so other than
  // a bad-block test (to ensure invocation) - no tests exist which fail based on payload data.

  private Proposal createProposal(
      final RoundSpecificItems roundItem,
      final List<SignedData<RoundChangePayload>> roundChanges,
      final List<SignedData<PreparePayload>> prepares) {
    return validators
        .getMessageFactory(0)
        .createProposal(roundItem.roundIdentifier, roundItem.block, roundChanges, prepares);
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
    final Proposal proposal = createProposal(roundItem, emptyList(), emptyList());

    reset(blockValidator);
    when(blockValidator.validateAndProcessBlock(
            eq(protocolContext),
            any(),
            eq(HeaderValidationMode.LIGHT),
            eq(HeaderValidationMode.FULL),
            eq(false)))
        .thenReturn(new BlockProcessingResult("Failed"));

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationFailsIfRoundZeroHasNonEmptyPrepares() {
    when(bftExtraDataCodec.encodeWithoutCommitSeals(any())).thenReturn(Bytes.EMPTY);
    when(bftExtraDataCodec.decode(any()))
        .thenReturn(new BftExtraData(Bytes.EMPTY, emptyList(), Optional.empty(), 0, emptyList()));

    final Prepare prepareMsg =
        validators
            .getMessageFactory(1)
            .createPrepare(
                roundItems.get(ROUND_ID.ZERO).roundIdentifier,
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
        validators
            .getMessageFactory(1)
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
            roundItem.roundIdentifier,
            validators.getNode(0),
            validators.getNode(1),
            validators.getNode(2));

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(roundItem.roundIdentifier, roundItem.block, roundChanges, emptyList());

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

  @Test
  public void validationFailsIfPiggybackedRoundChangePayloadHasDuplicatedAuthors() {
    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);

    final List<SignedData<RoundChangePayload>> roundChanges =
        createEmptyRoundChangePayloads(
            roundItem.roundIdentifier,
            validators.getNode(0),
            validators.getNode(1),
            validators.getNode(1));

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(roundItem.roundIdentifier, roundItem.block, roundChanges, emptyList());

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationFailsIfRoundChangePayloadsTargetADifferentRoundToProposal() {
    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);

    final List<SignedData<RoundChangePayload>> roundChanges =
        createEmptyRoundChangePayloads(
            ConsensusRoundHelpers.createFrom(roundItem.roundIdentifier, 0, +1),
            validators.getNode(0),
            validators.getNode(1),
            validators.getNode(2));

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(roundItem.roundIdentifier, roundItem.block, roundChanges, emptyList());

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationFailsIfRoundChangePayloadsTargetADifferentHeightToProposal() {
    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);

    final List<SignedData<RoundChangePayload>> roundChanges =
        createEmptyRoundChangePayloads(
            ConsensusRoundHelpers.createFrom(roundItem.roundIdentifier, +1, 0),
            validators.getNode(0),
            validators.getNode(1),
            validators.getNode(2));

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(roundItem.roundIdentifier, roundItem.block, roundChanges, emptyList());

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationFailsIfBlockHashInLatestPreparedMetadataDoesNotMatchProposedBlock() {
    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);
    final List<SignedData<RoundChangePayload>> roundChanges =
        createEmptyRoundChangePayloads(
            roundItem.roundIdentifier, validators.getNode(0), validators.getNode(1));

    final RoundChangePayload illegalPayload =
        new RoundChangePayload(
            roundItem.roundIdentifier,
            Optional.of(
                new PreparedRoundMetadata(
                    Hash.fromHexStringLenient("0x1"),
                    roundItems.get(ROUND_ID.ZERO).roundIdentifier.getRoundNumber())));

    final SignedData<RoundChangePayload> preparedRoundChange =
        SignedData.create(
            illegalPayload,
            validators.getNode(2).getNodeKey().sign(illegalPayload.hashForSignature()));

    roundChanges.add(preparedRoundChange);

    when(bftExtraDataCodec.encodeWithoutCommitSeals(any())).thenReturn(Bytes.EMPTY);
    when(bftExtraDataCodec.decode(any()))
        .thenReturn(new BftExtraData(Bytes.EMPTY, emptyList(), Optional.empty(), 0, emptyList()));

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(
                roundItem.roundIdentifier,
                roundItem.block,
                roundChanges,
                createPreparePayloads(
                    roundItems.get(ROUND_ID.ZERO).roundIdentifier,
                    Hash.fromHexStringLenient("0x1"),
                    validators.getNode(0),
                    validators.getNode(1),
                    validators.getNode(2)));

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  // Piggybacked RoundChange tests
  @Test
  public void validationFailsIfPreparesAreNonEmptyButNoRoundChangeHasPreparedMetadata() {
    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);
    final List<SignedData<RoundChangePayload>> roundChanges =
        createEmptyRoundChangePayloads(
            roundItem.roundIdentifier,
            validators.getNode(0),
            validators.getNode(1),
            validators.getNode(2));

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(
                roundItem.roundIdentifier,
                roundItem.block,
                roundChanges,
                createPreparePayloads(
                    roundItems.get(ROUND_ID.ZERO).roundIdentifier,
                    Hash.fromHexStringLenient("0x1"),
                    validators.getNode(0)));

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationFailsIfPiggybackedPreparePayloadIsFromNonValidator() {
    when(bftExtraDataCodec.encodeWithoutCommitSeals(any())).thenReturn(Bytes.EMPTY);
    when(bftExtraDataCodec.decode(any()))
        .thenReturn(new BftExtraData(Bytes.EMPTY, emptyList(), Optional.empty(), 0, emptyList()));

    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);
    final List<SignedData<RoundChangePayload>> roundChanges = createPreparedRoundZeroRoundChanges();

    final QbftNode nonValidator = QbftNode.create();
    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(
                roundItem.roundIdentifier,
                roundItem.block,
                roundChanges,
                createPreparePayloads(
                    roundItems.get(ROUND_ID.ZERO).roundIdentifier,
                    roundItems.get(ROUND_ID.ZERO).block.getHash(),
                    validators.getNode(0),
                    validators.getNode(1),
                    nonValidator));

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationFailsIfPiggybackedPreparePayloadHasDuplicatedAuthors() {
    when(bftExtraDataCodec.encodeWithoutCommitSeals(any())).thenReturn(Bytes.EMPTY);
    when(bftExtraDataCodec.decode(any()))
        .thenReturn(new BftExtraData(Bytes.EMPTY, emptyList(), Optional.empty(), 0, emptyList()));

    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);
    final List<SignedData<RoundChangePayload>> roundChanges = createPreparedRoundZeroRoundChanges();

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(
                roundItem.roundIdentifier,
                roundItem.block,
                roundChanges,
                createPreparePayloads(
                    roundItems.get(ROUND_ID.ZERO).roundIdentifier,
                    roundItems.get(ROUND_ID.ZERO).block.getHash(),
                    validators.getNode(0),
                    validators.getNode(1),
                    validators.getNode(1)));

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationFailsIfInsufficientPiggybackedPreparePayloads() {
    when(bftExtraDataCodec.encodeWithoutCommitSeals(any())).thenReturn(Bytes.EMPTY);
    when(bftExtraDataCodec.decode(any()))
        .thenReturn(new BftExtraData(Bytes.EMPTY, emptyList(), Optional.empty(), 0, emptyList()));

    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);
    final List<SignedData<RoundChangePayload>> roundChanges = createPreparedRoundZeroRoundChanges();

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(
                roundItem.roundIdentifier,
                roundItem.block,
                roundChanges,
                createPreparePayloads(
                    roundItems.get(ROUND_ID.ZERO).roundIdentifier,
                    roundItems.get(ROUND_ID.ZERO).block.getHash(),
                    validators.getNode(0),
                    validators.getNode(1)));

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationFailsIfPreparePayloadsDoNotMatchMetadataInRoundChanges() {
    when(bftExtraDataCodec.encodeWithoutCommitSeals(any())).thenReturn(Bytes.EMPTY);
    when(bftExtraDataCodec.decode(any()))
        .thenReturn(new BftExtraData(Bytes.EMPTY, emptyList(), Optional.empty(), 0, emptyList()));

    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);
    final List<SignedData<RoundChangePayload>> roundChanges = createPreparedRoundZeroRoundChanges();

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(
                roundItem.roundIdentifier,
                roundItem.block,
                roundChanges,
                createPreparePayloads(
                    roundItems.get(ROUND_ID.ONE).roundIdentifier,
                    roundItems.get(ROUND_ID.ZERO).block.getHash(),
                    validators.getNode(0),
                    validators.getNode(1),
                    validators.getNode(2)));

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationFailsIfPreparePayloadsDoNotMatchBlockHashInRoundChanges() {
    when(bftExtraDataCodec.encodeWithoutCommitSeals(any())).thenReturn(Bytes.EMPTY);
    when(bftExtraDataCodec.decode(any()))
        .thenReturn(new BftExtraData(Bytes.EMPTY, emptyList(), Optional.empty(), 0, emptyList()));

    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);
    final List<SignedData<RoundChangePayload>> roundChanges = createPreparedRoundZeroRoundChanges();

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(
                roundItem.roundIdentifier,
                roundItem.block,
                roundChanges,
                createPreparePayloads(
                    roundItems.get(ROUND_ID.ZERO).roundIdentifier,
                    Hash.fromHexStringLenient("0x1"),
                    validators.getNode(0),
                    validators.getNode(1),
                    validators.getNode(2)));

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  @Test
  public void validationFailsIfTwoRoundChangesArePreparedOnSameRoundDifferentBlock() {
    when(bftExtraDataCodec.encodeWithoutCommitSeals(any())).thenReturn(Bytes.EMPTY);
    when(bftExtraDataCodec.decode(any()))
        .thenReturn(new BftExtraData(Bytes.EMPTY, emptyList(), Optional.empty(), 0, emptyList()));

    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);
    final List<SignedData<RoundChangePayload>> roundChanges = createPreparedRoundZeroRoundChanges();

    final RoundChangePayload illegalPreparedRoundChangePayload =
        new RoundChangePayload(
            roundItem.roundIdentifier,
            Optional.of(
                new PreparedRoundMetadata(
                    roundItems.get(ROUND_ID.ONE).block.getHash(),
                    roundItems.get(ROUND_ID.ZERO).roundIdentifier.getRoundNumber())));

    final SignedData<RoundChangePayload> preparedRoundChange =
        SignedData.create(
            illegalPreparedRoundChangePayload,
            validators
                .getNode(3)
                .getNodeKey()
                .sign(illegalPreparedRoundChangePayload.hashForSignature()));

    roundChanges.add(preparedRoundChange);

    final Proposal proposal =
        validators
            .getMessageFactory(0)
            .createProposal(
                roundItem.roundIdentifier,
                roundItem.block,
                roundChanges,
                createPreparePayloads(
                    roundItems.get(ROUND_ID.ZERO).roundIdentifier,
                    Hash.fromHexStringLenient("0x1"),
                    validators.getNode(0),
                    validators.getNode(1),
                    validators.getNode(2)));

    assertThat(roundItem.messageValidator.validate(proposal)).isFalse();
  }

  private List<SignedData<RoundChangePayload>> createPreparedRoundZeroRoundChanges() {
    final RoundSpecificItems roundItem = roundItems.get(ROUND_ID.ONE);
    final List<SignedData<RoundChangePayload>> roundChanges =
        createEmptyRoundChangePayloads(
            roundItem.roundIdentifier, validators.getNode(0), validators.getNode(1));

    final RoundChangePayload preparedRoundChangePayload =
        new RoundChangePayload(
            roundItem.roundIdentifier,
            Optional.of(
                new PreparedRoundMetadata(
                    roundItems.get(ROUND_ID.ZERO).block.getHash(),
                    roundItems.get(ROUND_ID.ZERO).roundIdentifier.getRoundNumber())));

    final SignedData<RoundChangePayload> preparedRoundChange =
        SignedData.create(
            preparedRoundChangePayload,
            validators.getNode(2).getNodeKey().sign(preparedRoundChangePayload.hashForSignature()));

    roundChanges.add(preparedRoundChange);

    return roundChanges;
  }
}
