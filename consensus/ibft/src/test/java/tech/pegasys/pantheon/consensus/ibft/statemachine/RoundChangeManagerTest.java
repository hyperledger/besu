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
package tech.pegasys.pantheon.consensus.ibft.statemachine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftContext;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.ibftmessagedata.SignedData;
import tech.pegasys.pantheon.consensus.ibft.validation.MessageValidator;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.ProtocolContext;
import tech.pegasys.pantheon.ethereum.chain.MutableBlockchain;
import tech.pegasys.pantheon.ethereum.core.Address;
import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.db.WorldStateArchive;
import tech.pegasys.pantheon.ethereum.mainnet.BlockHeaderValidator;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.junit.Before;
import org.junit.Test;

public class RoundChangeManagerTest {

  private RoundChangeManager manager;

  private final KeyPair proposerKey = KeyPair.generate();
  private final KeyPair validator1Key = KeyPair.generate();
  private final KeyPair validator2Key = KeyPair.generate();
  private final KeyPair nonValidatorKey = KeyPair.generate();

  private final ConsensusRoundIdentifier ri1 = new ConsensusRoundIdentifier(2, 1);
  private final ConsensusRoundIdentifier ri2 = new ConsensusRoundIdentifier(2, 2);
  private final ConsensusRoundIdentifier ri3 = new ConsensusRoundIdentifier(2, 3);

  @Before
  public void setup() {
    List<Address> validators = Lists.newArrayList();

    validators.add(Util.publicKeyToAddress(proposerKey.getPublicKey()));
    validators.add(Util.publicKeyToAddress(validator1Key.getPublicKey()));
    validators.add(Util.publicKeyToAddress(validator2Key.getPublicKey()));

    final ProtocolContext<IbftContext> protocolContext =
        new ProtocolContext<>(
            mock(MutableBlockchain.class), mock(WorldStateArchive.class), mock(IbftContext.class));

    @SuppressWarnings("unchecked")
    BlockHeaderValidator<IbftContext> headerValidator =
        (BlockHeaderValidator<IbftContext>) mock(BlockHeaderValidator.class);
    BlockHeader parentHeader = mock(BlockHeader.class);

    Map<ConsensusRoundIdentifier, MessageValidator> messageValidators = Maps.newHashMap();

    messageValidators.put(
        ri1,
        new MessageValidator(
            validators,
            Util.publicKeyToAddress(proposerKey.getPublicKey()),
            ri1,
            headerValidator,
            protocolContext,
            parentHeader));

    messageValidators.put(
        ri2,
        new MessageValidator(
            validators,
            Util.publicKeyToAddress(validator1Key.getPublicKey()),
            ri2,
            headerValidator,
            protocolContext,
            parentHeader));

    messageValidators.put(
        ri3,
        new MessageValidator(
            validators,
            Util.publicKeyToAddress(validator2Key.getPublicKey()),
            ri3,
            headerValidator,
            protocolContext,
            parentHeader));

    manager = new RoundChangeManager(2, validators, messageValidators::get);
  }

  private SignedData<RoundChangePayload> makeRoundChangeMessage(
      final KeyPair key, final ConsensusRoundIdentifier round) {
    MessageFactory messageFactory = new MessageFactory(key);
    return messageFactory.createSignedRoundChangePayload(round, Optional.empty());
  }

  @Test
  public void rejectsInvalidRoundChangeMessage() {
    SignedData<RoundChangePayload> roundChangeData = makeRoundChangeMessage(nonValidatorKey, ri1);
    assertThat(manager.appendRoundChangeMessage(roundChangeData)).isEmpty();
    assertThat(manager.roundChangeCache.get(ri1)).isNull();
  }

  @Test
  public void acceptsValidRoundChangeMessage() {
    SignedData<RoundChangePayload> roundChangeData = makeRoundChangeMessage(proposerKey, ri2);
    assertThat(manager.appendRoundChangeMessage(roundChangeData)).isEmpty();
    assertThat(manager.roundChangeCache.get(ri2).receivedMessages.size()).isEqualTo(1);
  }

  @Test
  public void doesntAcceptDuplicateValidRoundChangeMessage() {
    SignedData<RoundChangePayload> roundChangeData = makeRoundChangeMessage(proposerKey, ri2);
    assertThat(manager.appendRoundChangeMessage(roundChangeData)).isEmpty();
    assertThat(manager.appendRoundChangeMessage(roundChangeData)).isEmpty();
    assertThat(manager.roundChangeCache.get(ri2).receivedMessages.size()).isEqualTo(1);
  }

  @Test
  public void becomesReadyAtThreshold() {
    SignedData<RoundChangePayload> roundChangeDataProposer =
        makeRoundChangeMessage(proposerKey, ri2);
    SignedData<RoundChangePayload> roundChangeDataValidator1 =
        makeRoundChangeMessage(validator1Key, ri2);
    assertThat(manager.appendRoundChangeMessage(roundChangeDataProposer))
        .isEqualTo(Optional.empty());
    assertThat(manager.appendRoundChangeMessage(roundChangeDataValidator1).isPresent()).isTrue();
  }

  @Test
  public void doesntReachReadyWhenSuppliedWithDifferentRounds() {
    SignedData<RoundChangePayload> roundChangeDataProposer =
        makeRoundChangeMessage(proposerKey, ri2);
    SignedData<RoundChangePayload> roundChangeDataValidator1 =
        makeRoundChangeMessage(validator1Key, ri3);
    assertThat(manager.appendRoundChangeMessage(roundChangeDataProposer))
        .isEqualTo(Optional.empty());
    assertThat(manager.appendRoundChangeMessage(roundChangeDataValidator1))
        .isEqualTo(Optional.empty());
    assertThat(manager.roundChangeCache.get(ri2).receivedMessages.size()).isEqualTo(1);
    assertThat(manager.roundChangeCache.get(ri3).receivedMessages.size()).isEqualTo(1);
  }

  @Test
  public void discardsRoundPreviousToThatRequested() {
    SignedData<RoundChangePayload> roundChangeDataProposer =
        makeRoundChangeMessage(proposerKey, ri1);
    SignedData<RoundChangePayload> roundChangeDataValidator1 =
        makeRoundChangeMessage(validator1Key, ri2);
    SignedData<RoundChangePayload> roundChangeDataValidator2 =
        makeRoundChangeMessage(validator2Key, ri3);
    assertThat(manager.appendRoundChangeMessage(roundChangeDataProposer))
        .isEqualTo(Optional.empty());
    assertThat(manager.appendRoundChangeMessage(roundChangeDataValidator1))
        .isEqualTo(Optional.empty());
    assertThat(manager.appendRoundChangeMessage(roundChangeDataValidator2))
        .isEqualTo(Optional.empty());
    manager.discardRoundsPriorTo(ri2);
    assertThat(manager.roundChangeCache.get(ri1)).isNull();
    assertThat(manager.roundChangeCache.get(ri2).receivedMessages.size()).isEqualTo(1);
    assertThat(manager.roundChangeCache.get(ri3).receivedMessages.size()).isEqualTo(1);
  }

  @Test
  public void stopsAcceptingMessagesAfterReady() {
    SignedData<RoundChangePayload> roundChangeDataProposer =
        makeRoundChangeMessage(proposerKey, ri2);
    SignedData<RoundChangePayload> roundChangeDataValidator1 =
        makeRoundChangeMessage(validator1Key, ri2);
    SignedData<RoundChangePayload> roundChangeDataValidator2 =
        makeRoundChangeMessage(validator2Key, ri2);
    assertThat(manager.appendRoundChangeMessage(roundChangeDataProposer))
        .isEqualTo(Optional.empty());
    assertThat(manager.appendRoundChangeMessage(roundChangeDataValidator1).isPresent()).isTrue();
    assertThat(manager.roundChangeCache.get(ri2).receivedMessages.size()).isEqualTo(2);
    assertThat(manager.appendRoundChangeMessage(roundChangeDataValidator2))
        .isEqualTo(Optional.empty());
    assertThat(manager.roundChangeCache.get(ri2).receivedMessages.size()).isEqualTo(2);
  }
}
