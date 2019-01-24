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

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static tech.pegasys.pantheon.consensus.ibft.support.MessageReceptionHelpers.assertPeersReceivedExactly;
import static tech.pegasys.pantheon.consensus.ibft.support.MessageReceptionHelpers.assertPeersReceivedNoMessages;

import tech.pegasys.pantheon.consensus.ibft.ConsensusRoundIdentifier;
import tech.pegasys.pantheon.consensus.ibft.IbftHelpers;
import tech.pegasys.pantheon.consensus.ibft.ibftevent.NewChainHead;
import tech.pegasys.pantheon.consensus.ibft.messagedata.ProposalMessageData;
import tech.pegasys.pantheon.consensus.ibft.payload.CommitPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.MessageFactory;
import tech.pegasys.pantheon.consensus.ibft.payload.NewRoundPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.PreparePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.ProposalPayload;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangeCertificate;
import tech.pegasys.pantheon.consensus.ibft.payload.RoundChangePayload;
import tech.pegasys.pantheon.consensus.ibft.payload.SignedData;
import tech.pegasys.pantheon.consensus.ibft.support.RoundSpecificNodeRoles;
import tech.pegasys.pantheon.consensus.ibft.support.TestContext;
import tech.pegasys.pantheon.consensus.ibft.support.TestContextBuilder;
import tech.pegasys.pantheon.consensus.ibft.support.ValidatorPeer;
import tech.pegasys.pantheon.crypto.SECP256K1.KeyPair;
import tech.pegasys.pantheon.ethereum.core.Block;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;

public class GossipTest {
  private final long blockTimeStamp = 100;
  private final Clock fixedClock =
      Clock.fixed(Instant.ofEpochSecond(blockTimeStamp), ZoneId.systemDefault());

  private final int NETWORK_SIZE = 5;

  private final TestContext context =
      new TestContextBuilder()
          .validatorCount(NETWORK_SIZE)
          .indexOfFirstLocallyProposedBlock(0)
          .clock(fixedClock)
          .useGossip(true)
          .build();

  private final ConsensusRoundIdentifier roundId = new ConsensusRoundIdentifier(1, 0);
  private final RoundSpecificNodeRoles roles = context.getRoundSpecificRoles(roundId);
  private Block block;
  private ValidatorPeer sender;
  private MessageFactory msgFactory;

  @Before
  public void setup() {
    context.getController().start();
    block = context.createBlockForProposalFromChainHead(roundId.getRoundNumber(), 30);
    sender = roles.getProposer();
    msgFactory = sender.getMessageFactory();
  }

  @Test
  public void gossipMessagesToPeers() {
    SignedData<PreparePayload> localPrepare =
        context.getLocalNodeMessageFactory().createSignedPreparePayload(roundId, block.getHash());
    assertPeersReceivedNoMessages(roles.getNonProposingPeers());
    final SignedData<ProposalPayload> proposal = sender.injectProposal(roundId, block);
    // sender node will have a prepare message as an effect of the proposal being sent
    assertPeersReceivedExactly(roles.getNonProposingPeers(), proposal, localPrepare);
    assertPeersReceivedExactly(singleton(sender), localPrepare);

    final SignedData<PreparePayload> prepare = sender.injectPrepare(roundId, block.getHash());
    assertPeersReceivedExactly(roles.getNonProposingPeers(), prepare);
    assertPeersReceivedNoMessages(singleton(sender));

    final SignedData<CommitPayload> commit = sender.injectCommit(roundId, block.getHash());
    assertPeersReceivedExactly(roles.getNonProposingPeers(), commit);
    assertPeersReceivedNoMessages(singleton(sender));

    final SignedData<RoundChangePayload> roundChange =
        msgFactory.createSignedRoundChangePayload(roundId, Optional.empty());
    final RoundChangeCertificate roundChangeCert =
        new RoundChangeCertificate(singleton(roundChange));
    SignedData<NewRoundPayload> newRound =
        sender.injectNewRound(roundId, roundChangeCert, proposal);
    assertPeersReceivedExactly(roles.getNonProposingPeers(), newRound);
    assertPeersReceivedNoMessages(singleton(sender));

    sender.injectRoundChange(roundId, Optional.empty());
    assertPeersReceivedExactly(roles.getNonProposingPeers(), roundChange);
    assertPeersReceivedNoMessages(singleton(sender));
  }

  @Test
  public void onlyGossipOnce() {
    final SignedData<PreparePayload> prepare = sender.injectPrepare(roundId, block.getHash());
    assertPeersReceivedExactly(roles.getNonProposingPeers(), prepare);

    sender.injectPrepare(roundId, block.getHash());
    assertPeersReceivedNoMessages(roles.getNonProposingPeers());

    sender.injectPrepare(roundId, block.getHash());
    assertPeersReceivedNoMessages(roles.getNonProposingPeers());
  }

  @Test
  public void messageWithUnknownValidatorIsNotGossiped() {
    final KeyPair unknownKeyPair = KeyPair.generate();
    final MessageFactory unknownMsgFactory = new MessageFactory(unknownKeyPair);
    final SignedData<ProposalPayload> unknownProposal =
        unknownMsgFactory.createSignedProposalPayload(roundId, block);

    sender.injectMessage(ProposalMessageData.create(unknownProposal));
    assertPeersReceivedNoMessages(roles.getAllPeers());
  }

  @Test
  public void messageIsNotGossipedToSenderOrCreator() {
    final ValidatorPeer msgCreator = roles.getNonProposingPeer(0);
    final MessageFactory peerMsgFactory = msgCreator.getMessageFactory();
    final SignedData<ProposalPayload> proposalFromPeer =
        peerMsgFactory.createSignedProposalPayload(roundId, block);

    sender.injectMessage(ProposalMessageData.create(proposalFromPeer));

    final List<ValidatorPeer> validators = new ArrayList<>(roles.getNonProposingPeers());
    validators.remove(msgCreator);
    assertPeersReceivedExactly(validators, proposalFromPeer);
    assertPeersReceivedNoMessages(ImmutableList.of(roles.getProposer(), msgCreator));
  }

  @Test
  public void futureMessageIsNotGossipedImmediately() {
    ConsensusRoundIdentifier futureRoundId = new ConsensusRoundIdentifier(2, 0);
    msgFactory.createSignedProposalPayload(futureRoundId, block);

    sender.injectProposal(futureRoundId, block);
    assertPeersReceivedNoMessages(roles.getAllPeers());
  }

  @Test
  public void previousHeightMessageIsNotGossiped() {
    final ConsensusRoundIdentifier futureRoundId = new ConsensusRoundIdentifier(0, 0);
    sender.injectProposal(futureRoundId, block);
    assertPeersReceivedNoMessages(roles.getAllPeers());
  }

  @Test
  public void futureMessageGetGossipedLater() {
    final Block signedCurrentHeightBlock =
        IbftHelpers.createSealedBlock(
            block,
            roles
                .getAllPeers()
                .stream()
                .map(peer -> peer.getBlockSignature(block.getHash()))
                .collect(Collectors.toList()));

    ConsensusRoundIdentifier futureRoundId = new ConsensusRoundIdentifier(2, 0);
    SignedData<PreparePayload> futurePrepare = sender.injectPrepare(futureRoundId, block.getHash());
    assertPeersReceivedNoMessages(roles.getNonProposingPeers());

    // add block to chain so we can move to next block height
    context.getBlockchain().appendBlock(signedCurrentHeightBlock, emptyList());
    context
        .getController()
        .handleNewBlockEvent(new NewChainHead(signedCurrentHeightBlock.getHeader()));

    assertPeersReceivedExactly(roles.getNonProposingPeers(), futurePrepare);
  }
}
