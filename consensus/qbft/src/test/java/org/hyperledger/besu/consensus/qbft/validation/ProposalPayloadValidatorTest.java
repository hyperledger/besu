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
import static org.hyperledger.besu.consensus.common.bft.BftContextBuilder.setupContextWithBftExtraDataEncoder;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.common.bft.BftExtraDataCodec;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.ProposedBlockHelpers;
import org.hyperledger.besu.consensus.qbft.QbftContext;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.messagewrappers.Proposal;
import org.hyperledger.besu.consensus.qbft.payload.MessageFactory;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftBlockHeaderFunctions;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftExtraData;
import org.hyperledger.besu.consensus.qbft.pki.PkiQbftExtraDataCodec;
import org.hyperledger.besu.crypto.NodeKey;
import org.hyperledger.besu.crypto.NodeKeyUtils;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.BlockValidator.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockValidator.Result;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator;
import org.hyperledger.besu.ethereum.core.BlockDataGenerator.BlockOptions;
import org.hyperledger.besu.ethereum.core.Util;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;
import org.hyperledger.besu.pki.cms.CmsValidator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ProposalPayloadValidatorTest {

  @Mock private BlockValidator blockValidator;
  @Mock private MutableBlockchain blockChain;
  @Mock private WorldStateArchive worldStateArchive;
  @Mock private CmsValidator cmsValidator;
  private ProtocolContext protocolContext;

  private static final int CHAIN_HEIGHT = 3;
  private final ConsensusRoundIdentifier targetRound =
      new ConsensusRoundIdentifier(CHAIN_HEIGHT, 3);

  private final NodeKey nodeKey = NodeKeyUtils.generate();
  private final Address expectedProposer = Util.publicKeyToAddress(nodeKey.getPublicKey());
  private final MessageFactory messageFactory = new MessageFactory(nodeKey);
  final ConsensusRoundIdentifier roundIdentifier =
      ConsensusRoundHelpers.createFrom(targetRound, 1, 0);
  final QbftExtraDataCodec bftExtraDataCodec = new QbftExtraDataCodec();

  @Before
  public void setup() {
    protocolContext =
        new ProtocolContext(
            blockChain,
            worldStateArchive,
            setupContextWithBftExtraDataEncoder(QbftContext.class, emptyList(), bftExtraDataCodec));
  }

  @Test
  public void validationPassesWhenProposerAndRoundMatchAndBlockIsValid() {
    final ProposalPayloadValidator payloadValidator =
        new ProposalPayloadValidator(
            expectedProposer, roundIdentifier, blockValidator, protocolContext, bftExtraDataCodec);
    final Block block =
        ProposedBlockHelpers.createProposalBlock(emptyList(), roundIdentifier, bftExtraDataCodec);
    final Proposal proposal =
        messageFactory.createProposal(roundIdentifier, block, emptyList(), emptyList());

    when(blockValidator.validateAndProcessBlock(
            eq(protocolContext),
            eq(block),
            eq(HeaderValidationMode.LIGHT),
            eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));

    assertThat(payloadValidator.validate(proposal.getSignedPayload())).isTrue();
  }

  @Test
  public void validationPassesWhenBlockRoundDoesNotMatchProposalRound() {
    final ProposalPayloadValidator payloadValidator =
        new ProposalPayloadValidator(
            expectedProposer, roundIdentifier, blockValidator, protocolContext, bftExtraDataCodec);

    final Block block =
        ProposedBlockHelpers.createProposalBlock(
            emptyList(),
            ConsensusRoundHelpers.createFrom(roundIdentifier, 0, +1),
            bftExtraDataCodec);
    final Proposal proposal =
        messageFactory.createProposal(roundIdentifier, block, emptyList(), emptyList());

    when(blockValidator.validateAndProcessBlock(
            eq(protocolContext),
            eq(block),
            eq(HeaderValidationMode.LIGHT),
            eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));

    assertThat(payloadValidator.validate(proposal.getSignedPayload())).isTrue();
  }

  @Test
  public void validationFailsWhenBlockFailsValidation() {
    final ConsensusRoundIdentifier roundIdentifier =
        ConsensusRoundHelpers.createFrom(targetRound, 1, 0);

    final ProposalPayloadValidator payloadValidator =
        new ProposalPayloadValidator(
            expectedProposer, roundIdentifier, blockValidator, protocolContext, bftExtraDataCodec);
    final Block block =
        ProposedBlockHelpers.createProposalBlock(emptyList(), roundIdentifier, bftExtraDataCodec);
    final Proposal proposal =
        messageFactory.createProposal(roundIdentifier, block, emptyList(), emptyList());

    when(blockValidator.validateAndProcessBlock(
            eq(protocolContext),
            eq(block),
            eq(HeaderValidationMode.LIGHT),
            eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result("Failed"));

    assertThat(payloadValidator.validate(proposal.getSignedPayload())).isFalse();
  }

  @Test
  public void validationFailsWhenExpectedProposerDoesNotMatchPayloadsAuthor() {
    final ProposalPayloadValidator payloadValidator =
        new ProposalPayloadValidator(
            Address.fromHexString("0x1"),
            roundIdentifier,
            blockValidator,
            protocolContext,
            bftExtraDataCodec);
    final Block block = ProposedBlockHelpers.createProposalBlock(emptyList(), roundIdentifier);
    final Proposal proposal =
        messageFactory.createProposal(roundIdentifier, block, emptyList(), emptyList());

    assertThat(payloadValidator.validate(proposal.getSignedPayload())).isFalse();
    verifyNoMoreInteractions(blockValidator);
  }

  @Test
  public void validationFailsWhenMessageMismatchesExpectedRound() {
    final ProposalPayloadValidator payloadValidator =
        new ProposalPayloadValidator(
            expectedProposer, roundIdentifier, blockValidator, protocolContext, bftExtraDataCodec);

    final Block block = ProposedBlockHelpers.createProposalBlock(emptyList(), roundIdentifier);
    final Proposal proposal =
        messageFactory.createProposal(
            ConsensusRoundHelpers.createFrom(roundIdentifier, 0, +1),
            block,
            emptyList(),
            emptyList());

    assertThat(payloadValidator.validate(proposal.getSignedPayload())).isFalse();
    verifyNoMoreInteractions(blockValidator);
  }

  @Test
  public void validationFailsWhenMessageMismatchesExpectedHeight() {
    final ProposalPayloadValidator payloadValidator =
        new ProposalPayloadValidator(
            expectedProposer, roundIdentifier, blockValidator, protocolContext, bftExtraDataCodec);

    final Block block = ProposedBlockHelpers.createProposalBlock(emptyList(), roundIdentifier);
    final Proposal proposal =
        messageFactory.createProposal(
            ConsensusRoundHelpers.createFrom(roundIdentifier, +1, 0),
            block,
            emptyList(),
            emptyList());

    assertThat(payloadValidator.validate(proposal.getSignedPayload())).isFalse();
    verifyNoMoreInteractions(blockValidator);
  }

  @Test
  public void validationFailsForBlockWithIncorrectHeight() {
    final ProposalPayloadValidator payloadValidator =
        new ProposalPayloadValidator(
            expectedProposer, roundIdentifier, blockValidator, protocolContext, bftExtraDataCodec);
    final Block block =
        ProposedBlockHelpers.createProposalBlock(
            emptyList(),
            ConsensusRoundHelpers.createFrom(roundIdentifier, +1, 0),
            bftExtraDataCodec);
    final Proposal proposal =
        messageFactory.createProposal(roundIdentifier, block, emptyList(), emptyList());

    when(blockValidator.validateAndProcessBlock(
            eq(protocolContext),
            eq(block),
            eq(HeaderValidationMode.LIGHT),
            eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));

    assertThat(payloadValidator.validate(proposal.getSignedPayload())).isFalse();
  }

  @Test
  public void validationForCmsFailsWhenCmsFailsValidation() {
    final PkiQbftExtraDataCodec pkiQbftExtraDataCodec = new PkiQbftExtraDataCodec();
    final QbftContext qbftContext =
        setupContextWithBftExtraDataEncoder(QbftContext.class, emptyList(), pkiQbftExtraDataCodec);
    final Bytes cms = Bytes.fromHexStringLenient("0x1");
    final ProtocolContext protocolContext =
        new ProtocolContext(blockChain, worldStateArchive, qbftContext);

    final ProposalPayloadValidator payloadValidator =
        new ProposalPayloadValidator(
            expectedProposer,
            roundIdentifier,
            blockValidator,
            protocolContext,
            pkiQbftExtraDataCodec,
            Optional.of(cmsValidator));
    final Block block =
        createPkiProposalBlock(emptyList(), roundIdentifier, pkiQbftExtraDataCodec, cms);
    final Proposal proposal =
        messageFactory.createProposal(roundIdentifier, block, emptyList(), emptyList());
    final Hash hashWithoutCms =
        PkiQbftBlockHeaderFunctions.forCmsSignature(pkiQbftExtraDataCodec).hash(block.getHeader());

    when(blockValidator.validateAndProcessBlock(
            eq(protocolContext),
            eq(block),
            eq(HeaderValidationMode.LIGHT),
            eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));
    when(cmsValidator.validate(eq(cms), eq(hashWithoutCms))).thenReturn(false);

    assertThat(payloadValidator.validate(proposal.getSignedPayload())).isFalse();
  }

  @Test
  public void validationForCmsPassesWhenCmsIsValid() {
    final PkiQbftExtraDataCodec pkiQbftExtraDataCodec = new PkiQbftExtraDataCodec();
    final QbftContext qbftContext =
        setupContextWithBftExtraDataEncoder(QbftContext.class, emptyList(), pkiQbftExtraDataCodec);
    final Bytes cms = Bytes.fromHexStringLenient("0x1");
    final ProtocolContext protocolContext =
        new ProtocolContext(blockChain, worldStateArchive, qbftContext);

    final ProposalPayloadValidator payloadValidator =
        new ProposalPayloadValidator(
            expectedProposer,
            roundIdentifier,
            blockValidator,
            protocolContext,
            pkiQbftExtraDataCodec,
            Optional.of(cmsValidator));
    final Block block =
        createPkiProposalBlock(emptyList(), roundIdentifier, pkiQbftExtraDataCodec, cms);
    final Proposal proposal =
        messageFactory.createProposal(roundIdentifier, block, emptyList(), emptyList());
    final Hash hashWithoutCms =
        PkiQbftBlockHeaderFunctions.forCmsSignature(pkiQbftExtraDataCodec).hash(block.getHeader());

    when(blockValidator.validateAndProcessBlock(
            eq(protocolContext),
            eq(block),
            eq(HeaderValidationMode.LIGHT),
            eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));
    when(cmsValidator.validate(eq(cms), eq(hashWithoutCms))).thenReturn(true);

    assertThat(payloadValidator.validate(proposal.getSignedPayload())).isTrue();
  }

  public static Block createPkiProposalBlock(
      final List<Address> validators,
      final ConsensusRoundIdentifier roundId,
      final BftExtraDataCodec bftExtraDataCodec,
      final Bytes cms) {
    final Bytes extraData =
        bftExtraDataCodec.encode(
            new PkiQbftExtraData(
                Bytes.wrap(new byte[32]),
                Collections.emptyList(),
                Optional.empty(),
                roundId.getRoundNumber(),
                validators,
                cms));
    final BlockOptions blockOptions =
        BlockOptions.create()
            .setExtraData(extraData)
            .setBlockNumber(roundId.getSequenceNumber())
            .setBlockHeaderFunctions(BftBlockHeaderFunctions.forCommittedSeal(bftExtraDataCodec))
            .hasOmmers(false)
            .hasTransactions(false);

    if (validators.size() > 0) {
      blockOptions.setCoinbase(validators.get(0));
    }
    return new BlockDataGenerator().block(blockOptions);
  }
}
