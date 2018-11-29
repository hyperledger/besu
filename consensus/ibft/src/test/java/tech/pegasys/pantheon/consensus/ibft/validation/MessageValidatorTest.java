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
package tech.pegasys.pantheon.consensus.ibft.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftMessageFactory;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftSignedMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedCommitMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedPrePrepareMessageData;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.IbftUnsignedPrepareMessageData;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;

import java.util.List;

import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MessageValidatorTest {

  private final KeyPair proposerKey = KeyPair.generate();
  private final KeyPair validatorKey = KeyPair.generate();
  private final KeyPair nonValidatorKey = KeyPair.generate();
  private final IbftMessageFactory proposerMessageFactory = new IbftMessageFactory(proposerKey);
  private final IbftMessageFactory validatorMessageFactory = new IbftMessageFactory(validatorKey);
  private final IbftMessageFactory nonValidatorMessageFactory =
      new IbftMessageFactory(nonValidatorKey);

  private final List<Address> validators = Lists.newArrayList();

  @Mock private BlockHeaderValidator<IbftContext> headerValidator;
  private final BlockHeader parentHeader = mock(BlockHeader.class);
  private final ConsensusRoundIdentifier roundIdentifier = new ConsensusRoundIdentifier(2, 0);
  private MessageValidator validator;

  private final Block block = mock(Block.class);

  @Before
  public void setup() {
    validators.add(Util.publicKeyToAddress(proposerKey.getPublicKey()));
    validators.add(Util.publicKeyToAddress(validatorKey.getPublicKey()));

    final ProtocolContext<IbftContext> protocolContext =
        new ProtocolContext<>(
            mock(MutableBlockchain.class), mock(WorldStateArchive.class), mock(IbftContext.class));

    validator =
        new MessageValidator(
            validators,
            Util.publicKeyToAddress(proposerKey.getPublicKey()),
            roundIdentifier,
            headerValidator,
            protocolContext,
            parentHeader);

    when(block.getHash()).thenReturn(Hash.fromHexStringLenient("1"));
  }

  @Test
  public void receivingAPrepareMessageBeforePrePrepareFails() {
    final IbftSignedMessageData<IbftUnsignedPrepareMessageData> prepareMsg =
        proposerMessageFactory.createIbftSignedPrepareMessageData(roundIdentifier, Hash.ZERO);

    assertThat(validator.validatePrepareMessage(prepareMsg)).isFalse();
  }

  @Test
  public void receivingACommitMessageBeforePreprepareFails() {
    final IbftSignedMessageData<IbftUnsignedCommitMessageData> commitMsg =
        proposerMessageFactory.createIbftSignedCommitMessageData(
            roundIdentifier, Hash.ZERO, SECP256K1.sign(Hash.ZERO, proposerKey));

    assertThat(validator.validateCommmitMessage(commitMsg)).isFalse();
  }

  @Test
  public void receivingPreprepareMessageFromNonProposerFails() {
    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> preprepareMsg =
        validatorMessageFactory.createIbftSignedPrePrepareMessageData(
            roundIdentifier, mock(Block.class));

    assertThat(validator.addPreprepareMessage(preprepareMsg)).isFalse();
  }

  @Test
  public void receivingPreprepareMessageWithIllegalBlockFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(false);
    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> preprepareMsg =
        proposerMessageFactory.createIbftSignedPrePrepareMessageData(
            roundIdentifier, mock(Block.class));

    assertThat(validator.addPreprepareMessage(preprepareMsg)).isFalse();
  }

  @Test
  public void receivingPrepareFromProposerFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> preprepareMsg =
        proposerMessageFactory.createIbftSignedPrePrepareMessageData(roundIdentifier, block);

    final IbftSignedMessageData<IbftUnsignedPrepareMessageData> prepareMsg =
        proposerMessageFactory.createIbftSignedPrepareMessageData(roundIdentifier, block.getHash());

    assertThat(validator.addPreprepareMessage(preprepareMsg)).isTrue();
    assertThat(validator.validatePrepareMessage(prepareMsg)).isFalse();
  }

  @Test
  public void receivingPrepareFromNonValidatorFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);
    final Block block = mock(Block.class);
    final BlockHeader header = mock(BlockHeader.class);
    when(header.getHash()).thenReturn(Hash.fromHexStringLenient("1")); // arbitrary hash value.
    when(block.getHeader()).thenReturn(header);

    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> preprepareMsg =
        proposerMessageFactory.createIbftSignedPrePrepareMessageData(roundIdentifier, block);

    final IbftSignedMessageData<IbftUnsignedPrepareMessageData> prepareMsg =
        nonValidatorMessageFactory.createIbftSignedPrepareMessageData(
            roundIdentifier, header.getHash());

    assertThat(validator.addPreprepareMessage(preprepareMsg)).isTrue();
    assertThat(validator.validatePrepareMessage(prepareMsg)).isFalse();
  }

  @Test
  public void receivingMessagesWithDifferentRoundIdFromPreprepareFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> preprepareMsg =
        proposerMessageFactory.createIbftSignedPrePrepareMessageData(roundIdentifier, block);

    final ConsensusRoundIdentifier invalidRoundIdentifier =
        new ConsensusRoundIdentifier(
            roundIdentifier.getSequenceNumber(), roundIdentifier.getRoundNumber() + 1);
    final IbftSignedMessageData<IbftUnsignedPrepareMessageData> prepareMsg =
        validatorMessageFactory.createIbftSignedPrepareMessageData(
            invalidRoundIdentifier, block.getHash());
    final IbftSignedMessageData<IbftUnsignedCommitMessageData> commitMsg =
        validatorMessageFactory.createIbftSignedCommitMessageData(
            invalidRoundIdentifier, block.getHash(), SECP256K1.sign(block.getHash(), proposerKey));

    assertThat(validator.addPreprepareMessage(preprepareMsg)).isTrue();
    assertThat(validator.validatePrepareMessage(prepareMsg)).isFalse();
    assertThat(validator.validateCommmitMessage(commitMsg)).isFalse();
  }

  @Test
  public void receivingPrepareNonProposerValidatorWithCorrectRoundIsSuccessful() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);
    final Block block = mock(Block.class);
    final BlockHeader header = mock(BlockHeader.class);
    final Hash blockHash = Hash.fromHexStringLenient("1");
    when(header.getHash()).thenReturn(blockHash); // arbitrary hash value.
    when(block.getHeader()).thenReturn(header);
    when(block.getHash()).thenReturn(blockHash);

    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> preprepareMsg =
        proposerMessageFactory.createIbftSignedPrePrepareMessageData(roundIdentifier, block);
    final IbftSignedMessageData<IbftUnsignedPrepareMessageData> prepareMsg =
        validatorMessageFactory.createIbftSignedPrepareMessageData(
            roundIdentifier, header.getHash());

    assertThat(validator.addPreprepareMessage(preprepareMsg)).isTrue();
    assertThat(validator.validatePrepareMessage(prepareMsg)).isTrue();
  }

  @Test
  public void receivingACommitMessageWithAnInvalidCommitSealFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> preprepareMsg =
        proposerMessageFactory.createIbftSignedPrePrepareMessageData(roundIdentifier, block);

    final IbftSignedMessageData<IbftUnsignedCommitMessageData> commitMsg =
        proposerMessageFactory.createIbftSignedCommitMessageData(
            roundIdentifier, block.getHash(), SECP256K1.sign(block.getHash(), nonValidatorKey));

    assertThat(validator.addPreprepareMessage(preprepareMsg)).isTrue();
    assertThat(validator.validateCommmitMessage(commitMsg)).isFalse();
  }

  @Test
  public void commitMessageContainingValidSealFromValidatorIsSuccessful() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> preprepareMsg =
        proposerMessageFactory.createIbftSignedPrePrepareMessageData(roundIdentifier, block);

    final IbftSignedMessageData<IbftUnsignedCommitMessageData> proposerCommitMsg =
        proposerMessageFactory.createIbftSignedCommitMessageData(
            roundIdentifier, block.getHash(), SECP256K1.sign(block.getHash(), proposerKey));

    final IbftSignedMessageData<IbftUnsignedCommitMessageData> validatorCommitMsg =
        validatorMessageFactory.createIbftSignedCommitMessageData(
            roundIdentifier, block.getHash(), SECP256K1.sign(block.getHash(), validatorKey));

    assertThat(validator.addPreprepareMessage(preprepareMsg)).isTrue();
    assertThat(validator.validateCommmitMessage(proposerCommitMsg)).isTrue();
    assertThat(validator.validateCommmitMessage(validatorCommitMsg)).isTrue();
  }

  @Test
  public void subsequentPreprepareHasDifferentSenderFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> preprepareMsg =
        proposerMessageFactory.createIbftSignedPrePrepareMessageData(roundIdentifier, block);
    assertThat(validator.addPreprepareMessage(preprepareMsg)).isTrue();

    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> secondPreprepareMsg =
        validatorMessageFactory.createIbftSignedPrePrepareMessageData(roundIdentifier, block);
    assertThat(validator.addPreprepareMessage(secondPreprepareMsg)).isFalse();
  }

  @Test
  public void subsequentPreprepareHasDifferentContentFails() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> preprepareMsg =
        proposerMessageFactory.createIbftSignedPrePrepareMessageData(roundIdentifier, block);
    assertThat(validator.addPreprepareMessage(preprepareMsg)).isTrue();

    final ConsensusRoundIdentifier newRoundIdentifier = new ConsensusRoundIdentifier(3, 0);
    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> secondPreprepareMsg =
        proposerMessageFactory.createIbftSignedPrePrepareMessageData(newRoundIdentifier, block);
    assertThat(validator.addPreprepareMessage(secondPreprepareMsg)).isFalse();
  }

  @Test
  public void subsequentPreprepareHasIdenticalSenderAndContentIsSuccessful() {
    when(headerValidator.validateHeader(any(), any(), any(), any())).thenReturn(true);

    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> preprepareMsg =
        proposerMessageFactory.createIbftSignedPrePrepareMessageData(roundIdentifier, block);
    assertThat(validator.addPreprepareMessage(preprepareMsg)).isTrue();

    final IbftSignedMessageData<IbftUnsignedPrePrepareMessageData> secondPreprepareMsg =
        proposerMessageFactory.createIbftSignedPrePrepareMessageData(roundIdentifier, block);
    assertThat(validator.addPreprepareMessage(secondPreprepareMsg)).isTrue();
  }
}
