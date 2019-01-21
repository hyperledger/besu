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
package tech.pegasys.pantheon.consensus.ibft.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.pantheon.consensus.ibft.support.MessageReceptionHelpers.assertPeersReceivedExactly;
import static tech.pegasys.pantheon.consensus.ibft.support.MessageReceptionHelpers.assertPeersReceivedNoMessages;
import static tech.pegasys.pantheon.consensus.ibft.support.TestHelpers.createSignedCommitPayload;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.messagedata.IbftV2;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.support.NodeParams;
import tech.pegasys.pantheon.consensus.ibft.support.RoundSpecificNodeRoles;
import tech.pegasys.pantheon.consensus.ibft.support.TestContext;
import tech.pegasys.pantheon.consensus.ibft.support.TestContextBuilder;
import tech.pegasys.pantheon.consensus.ibft.support.ValidatorPeer;
import tech.pegasys.pantheon.crypto.SECP256K1;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.crypto.SECP256K1.Signature;
import tech.pegasys.pantheon.ethereum.core.Block;
import tech.pegasys.pantheon.ethereum.core.Hash;
import tech.pegasys.pantheon.ethereum.core.Util;
import tech.pegasys.pantheon.ethereum.p2p.api.MessageData;
import tech.pegasys.pantheon.ethereum.p2p.wire.RawMessage;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.Before;
import org.junit.Test;

public class SpuriousBehaviourTest {

  final long blockTimeStamp = 100;
  private final Clock fixedClock =
      Clock.fixed(Instant.ofEpochSecond(blockTimeStamp), ZoneId.systemDefault());

  // Test is configured such that a remote peer is responsible for proposing a block
  private final int NETWORK_SIZE = 5;

  // Configuration ensures remote peer will provide proposal for first block
  private final TestContext context =
      new TestContextBuilder()
          .validatorCount(NETWORK_SIZE)
          .indexOfFirstLocallyProposedBlock(0)
          .clock(fixedClock)
          .build();
  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificNodeRoles roles = context.getRoundSpecificRoles(roundId);

  private Block proposedBlock = context.createBlockForProposalFromChainHead(0, 30);
  private SignedData<PreparePayload> expectedPrepare;
  private SignedData<CommitPayload> expectedCommit;

  @Before
  public void setup() {
    context.getController().start();

    expectedPrepare =
        context
            .getLocalNodeMessageFactory()
            .createSignedPreparePayload(roundId, proposedBlock.getHash());
    expectedCommit =
        createSignedCommitPayload(
            roundId, proposedBlock, context.getLocalNodeParams().getNodeKeyPair());
  }

  @Test
  public void badlyFormedRlpDoesNotPreventOngoingIbftOperation() {
    final MessageData illegalCommitMsg = new RawMessage(IbftV2.PREPARE, BytesValue.EMPTY);
    roles.getNonProposingPeer(0).injectMessage(illegalCommitMsg);

    roles.getProposer().injectProposal(roundId, proposedBlock);
    assertPeersReceivedExactly(roles.getAllPeers(), expectedPrepare);
  }

  @Test
  public void messageWithIllegalMessageCodeAreDiscardedAndDoNotPreventOngoingIbftOperation() {
    final MessageData illegalCommitMsg = new RawMessage(IbftV2.MESSAGE_SPACE, BytesValue.EMPTY);
    roles.getNonProposingPeer(0).injectMessage(illegalCommitMsg);

    roles.getProposer().injectProposal(roundId, proposedBlock);
    assertPeersReceivedExactly(roles.getAllPeers(), expectedPrepare);
  }

  @Test
  public void nonValidatorsCannotTriggerReponses() {
    final KeyPair nonValidatorKeys = KeyPair.generate();
    final NodeParams nonValidatorParams =
        new NodeParams(Util.publicKeyToAddress(nonValidatorKeys.getPublicKey()), nonValidatorKeys);

    final ValidatorPeer nonvalidator =
        new ValidatorPeer(
            nonValidatorParams,
            new MessageFactory(nonValidatorParams.getNodeKeyPair()),
            context.getEventMultiplexer());

    nonvalidator.injectProposal(new ConsensusRoundIdentifier(1, 0), proposedBlock);

    assertPeersReceivedNoMessages(roles.getAllPeers());
  }

  @Test
  public void preparesWithMisMatchedDigestAreNotRepondedTo() {
    roles.getProposer().injectProposal(roundId, proposedBlock);
    assertPeersReceivedExactly(roles.getAllPeers(), expectedPrepare);

    roles.getNonProposingPeers().forEach(peer -> peer.injectPrepare(roundId, Hash.ZERO));

    assertPeersReceivedNoMessages(roles.getAllPeers());

    roles
        .getNonProposingPeers()
        .forEach(peer -> peer.injectPrepare(roundId, proposedBlock.getHash()));

    assertPeersReceivedExactly(roles.getAllPeers(), expectedCommit);

    roles.getNonProposingPeers().forEach(peer -> peer.injectCommit(roundId, Hash.ZERO));
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    roles
        .getNonProposingPeers()
        .forEach(peer -> peer.injectCommit(roundId, proposedBlock.getHash()));
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
  }

  @Test
  public void oneCommitSealIsIllegalPreventsImport() {
    roles.getProposer().injectProposal(roundId, proposedBlock);
    assertPeersReceivedExactly(roles.getAllPeers(), expectedPrepare);
    roles
        .getNonProposingPeers()
        .forEach(peer -> peer.injectPrepare(roundId, proposedBlock.getHash()));

    // for a network of 5, 4 seals are required (local + 3 remote)
    roles.getNonProposingPeer(0).injectCommit(roundId, proposedBlock.getHash());
    roles.getNonProposingPeer(1).injectCommit(roundId, proposedBlock.getHash());

    // nonProposer-2 will generate an invalid seal
    final ValidatorPeer badSealPeer = roles.getNonProposingPeer(2);
    final Signature illegalSeal = SECP256K1.sign(Hash.ZERO, badSealPeer.getNodeKeys());

    badSealPeer.injectCommit(roundId, proposedBlock.getHash(), illegalSeal);
    assertThat(context.getCurrentChainHeight()).isEqualTo(0);

    // Now inject the REAL commit message
    badSealPeer.injectCommit(roundId, proposedBlock.getHash());
    assertThat(context.getCurrentChainHeight()).isEqualTo(1);
  }
}
