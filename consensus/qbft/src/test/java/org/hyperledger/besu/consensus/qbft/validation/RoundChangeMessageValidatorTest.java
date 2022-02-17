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

import static com.google.common.collect.Iterables.toArray;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.consensus.common.bft.BftContextBuilder.setupContextWithBftExtraDataEncoder;
import static org.hyperledger.besu.consensus.qbft.validation.ValidationTestHelpers.createPreparePayloads;
import static org.hyperledger.besu.consensus.qbft.validation.ValidationTestHelpers.createPreparedCertificate;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.consensus.common.bft.BftHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundHelpers;
import org.hyperledger.besu.consensus.common.bft.ConsensusRoundIdentifier;
import org.hyperledger.besu.consensus.common.bft.ProposedBlockHelpers;
import org.hyperledger.besu.consensus.common.bft.payload.SignedData;
import org.hyperledger.besu.consensus.qbft.QbftContext;
import org.hyperledger.besu.consensus.qbft.QbftExtraDataCodec;
import org.hyperledger.besu.consensus.qbft.messagewrappers.RoundChange;
import org.hyperledger.besu.consensus.qbft.payload.PreparedRoundMetadata;
import org.hyperledger.besu.consensus.qbft.payload.RoundChangePayload;
import org.hyperledger.besu.consensus.qbft.statemachine.PreparedCertificate;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.BlockValidator;
import org.hyperledger.besu.ethereum.BlockValidator.BlockProcessingOutputs;
import org.hyperledger.besu.ethereum.BlockValidator.Result;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.mainnet.HeaderValidationMode;
import org.hyperledger.besu.ethereum.worldstate.WorldStateArchive;

import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RoundChangeMessageValidatorTest {

  @Mock private RoundChangePayloadValidator payloadValidator;
  @Mock private BlockValidator blockValidator;
  @Mock private MutableBlockchain blockChain;
  @Mock private WorldStateArchive worldStateArchive;
  private ProtocolContext protocolContext;

  private RoundChangeMessageValidator messageValidator;
  private static final int VALIDATOR_COUNT = 4;
  private final QbftNodeList validators = QbftNodeList.createNodes(VALIDATOR_COUNT);
  private static final int CHAIN_HEIGHT = 3;
  private final ConsensusRoundIdentifier targetRound =
      new ConsensusRoundIdentifier(CHAIN_HEIGHT, 3);
  private final ConsensusRoundIdentifier roundIdentifier =
      ConsensusRoundHelpers.createFrom(targetRound, 0, -1);
  private final QbftExtraDataCodec bftExtraDataEncoder = new QbftExtraDataCodec();

  @Before
  public void setup() {
    protocolContext =
        new ProtocolContext(
            blockChain,
            worldStateArchive,
            setupContextWithBftExtraDataEncoder(
                QbftContext.class, emptyList(), bftExtraDataEncoder));
  }

  @Test
  public void roundChangeWithNoPiggyBackedDataIsValid() {
    when(payloadValidator.validate(any())).thenReturn(true);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    for (int i = 0; i < VALIDATOR_COUNT; i++) {
      final RoundChange message =
          validators.getMessageFactory(i).createRoundChange(targetRound, Optional.empty());
      assertThat(messageValidator.validate(message)).isTrue();
    }
  }

  @Test
  public void roundChangeWithValidPiggyBackDataIsValid() {
    when(blockValidator.validateAndProcessBlock(
            any(), any(), eq(HeaderValidationMode.LIGHT), eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));
    when(payloadValidator.validate(any())).thenReturn(true);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    final Block block =
        ProposedBlockHelpers.createProposalBlock(
            Collections.emptyList(), roundIdentifier, bftExtraDataEncoder);
    final PreparedCertificate prepCert =
        createPreparedCertificate(
            block, roundIdentifier, toArray(validators.getNodes(), QbftNode.class));

    final RoundChange message =
        validators.getMessageFactory(0).createRoundChange(targetRound, Optional.of(prepCert));
    assertThat(messageValidator.validate(message)).isTrue();
  }

  @Test
  public void roundChangeWithBlockRoundMismatchingPreparesIsValid() {
    when(blockValidator.validateAndProcessBlock(
            any(), any(), eq(HeaderValidationMode.LIGHT), eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));
    when(payloadValidator.validate(any())).thenReturn(true);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    final Block block =
        ProposedBlockHelpers.createProposalBlock(
            Collections.emptyList(), roundIdentifier, bftExtraDataEncoder);
    final PreparedCertificate prepCert =
        createPreparedCertificate(
            block,
            ConsensusRoundHelpers.createFrom(roundIdentifier, 0, +1),
            toArray(validators.getNodes(), QbftNode.class));

    final RoundChange message =
        validators.getMessageFactory(0).createRoundChange(targetRound, Optional.of(prepCert));
    assertThat(messageValidator.validate(message)).isTrue();
  }

  @Test
  public void blockIsInvalidFailsValidation() {
    when(blockValidator.validateAndProcessBlock(
            any(), any(), eq(HeaderValidationMode.LIGHT), eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result("Failed"));
    when(payloadValidator.validate(any())).thenReturn(true);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    final Block block =
        ProposedBlockHelpers.createProposalBlock(Collections.emptyList(), roundIdentifier);
    final PreparedCertificate prepCert =
        createPreparedCertificate(
            block, roundIdentifier, toArray(validators.getNodes(), QbftNode.class));

    final RoundChange message =
        validators.getMessageFactory(0).createRoundChange(targetRound, Optional.of(prepCert));
    assertThat(messageValidator.validate(message)).isFalse();
  }

  @Test
  public void invalidEmbeddedPayloadFailsValidation() {
    when(payloadValidator.validate(any())).thenReturn(false);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    final RoundChange message =
        validators.getMessageFactory(0).createRoundChange(targetRound, Optional.empty());
    assertThat(messageValidator.validate(message)).isFalse();
  }

  @Test
  public void insufficientPiggyBackedPrepareMessagesIsInvalid() {
    when(blockValidator.validateAndProcessBlock(
            any(), any(), eq(HeaderValidationMode.LIGHT), eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));
    when(payloadValidator.validate(any())).thenReturn(true);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    final Block block =
        ProposedBlockHelpers.createProposalBlock(
            Collections.emptyList(), roundIdentifier, bftExtraDataEncoder);
    final PreparedCertificate prepCert =
        createPreparedCertificate(
            block, roundIdentifier, validators.getNode(0), validators.getNode(1));

    final RoundChange message =
        validators.getMessageFactory(0).createRoundChange(targetRound, Optional.of(prepCert));
    assertThat(messageValidator.validate(message)).isFalse();
  }

  @Test
  public void prepareFromNonValidatorFails() {
    when(blockValidator.validateAndProcessBlock(
            any(), any(), eq(HeaderValidationMode.LIGHT), eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));
    when(payloadValidator.validate(any())).thenReturn(true);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    final QbftNode nonValidator = QbftNode.create();

    final Block block =
        ProposedBlockHelpers.createProposalBlock(
            Collections.emptyList(), roundIdentifier, bftExtraDataEncoder);
    final PreparedCertificate prepCert =
        createPreparedCertificate(
            block, roundIdentifier, validators.getNode(0), validators.getNode(1), nonValidator);

    final RoundChange message =
        validators.getMessageFactory(0).createRoundChange(targetRound, Optional.of(prepCert));
    assertThat(messageValidator.validate(message)).isFalse();
  }

  @Test
  public void validationFailsIfPreparedMetadataContainsDifferentRoundToBlock() {
    when(blockValidator.validateAndProcessBlock(
            any(), any(), eq(HeaderValidationMode.LIGHT), eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));
    when(payloadValidator.validate(any())).thenReturn(true);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    final Block block =
        ProposedBlockHelpers.createProposalBlock(
            Collections.emptyList(), roundIdentifier, bftExtraDataEncoder);
    final PreparedCertificate prepCert =
        new PreparedCertificate(
            block,
            validators.getNodes().stream()
                .map(
                    n ->
                        n.getMessageFactory()
                            .createPrepare(roundIdentifier, block.getHash())
                            .getSignedPayload())
                .collect(Collectors.toList()),
            roundIdentifier.getRoundNumber() - 1);

    final RoundChange message =
        validators.getMessageFactory(0).createRoundChange(targetRound, Optional.of(prepCert));
    assertThat(messageValidator.validate(message)).isFalse();
  }

  @Test
  public void validationFailsIfPreparesContainsDifferentRoundToBlock() {
    when(blockValidator.validateAndProcessBlock(
            any(), any(), eq(HeaderValidationMode.LIGHT), eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));
    when(payloadValidator.validate(any())).thenReturn(true);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    final Block block =
        ProposedBlockHelpers.createProposalBlock(
            Collections.emptyList(), roundIdentifier, bftExtraDataEncoder);
    final PreparedCertificate prepCert =
        new PreparedCertificate(
            block,
            validators.getNodes().stream()
                .map(
                    n ->
                        n.getMessageFactory()
                            .createPrepare(
                                ConsensusRoundHelpers.createFrom(roundIdentifier, 0, -1),
                                block.getHash())
                            .getSignedPayload())
                .collect(Collectors.toList()),
            roundIdentifier.getRoundNumber());

    final RoundChange message =
        validators.getMessageFactory(0).createRoundChange(targetRound, Optional.of(prepCert));
    assertThat(messageValidator.validate(message)).isFalse();
  }

  @Test
  public void validationFailsIfPreparesContainsWrongHeight() {
    when(blockValidator.validateAndProcessBlock(
            any(), any(), eq(HeaderValidationMode.LIGHT), eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));
    when(payloadValidator.validate(any())).thenReturn(true);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    final Block block =
        ProposedBlockHelpers.createProposalBlock(
            Collections.emptyList(), roundIdentifier, bftExtraDataEncoder);
    final PreparedCertificate prepCert =
        new PreparedCertificate(
            block,
            validators.getNodes().stream()
                .map(
                    n ->
                        n.getMessageFactory()
                            .createPrepare(
                                ConsensusRoundHelpers.createFrom(roundIdentifier, +1, 0),
                                block.getHash())
                            .getSignedPayload())
                .collect(Collectors.toList()),
            roundIdentifier.getRoundNumber());

    final RoundChange message =
        validators.getMessageFactory(0).createRoundChange(targetRound, Optional.of(prepCert));
    assertThat(messageValidator.validate(message)).isFalse();
  }

  @Test
  public void validationFailsIfPreparesHaveDuplicateAuthors() {
    when(blockValidator.validateAndProcessBlock(
            any(), any(), eq(HeaderValidationMode.LIGHT), eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));
    when(payloadValidator.validate(any())).thenReturn(true);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    final Block block =
        ProposedBlockHelpers.createProposalBlock(
            Collections.emptyList(), roundIdentifier, bftExtraDataEncoder);
    final PreparedCertificate prepCert =
        createPreparedCertificate(
            block,
            roundIdentifier,
            validators.getNode(0),
            validators.getNode(1),
            validators.getNode(1));

    final RoundChange message =
        validators.getMessageFactory(0).createRoundChange(targetRound, Optional.of(prepCert));

    assertThat(messageValidator.validate(message)).isFalse();
  }

  @Test
  public void validationFailsIfBlockExistsButNotPreparedMetadata() {
    when(blockValidator.validateAndProcessBlock(
            any(), any(), eq(HeaderValidationMode.LIGHT), eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));
    when(payloadValidator.validate(any())).thenReturn(true);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    final Block block =
        ProposedBlockHelpers.createProposalBlock(Collections.emptyList(), roundIdentifier);

    final RoundChangePayload payload = new RoundChangePayload(targetRound, Optional.empty());
    final SECPSignature signature =
        validators.getNode(0).getNodeKey().sign(payload.hashForSignature());

    final RoundChange message =
        new RoundChange(SignedData.create(payload, signature), Optional.of(block), emptyList());

    assertThat(messageValidator.validate(message)).isFalse();
  }

  @Test
  public void validationFailsIfBlockHashDoesNotMatchPreparedMetadata() {
    when(blockValidator.validateAndProcessBlock(
            any(), any(), eq(HeaderValidationMode.LIGHT), eq(HeaderValidationMode.FULL)))
        .thenReturn(new Result(new BlockProcessingOutputs(null, null)));
    when(payloadValidator.validate(any())).thenReturn(true);
    messageValidator =
        new RoundChangeMessageValidator(
            payloadValidator,
            BftHelpers.calculateRequiredValidatorQuorum(VALIDATOR_COUNT),
            CHAIN_HEIGHT,
            validators.getNodeAddresses(),
            blockValidator,
            protocolContext);

    final Block block =
        ProposedBlockHelpers.createProposalBlock(Collections.emptyList(), roundIdentifier);

    final RoundChangePayload payload =
        new RoundChangePayload(
            targetRound,
            Optional.of(
                new PreparedRoundMetadata(
                    Hash.fromHexStringLenient("0x1"), roundIdentifier.getRoundNumber())));
    final SECPSignature signature =
        validators.getNode(0).getNodeKey().sign(payload.hashForSignature());

    final RoundChange message =
        new RoundChange(
            SignedData.create(payload, signature),
            Optional.of(block),
            createPreparePayloads(
                roundIdentifier, block.getHash(), toArray(validators.getNodes(), QbftNode.class)));

    assertThat(messageValidator.validate(message)).isFalse();
  }
}
